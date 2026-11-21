//! Agent messages (`agent_hud_messaging`): the tool group through which agents
//! message, start and read other agent sessions on this computer. Its calls
//! come from the local tools channel and run on the caller's thread, never
//! with the application's lock held. A look every 5 seconds, while a message
//! still waits for something, captures deliveries and throws planes back.
//!
//! The sends go through the product's own senders when it has some
//! (`ProductExtension::delivery`), else through the host's: the app's data
//! folder keeps their receipts, and the host follows a started Claude job
//! into the desktop app itself.
use crate::{activity, backend::Backend, claude_plugin::ClaudePlugin, extension::Delivery};
use agent_hud_messaging::{Host, Messaging, Places, Preferences, Turns};
use agent_hud_runtime::delivery::{
    Handoffs, LocalClientPaths, LocalClients, NativeCodexTransport, SessionStarts, StartConfig,
};
use serde_json::Value;
use std::{
    path::PathBuf,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Condvar, Mutex, PoisonError, Weak,
    },
    thread::JoinHandle,
    time::{Duration, SystemTime},
};
use tauri::Manager;

const LOOK: Duration = Duration::from_secs(5);

#[derive(Default)]
struct LookSignal {
    stop: bool,
    wake: bool,
}

/// The host's own senders: their receipts, and the Claude jobs it follows.
struct Own {
    clients: Arc<LocalClients>,
    starts: SessionStarts,
    handoffs: Mutex<Handoffs>,
}

pub(crate) struct AgentMessages {
    app: tauri::AppHandle,
    messaging: Mutex<Messaging>,
    waiting: AtomicBool,
    signal: Arc<(Mutex<LookSignal>, Condvar)>,
    conversations: Option<Arc<dyn Turns>>,
    own: Option<Arc<Own>>,
    backend: Weak<Mutex<Backend>>,
    looks: Mutex<Option<JoinHandle<()>>>,
}

impl AgentMessages {
    /// Agent messages over the product's senders, else the host's own in
    /// `data` (the app's data folder; the demo's own folder in the demo).
    pub(crate) fn start(
        app: &tauri::AppHandle,
        backend: Weak<Mutex<Backend>>,
        product: Option<Delivery>,
        data: PathBuf,
    ) -> Result<Arc<Self>, String> {
        let (delivery, own) = match product {
            Some(delivery) => (delivery, None),
            None => own_delivery(app, data)?,
        };
        let places = Places {
            codex_data: delivery.starts.codex_data.clone(),
            applications: delivery.starts.applications(),
            codex_command: delivery.starts.codex_executable.is_some(),
        };
        let starts = SessionStarts::new(delivery.starts, delivery.start_receipts)
            .map_err(|error| error.to_string())?
            .with_deepseek_channel(delivery.clients.deepseek_channel());
        let flights = app.clone();
        let messaging = Messaging::new(
            &delivery.directory,
            places,
            delivery.clients,
            starts,
            delivery.follow,
            Arc::new(move |flight| activity::publish(&flights, "hud:flight", &flight)),
        );
        let initial_waiting = messaging.waiting();
        let signal = Arc::new((Mutex::new(LookSignal::default()), Condvar::new()));
        let owner = Arc::new(Self {
            app: app.clone(),
            messaging: Mutex::new(messaging),
            waiting: AtomicBool::new(initial_waiting),
            signal: Arc::clone(&signal),
            conversations: delivery.conversations,
            own,
            backend,
            looks: Mutex::new(None),
        });
        let looker = Arc::downgrade(&owner);
        let thread = std::thread::Builder::new()
            .name("agenthud-messages".into())
            .spawn(move || loop {
                {
                    let mut state = signal.0.lock().unwrap_or_else(PoisonError::into_inner);
                    while !state.stop && !state.wake {
                        let (next, timeout) = signal
                            .1
                            .wait_timeout(state, LOOK)
                            .unwrap_or_else(PoisonError::into_inner);
                        state = next;
                        if timeout.timed_out() {
                            break;
                        }
                    }
                    if state.stop {
                        break;
                    }
                    state.wake = false;
                }
                match looker.upgrade() {
                    Some(owner) => owner.look(),
                    None => break,
                }
            })
            .map_err(|error| error.to_string())?;
        *owner.looks.lock().unwrap_or_else(PoisonError::into_inner) = Some(thread);
        Ok(owner)
    }

    pub(crate) fn wake_if_waiting(&self) {
        if self.waiting.load(Ordering::Acquire) {
            let mut state = self.signal.0.lock().unwrap_or_else(PoisonError::into_inner);
            state.wake = true;
            self.signal.1.notify_all();
        }
    }

    /// The group's tools while it is on, as the relay lists them.
    pub(crate) fn tools(snapshot: &Value) -> Value {
        Messaging::tools(Preferences::read(&snapshot["settings"]).enabled)
    }

    /// One tool call from the relay: `{name, arguments, caller}`.
    pub(crate) fn call(&self, params: &Value) -> Result<Value, String> {
        let snapshot = self.snapshot()?;
        let sending = matches!(
            params["name"].as_str(),
            Some("send_message" | "start_session")
        );
        let (result, waiting) = self.with_messaging(&snapshot, |messaging, host| {
            let result = messaging.call(params, host, chrono::Utc::now().timestamp_millis());
            (result, messaging.waiting())
        });
        self.waiting.store(waiting, Ordering::Release);
        if sending && result.is_ok() {
            self.publish_changed();
        }
        result
    }

    fn publish_changed(&self) {
        activity::publish(
            &self.app,
            "agent-messages:changed",
            &serde_json::json!(true),
        );
    }

    pub(crate) fn history(&self, snapshot: &Value) -> Value {
        self.messaging
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .history(snapshot)
    }

    /// Runs `work` with the group's senders and this moment's facts, for the
    /// person's own words from the HUD's clipboard window, which do not need
    /// the group switched on.
    pub(crate) fn as_user<T>(
        &self,
        work: impl FnOnce(&mut Messaging, &Host<'_>) -> T,
    ) -> Result<T, String> {
        let snapshot = self.snapshot()?;
        Ok(self.with_messaging(&snapshot, work))
    }

    fn with_messaging<T>(
        &self,
        snapshot: &Value,
        work: impl FnOnce(&mut Messaging, &Host<'_>) -> T,
    ) -> T {
        let query = self.query();
        let host = Host {
            snapshot,
            query: &query,
            conversations: self.conversations.as_deref(),
        };
        let mut messaging = self
            .messaging
            .lock()
            .unwrap_or_else(PoisonError::into_inner);
        work(&mut messaging, &host)
    }

    /// The 5-second look: the host's own senders' receipts and moves, and
    /// the messages that still wait for something while the group is on.
    fn look(&self) {
        if let Some(own) = &self.own {
            let _ = own.clients.maintain(SystemTime::now());
            own.handoffs
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .hand_off_stopped(&own.starts, |session| own.clients.waits_for(session));
        }
        let Ok(snapshot) = self.snapshot() else {
            return;
        };
        if !Preferences::read(&snapshot["settings"]).enabled {
            return;
        }
        let (changed, waiting) = self.with_messaging(&snapshot, |messaging, host| {
            if !messaging.waiting() {
                return (false, false);
            }
            let changed = messaging.maintain(host, chrono::Utc::now().timestamp_millis());
            (changed, messaging.waiting())
        });
        self.waiting.store(waiting, Ordering::Release);
        if changed {
            self.publish_changed();
        }
    }

    fn snapshot(&self) -> Result<Arc<Value>, String> {
        let backend = self
            .backend
            .upgrade()
            .ok_or("The application is stopping")?;
        let backend = backend.lock().unwrap_or_else(PoisonError::into_inner);
        backend
            .snapshot
            .clone()
            .ok_or_else(|| "Application data is loading".into())
    }

    /// The app's queries (`session_detail` pages), each under the
    /// application's lock only while it runs.
    fn query(&self) -> impl Fn(&str, Value) -> Result<Value, String> {
        let backend = self.backend.clone();
        move |method, params| {
            let backend = backend.upgrade().ok_or("The application is stopping")?;
            let mut backend = backend.lock().unwrap_or_else(PoisonError::into_inner);
            backend.runtime.local_query(method, params)
        }
    }

    pub(crate) fn stop(&self) {
        {
            let mut state = self.signal.0.lock().unwrap_or_else(PoisonError::into_inner);
            state.stop = true;
            self.signal.1.notify_all();
        }
        if let Some(thread) = self
            .looks
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .take()
        {
            let _ = thread.join();
        }
    }
}

/// The host's own senders, over `data` and Agent HUD's plugins' folders.
fn own_delivery(
    app: &tauri::AppHandle,
    data: PathBuf,
) -> Result<(Delivery, Option<Arc<Own>>), String> {
    let home = agent_hud_runtime::home_directory()?;
    let config = StartConfig::find(&home);
    let receipts = data.join("local-client-receipts");
    let clients = LocalClients::with_transport(
        LocalClientPaths {
            claude_channel: app.state::<ClaudePlugin>().channel_directory(),
            codex_data: config.codex_data.clone(),
            receipts: receipts.clone(),
        },
        Vec::new(),
        Arc::new(NativeCodexTransport {
            queue: config.codex_queue(),
        }),
        Duration::from_secs(15),
    )
    .map_err(|error| error.to_string())?
    .recording_deliveries(Some(data.clone()))
    .with_deepseek_channel(Some(agent_hud_runtime::deepseek_plugin::channel_directory(
        &data,
    )));
    let clients = Arc::new(clients);
    let start_receipts = receipts.join("starts");
    let starts = SessionStarts::new(config.clone(), start_receipts.clone())
        .map_err(|error| error.to_string())?;
    let own = Arc::new(Own {
        clients: clients.clone(),
        handoffs: Mutex::new(Handoffs::load(&starts)),
        starts,
    });
    let follower = own.clone();
    let delivery = Delivery {
        clients,
        starts: config,
        start_receipts,
        directory: data,
        follow: Arc::new(move |receipt| {
            let waiting = receipt
                .session_id
                .as_deref()
                .is_some_and(|session| follower.clients.waits_for(session));
            follower
                .handoffs
                .lock()
                .unwrap_or_else(PoisonError::into_inner)
                .follow(&follower.starts, &receipt, waiting);
        }),
        conversations: None,
    };
    Ok((delivery, Some(own)))
}

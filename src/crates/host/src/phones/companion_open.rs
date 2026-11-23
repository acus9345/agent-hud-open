//! Opens or wakes a session (and launches its desktop client if closed) when
//! requested from a paired companion device (`/v1/companion/open`) or when a
//! message is sent to a session whose client or window is currently closed.
use crate::{
    backend::Backend,
    messages::AgentMessages,
    service::{wake_collector, REQUEST},
};
use agent_hud_messaging::UserTarget;
use agent_hud_runtime::{claude_plugin::ClaudeChannel, NativePlatform};
use agent_hud_share::LanHandled;
use serde_json::{json, Value};
use std::{
    path::Path,
    sync::{Arc, Condvar, Mutex, PoisonError, RwLock, Weak},
    thread,
    time::{Duration, SystemTime},
};

#[path = "companion_claude.rs"]
mod claude;

use super::{json_reply, messages_handle};

pub(super) fn handle_open(
    body: &[u8],
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
) -> LanHandled {
    let payload: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return json_reply(400, json!({"error": "Request body must be valid JSON"})),
    };
    let Some(id) = payload["id"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        return json_reply(400, json!({"error": "Session id is required"}));
    };
    let client_hint = payload["client"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty());
    match open_session_on_desktop(id, client_hint, backend, latest_snapshot) {
        Ok(action) => json_reply(200, json!({"ok": true, "opened": true, "action": action})),
        Err(error) => json_reply(400, json!({"error": error})),
    }
}

pub(super) fn handle_send_now(
    body: &[u8],
    backend: &Weak<Mutex<Backend>>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
) -> LanHandled {
    let payload: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return json_reply(400, json!({"error": "Request body must be valid JSON"})),
    };
    let client = payload["client"].as_str().unwrap_or("").trim().to_owned();
    let Some(session_id) = payload["sessionId"]
        .as_str()
        .or_else(|| payload["id"].as_str())
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        return json_reply(400, json!({"error": "Session id is required"}));
    };
    let text = payload["text"].as_str().unwrap_or("").trim();
    if client == "antigravity" {
        match agent_hud_runtime::delivery::antigravity_send_queued_now(session_id) {
            Ok(()) => {
                wake_collector(wake, REQUEST);
                return json_reply(200, json!({"state": "steered", "sessionId": session_id}));
            }
            Err(err) if text.is_empty() => {
                return json_reply(400, json!({"error": err.to_string()}));
            }
            Err(_) => {}
        }
    }
    if text.is_empty() {
        return json_reply(200, json!({"state": "queued", "sessionId": session_id}));
    }
    let Some(messages) = messages_handle(backend) else {
        return json_reply(503, json!({"error": "Messaging is unavailable"}));
    };
    let now = chrono::Utc::now().timestamp_millis();
    let res = messages.as_user(|m, host| m.user_send_now(&client, session_id, text, host, now));
    if client == "antigravity" {
        let _ = agent_hud_runtime::delivery::antigravity_send_queued_now(session_id);
    }
    finish_send(res, wake)
}

pub(super) fn handle_send_with_wake(
    target: &UserTarget,
    text: &str,
    send_now: bool,
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    app_dir: Option<&Path>,
) -> LanHandled {
    let Some(messages) = messages_handle(backend) else {
        return json_reply(503, json!({"error": "Messaging is unavailable"}));
    };
    let now = chrono::Utc::now().timestamp_millis();
    let initial = match (send_now, target) {
        (true, UserTarget::Session { client, session_id }) => {
            messages.as_user(|m, host| m.user_send_now(client, session_id, text, host, now))
        }
        _ => messages.as_user(|m, host| m.user_send(target, text, host, now)),
    };

    let UserTarget::Session { client, session_id } = target else {
        return finish_send(initial, wake);
    };

    if send_now && client == "antigravity" {
        let _ = agent_hud_runtime::delivery::antigravity_send_queued_now(session_id);
    }

    // If Codex queued the message on Windows, also wake/open the thread in Codex
    // Desktop so the user does not have to manually open the app or thread.
    if client == "codex" {
        if let Ok(Ok(ref delivery)) = initial {
            if delivery["state"] == "queued" {
                let _ =
                    open_session_on_desktop(session_id, Some("codex"), backend, latest_snapshot);
            }
        }
    }

    // Wake the session's app only when waking could change the answer: not
    // for words that are refused as such or an agent the person turned off.
    let needs_wake = match &initial {
        Ok(Ok(delivery)) => delivery["state"] == "refused",
        Ok(Err(_)) | Err(_) => true,
    } && !messages
        .as_user(|m, host| m.user_refusal_is_final(client, text, host))
        .unwrap_or(false);
    if !needs_wake {
        return finish_send(initial, wake);
    }

    // The session or its desktop app is closed: open/wake it and deliver.
    let _ = open_session_on_desktop(session_id, Some(client.as_str()), backend, latest_snapshot);

    match client.as_str() {
        "claude" => wake_and_deliver_claude(session_id, text, &messages, wake, app_dir, initial),
        "antigravity" | "deepseek" | "codex" => {
            retry_send_after_wake(target, text, &messages, wake, initial)
        }
        _ => finish_send(initial, wake),
    }
}

fn finish_send(
    result: Result<Result<Value, String>, String>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
) -> LanHandled {
    match result {
        Ok(Ok(delivery)) => {
            wake_collector(wake, REQUEST);
            json_reply(200, delivery)
        }
        Ok(Err(error)) | Err(error) => json_reply(400, json!({"error": error})),
    }
}

fn wake_and_deliver_claude(
    session_id: &str,
    text: &str,
    messages: &Arc<AgentMessages>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    app_dir: Option<&Path>,
    fallback: Result<Result<Value, String>, String>,
) -> LanHandled {
    // Poll briefly to see if the woken Claude session checks in directly.
    let target = UserTarget::Session {
        client: "claude".into(),
        session_id: session_id.to_owned(),
    };
    if let Some(reply) = poll_send(&target, text, messages, wake, 10, 300) {
        return reply;
    }
    // Queue the prompt directly in claude-channel so the plugin picks it up the
    // moment Claude Desktop finishes loading the session.
    if let Some(dir) = app_dir {
        let channel = ClaudeChannel::new(dir.join("claude-channel"));
        let req_id = uuid::Uuid::new_v4().to_string();
        if channel
            .leave(session_id, &req_id, text, true, SystemTime::now())
            .is_ok()
        {
            wake_collector(wake, REQUEST);
            return json_reply(
                200,
                json!({
                    "state": "queued",
                    "reason": "Opened session in Claude Desktop and queued message",
                    "sessionId": session_id,
                }),
            );
        }
    }
    finish_send(fallback, wake)
}

fn retry_send_after_wake(
    target: &UserTarget,
    text: &str,
    messages: &Arc<AgentMessages>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    fallback: Result<Result<Value, String>, String>,
) -> LanHandled {
    poll_send(target, text, messages, wake, 24, 450).unwrap_or_else(|| finish_send(fallback, wake))
}

/// Retries sending `text` to a session being woken, `tries` times every
/// `every_ms`, until the session takes it.
fn poll_send(
    target: &UserTarget,
    text: &str,
    messages: &Arc<AgentMessages>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    tries: u32,
    every_ms: u64,
) -> Option<LanHandled> {
    for _ in 0..tries {
        thread::sleep(Duration::from_millis(every_ms));
        let now = chrono::Utc::now().timestamp_millis();
        if let Ok(Ok(delivery)) = messages.as_user(|m, host| m.user_send(target, text, host, now)) {
            if delivery["state"] != "refused" {
                wake_collector(wake, REQUEST);
                return Some(json_reply(200, delivery));
            }
        }
    }
    None
}

pub(crate) fn open_session_on_desktop(
    id: &str,
    client_hint: Option<&str>,
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
) -> Result<String, String> {
    let snapshot = latest_snapshot
        .read()
        .unwrap_or_else(PoisonError::into_inner)
        .clone();
    let row = snapshot.as_ref().and_then(|snap| {
        snap["sessions"]
            .as_array()
            .and_then(|rows| rows.iter().find(|r| r["id"].as_str() == Some(id)).cloned())
    });
    let field = |key: &str| {
        row.as_ref()
            .and_then(|r| r[key].as_str())
            .map(str::to_owned)
    };
    let source = field("source")
        .or_else(|| client_hint.map(str::to_owned))
        .unwrap_or_default();
    let cwd = field("cwd").or_else(|| field("project"));
    let title = field("title");
    let model = field("model");

    let platform = NativePlatform::new()?;

    match source.as_str() {
        "claude" => {
            let local_id = claude::ensure_claude_desktop_session(
                &platform,
                id,
                cwd.as_deref(),
                title.as_deref(),
                model.as_deref(),
            )
            .unwrap_or_else(|| format!("local_{id}"));
            let _ = platform.open_client("claude");
            let target = json!({"kind": "claude-desktop-session", "id": local_id});
            if platform.navigate_session(&target).unwrap_or(false) {
                return Ok("claude_desktop".into());
            }
        }
        "codex" => {
            let _ = platform.open_client("codex");
            let bare = id.strip_prefix("codex:").unwrap_or(id);
            let target = json!({"kind": "codex-thread", "id": bare});
            if platform.navigate_session(&target).unwrap_or(false) {
                return Ok("codex_desktop".into());
            }
        }
        "antigravity" => {
            let bare = id.strip_prefix("antigravity:").unwrap_or(id);
            let target = json!({"kind": "antigravity-conversation", "id": bare, "cwd": cwd});
            if platform.navigate_session(&target).unwrap_or(false) {
                return Ok("antigravity_desktop".into());
            }
            if platform.open_client("antigravity") {
                return Ok("antigravity_app".into());
            }
        }
        "deepseek" => {
            let target = json!({"kind": "deepseek-session", "id": id});
            if platform.navigate_session(&target).unwrap_or(false)
                || platform.open_client("deepseek")
            {
                return Ok("deepseek_desktop".into());
            }
        }
        _ => {}
    }

    if let Some(backend) = backend.upgrade() {
        let res = {
            let mut guard = backend.lock().unwrap_or_else(PoisonError::into_inner);
            guard.runtime.request("open_session", json!({"id": id}))
        };
        if res.is_ok() {
            return Ok("runtime_open".into());
        }
    }
    if !source.is_empty() && platform.open_client(&source) {
        return Ok("client_app".into());
    }
    Err("Could not open session or launch its desktop application".into())
}

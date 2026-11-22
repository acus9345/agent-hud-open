//! The share's thread. It starts and stops the LAN link with the page's
//! switch, projects each snapshot into records, reads the ledger's usage at
//! most every 30 seconds once it has moved, and publishes what changed.
use super::{relay, settings::Settings, Command, Query};
use crate::{keys, projection, SyncRecord};
use agent_hud_lan::{
    cache::SnapshotCache,
    credentials::{Credentials, SecretStore},
    identity::Identity,
    server::LanServer,
};
use serde_json::{json, Value};
use std::{
    collections::BTreeMap,
    path::PathBuf,
    sync::{mpsc, Arc, Mutex, PoisonError},
    time::Duration,
};

mod usage;
use usage::usage_day;

const ADDRESS_EVERY_MS: i64 = 30_000;
/// Usage goes out for the last 30 days, as every other copy of it does.
const RETENTION_MS: i64 = 30 * keys::DAY_MS;

pub(super) struct Worker {
    directory: PathBuf,
    query: Query,
    extension: crate::LanExtension,
    secrets: Option<Arc<dyn SecretStore>>,
    settings: Settings,
    name: String,
    cache: Arc<Mutex<SnapshotCache<SyncRecord>>>,
    lan: Option<LanServer<SyncRecord>>,
    pub(super) records: BTreeMap<String, SyncRecord>,
    snapshot: Option<Arc<Value>>,
    /// What the readings last published said was read (`reading_facts`).
    readings: Option<Value>,
    usage_revision: Option<u64>,
    usage_seeded: bool,
    usage_tried_at: i64,
    /// The revision and time of the last read over every day kept.
    usage_full_revision: Option<u64>,
    usage_full_at: i64,
    address_at: i64,
    relay: relay::RelayTunnel,
    error: Option<String>,
}
impl Worker {
    pub fn new_with_extension(
        directory: PathBuf,
        query: Query,
        extension: crate::LanExtension,
    ) -> Result<Self, String> {
        Self::with_secrets_and_extension(directory, query, super::os::secrets(), extension)
    }
    #[cfg(test)]
    pub(super) fn with_secrets(
        directory: PathBuf,
        query: Query,
        secrets: Option<Arc<dyn SecretStore>>,
    ) -> Result<Self, String> {
        Self::with_secrets_and_extension(directory, query, secrets, Arc::new(|_, _| Ok(None)))
    }
    fn with_secrets_and_extension(
        directory: PathBuf,
        query: Query,
        secrets: Option<Arc<dyn SecretStore>>,
        extension: crate::LanExtension,
    ) -> Result<Self, String> {
        let settings = Settings::load(&directory)?;
        let name = super::computer_name();
        let cache = SnapshotCache::new(
            settings.device_id.clone(),
            name.clone(),
            directory.join("cursor.json"),
        )?
        .with_rules(std::sync::Arc::new(crate::phone_rules::refusal));
        Ok(Self {
            directory,
            query,
            extension,
            secrets,
            settings,
            name,
            cache: Arc::new(Mutex::new(cache)),
            lan: None,
            records: BTreeMap::new(),
            snapshot: None,
            readings: None,
            usage_revision: None,
            usage_seeded: false,
            usage_tried_at: 0,
            usage_full_revision: None,
            usage_full_at: 0,
            address_at: 0,
            relay: relay::RelayTunnel::default(),
            error: None,
        })
    }
    pub fn run(&mut self, inbox: mpsc::Receiver<Command>) {
        if self.settings.enabled {
            if let Err(error) = self.start_lan() {
                self.error = Some(error);
            }
        }
        loop {
            match inbox.recv_timeout(Duration::from_secs(5)) {
                Ok(Command::Snapshot(snapshot)) => self.snapshot = Some(snapshot),
                Ok(Command::Request {
                    method,
                    params,
                    reply,
                }) => {
                    let _ = reply.send(self.request(&method, params, now()));
                }
                Ok(Command::Stop) | Err(mpsc::RecvTimeoutError::Disconnected) => break,
                Err(mpsc::RecvTimeoutError::Timeout) => {}
            }
            if let Err(error) = self.pass(now()) {
                self.error = Some(error);
            }
        }
        self.relay.stop();
        if let Some(mut lan) = self.lan.take() {
            let _ = lan.stop(false);
        }
    }
    #[cfg(test)]
    pub(super) fn observe_for_test(&mut self, snapshot: Arc<Value>) {
        self.snapshot = Some(snapshot);
    }
    fn start_lan(&mut self) -> Result<(), String> {
        if self.lan.is_some() {
            return Ok(());
        }
        let secrets = self
            .secrets
            .clone()
            .ok_or("Sharing with phones is not available on this system yet.")?;
        let device = self.settings.device_id.clone();
        let identity = Identity::load(&device, &self.directory)?;
        let credentials = Credentials::new(&device, self.directory.clone(), secrets)?;
        let mut lan = LanServer::new(
            device,
            self.name.clone(),
            credentials,
            self.cache.clone(),
            identity,
            self.extension.clone(),
        );
        let port = lan.start(self.settings.port)?;
        if self.settings.port != port {
            self.settings.port = port;
            self.settings.save(&self.directory)?;
        }
        self.lan = Some(lan);
        self.address_at = 0;
        Ok(())
    }
    /// Turning sharing off disconnects the phones and revokes their grants.
    fn stop_lan(&mut self) -> Result<(), String> {
        self.relay.stop();
        match self.lan.take() {
            Some(mut lan) => lan.stop(true),
            None => Credentials::revoke_saved(&self.directory),
        }
    }
    fn refresh_lan_addresses(&mut self) {
        let Some(lan) = &self.lan else {
            return;
        };
        let include_overlay = self.settings.remote.mode == "tailscale";
        let extras =
            if self.settings.remote.mode == "relay" && !self.settings.remote.relay_url.is_empty() {
                vec![self.settings.remote.relay_url.clone()]
            } else {
                Vec::new()
            };
        lan.refresh_address_with_extras(include_overlay, &extras);
    }
    pub(super) fn request(
        &mut self,
        method: &str,
        params: Value,
        now: i64,
    ) -> Result<Value, String> {
        match method {
            "status" => Ok(self.status()),
            "set_enabled" => {
                let enabled = params["enabled"]
                    .as_bool()
                    .ok_or("enabled must be a boolean")?;
                if enabled {
                    self.start_lan()?;
                } else {
                    self.stop_lan()?;
                }
                self.settings.enabled = enabled;
                self.settings.save(&self.directory)?;
                self.error = None;
                Ok(self.status())
            }
            "pair_invitation" => self
                .lan
                .as_ref()
                .ok_or("Turn on sharing with phones before pairing")?
                .invitation(now),
            "revoke_clients" => {
                if let Some(lan) = &self.lan {
                    lan.revoke()?;
                }
                Ok(self.status())
            }
            "set_remote" => {
                let mode = params["mode"]
                    .as_str()
                    .unwrap_or(&self.settings.remote.mode);
                let relay_url = params["relayUrl"]
                    .as_str()
                    .unwrap_or(&self.settings.remote.relay_url);
                let ssh_target = params["sshTarget"]
                    .as_str()
                    .unwrap_or(&self.settings.remote.ssh_target);
                let remote_port = params["remotePort"]
                    .as_u64()
                    .and_then(|n| u16::try_from(n).ok())
                    .unwrap_or(self.settings.remote.remote_port);
                let validated = relay::validate_remote(mode, relay_url, ssh_target, remote_port)?;
                self.settings.remote = validated;
                self.settings.save(&self.directory)?;
                self.refresh_lan_addresses();
                self.relay.sync(
                    self.lan.is_some(),
                    self.settings.port,
                    &self.settings.remote,
                    now,
                );
                Ok(self.status())
            }
            _ => Err(format!("Unknown phone sharing request: {method}")),
        }
    }
    fn status(&self) -> Value {
        let mut status = self
            .lan
            .as_ref()
            .map(LanServer::status)
            .unwrap_or_else(|| json!({"enabled": false, "pairedClients": 0, "peers": []}));
        status["supported"] = json!(self.secrets.is_some());
        status["on"] = json!(self.settings.enabled);
        status["name"] = json!(self.name);
        status["error"] = json!(self.error);
        status["remote"] = json!({
            "mode": self.settings.remote.mode,
            "relayUrl": self.settings.remote.relay_url,
            "sshTarget": self.settings.remote.ssh_target,
            "remotePort": self.settings.remote.remote_port,
            "state": self.relay.state,
            "error": self.relay.error,
        });
        status
    }
    fn pass(&mut self, now: i64) -> Result<(), String> {
        if self.lan.is_none() {
            self.relay.sync(false, 0, &self.settings.remote, now);
            return Ok(());
        }
        if now - self.address_at >= ADDRESS_EVERY_MS {
            self.address_at = now;
            self.refresh_lan_addresses();
        }
        self.relay
            .sync(true, self.settings.port, &self.settings.remote, now);
        self.project(now)?;
        self.cache
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .update(self.records.values(), now)?;
        self.error = None;
        Ok(())
    }
    /// The records as this pass has them: this computer, the readings when
    /// what was read changed, and usage when the ledger moved.
    pub(super) fn project(&mut self, now: i64) -> Result<(), String> {
        let device = projection::device(&self.settings.device_id, &self.name, now);
        self.records.entry(device.id()).or_insert(device);
        if let Some(snapshot) = self.snapshot.clone() {
            self.project_readings(&snapshot, now);
            self.project_usage(&snapshot, now)?;
        }
        let cutoff = keys::day_start(now - RETENTION_MS);
        self.records
            .retain(|_, r| r.is_visible(now) && (r.kind != "usage" || usage_day(r) >= cutoff));
        Ok(())
    }
    fn project_readings(&mut self, snapshot: &Value, now: i64) {
        let record = projection::readings(&self.settings.device_id, snapshot, now);
        let empty = record.payload["providers"]
            .as_array()
            .is_none_or(Vec::is_empty);
        // Accounts still being read would otherwise go out as none at all.
        if empty && projection::accounts_loading(snapshot) {
            return;
        }
        let facts = projection::reading_facts(&record.payload, now);
        if self.readings.as_ref() != Some(&facts) {
            self.readings = Some(facts);
            self.records.insert(record.id(), record);
        }
    }
}
fn now() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

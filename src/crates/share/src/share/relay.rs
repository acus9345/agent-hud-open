//! Supervises an optional outbound SSH reverse tunnel for TLS-passthrough relays:
//! `ssh -N -R 127.0.0.1:<remote_port>:127.0.0.1:<local_port> <ssh_target>`.
//! Raw encrypted TLS bytes flow straight from the relay's SNI passthrough listener
//! into the local `LanServer`, so the relay never terminates TLS or sees plaintext.
use super::settings::RemoteSettings;
use std::process::{Child, Command, Stdio};

const RESTART_BACKOFF_MS: i64 = 5_000;

#[derive(Default)]
pub(super) struct RelayTunnel {
    child: Option<Child>,
    active_key: Option<(String, u16, u16)>,
    last_attempt_ms: i64,
    pub(super) state: &'static str,
    pub(super) error: Option<String>,
}

impl RelayTunnel {
    pub(super) fn sync(
        &mut self,
        sharing_on: bool,
        local_port: u16,
        cfg: &RemoteSettings,
        now: i64,
    ) {
        if !sharing_on || cfg.mode != "relay" || local_port == 0 {
            self.stop();
            self.state = "off";
            self.error = None;
            return;
        }
        let target = cfg.ssh_target.trim();
        if target.is_empty() {
            self.stop();
            self.state = "manual";
            self.error = None;
            return;
        }
        let remote_port = if cfg.remote_port == 0 {
            4499
        } else {
            cfg.remote_port
        };
        let wanted = (target.to_owned(), remote_port, local_port);

        if self.active_key.as_ref() != Some(&wanted) {
            self.stop();
            self.last_attempt_ms = 0;
        }

        if let Some(child) = self.child.as_mut() {
            match child.try_wait() {
                Ok(None) => {
                    self.state = "connected";
                    self.error = None;
                    return;
                }
                Ok(Some(exit)) => {
                    self.child = None;
                    self.active_key = None;
                    self.state = "reconnecting";
                    self.error = Some(format!("SSH tunnel exited ({exit}); retrying…"));
                }
                Err(err) => {
                    self.child = None;
                    self.active_key = None;
                    self.state = "error";
                    self.error = Some(err.to_string());
                }
            }
        }

        if now - self.last_attempt_ms < RESTART_BACKOFF_MS {
            return;
        }
        self.last_attempt_ms = now;
        self.spawn(wanted);
    }

    fn spawn(&mut self, key: (String, u16, u16)) {
        let (target, remote_port, local_port) = &key;
        let forward = format!("127.0.0.1:{remote_port}:127.0.0.1:{local_port}");
        let mut cmd = Command::new("ssh");
        cmd.args([
            "-N",
            "-o",
            "BatchMode=yes",
            "-o",
            "ExitOnForwardFailure=yes",
            "-o",
            "ServerAliveInterval=20",
            "-o",
            "ServerAliveCountMax=3",
            "-R",
            &forward,
            target,
        ])
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
        super::os::configure_background_command(&mut cmd);

        match cmd.spawn() {
            Ok(child) => {
                self.child = Some(child);
                self.active_key = Some(key);
                self.state = "connected";
                self.error = None;
            }
            Err(err) => {
                self.state = "error";
                self.error = Some(format!("Could not start ssh tunnel: {err}"));
            }
        }
    }

    pub(super) fn stop(&mut self) {
        if let Some(mut child) = self.child.take() {
            let _ = child.kill();
            let _ = child.wait();
        }
        self.active_key = None;
    }
}

impl Drop for RelayTunnel {
    fn drop(&mut self) {
        self.stop();
    }
}

pub(super) fn validate_remote(
    mode: &str,
    relay_url: &str,
    ssh_target: &str,
    remote_port: u16,
) -> Result<RemoteSettings, String> {
    if !matches!(mode, "lan" | "tailscale" | "relay") {
        return Err("Remote access mode must be lan, tailscale, or relay".into());
    }
    let clean_url = relay_url.trim().trim_end_matches('/').to_owned();
    if mode == "relay" && !clean_url.is_empty() && !clean_url.starts_with("https://") {
        return Err("Relay URL must start with https://".into());
    }
    let clean_ssh = ssh_target.trim().to_owned();
    if !clean_ssh.is_empty()
        && (clean_ssh.starts_with('-') || clean_ssh.chars().any(char::is_whitespace))
    {
        return Err("SSH target must be a single host alias or user@host".into());
    }
    Ok(RemoteSettings {
        mode: mode.to_owned(),
        relay_url: clean_url,
        ssh_target: clean_ssh,
        remote_port: if remote_port == 0 { 4499 } else { remote_port },
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_remote_modes_and_urls() {
        let ok = validate_remote("relay", "https://hud.example.com/", "my-vps", 4499).unwrap();
        assert_eq!(ok.relay_url, "https://hud.example.com");
        assert_eq!(ok.ssh_target, "my-vps");
        assert!(validate_remote("bogus", "", "", 4499).is_err());
        assert!(validate_remote("relay", "http://insecure.example", "", 4499).is_err());
        assert!(validate_remote(
            "relay",
            "https://hud.example.com",
            "-oProxyCommand=bad",
            4499
        )
        .is_err());
    }
}

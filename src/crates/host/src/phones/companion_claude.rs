//! Adopts or unarchives Claude Code CLI sessions in Claude Desktop's
//! `claude-code-sessions` index so opening a session from a companion device
//! works. It never changes folder trust or permission state: an adopted
//! session opens in the default permission mode.
use agent_hud_runtime::NativePlatform;
use serde_json::{json, Value};
use std::{
    fs,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

pub(super) fn ensure_claude_desktop_session(
    platform: &NativePlatform,
    cli_id: &str,
    cwd: Option<&str>,
    title: Option<&str>,
    model: Option<&str>,
) -> Option<String> {
    let bare = cli_id.strip_prefix("local_").unwrap_or(cli_id);
    if uuid::Uuid::parse_str(bare).is_err() {
        return None;
    }
    let clean_cwd = cwd
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(|s| s.strip_prefix(r"\\?\").unwrap_or(s).to_owned())
        .unwrap_or_else(|| platform.home_dir().to_string_lossy().into_owned());

    let mut newest_org_dir: Option<(SystemTime, PathBuf)> = None;
    for base in platform.claude_desktop_bases() {
        let Ok(accounts) = fs::read_dir(base.join("claude-code-sessions")) else {
            continue;
        };
        for acct in accounts.flatten() {
            let Ok(orgs) = fs::read_dir(acct.path()) else {
                continue;
            };
            for org in orgs.flatten() {
                let org_path = org.path();
                if !org_path.is_dir() {
                    continue;
                }
                if let Ok(meta) = fs::metadata(&org_path) {
                    let mtime = meta.modified().unwrap_or(UNIX_EPOCH);
                    if newest_org_dir
                        .as_ref()
                        .is_none_or(|(best, _)| mtime > *best)
                    {
                        newest_org_dir = Some((mtime, org_path.clone()));
                    }
                }
                if let Some(found) = find_and_unarchive_in_org(&org_path, bare) {
                    return Some(found);
                }
            }
        }
    }

    let (_, target_dir) = newest_org_dir?;
    let local_id = format!("local_{bare}");
    let now_ms = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0);
    let record = json!({
        "sessionId": local_id,
        "cliSessionId": bare,
        "cwd": clean_cwd,
        "originCwd": clean_cwd,
        "createdAt": now_ms,
        "lastActivityAt": now_ms,
        "isArchived": false,
        "title": title.unwrap_or("Claude Code session"),
        "model": model.filter(|m| !m.is_empty()).unwrap_or("claude-opus-5-5"),
        "permissionMode": "default",
        "interruptedAwaitingPerson": Value::Null,
        "indexedAt": now_ms,
        "steeredByRemoteClient": Value::Null,
        "alwaysAllowedReasons": [],
        "sessionPermissionUpdates": [],
        "adoptedFromOtherSurface": true,
        "fromCliTranscript": true,
    });
    let file_path = target_dir.join(format!("{local_id}.json"));
    write_atomic(&file_path, record.to_string().as_bytes()).ok()?;
    Some(local_id)
}

fn find_and_unarchive_in_org(org_path: &Path, bare: &str) -> Option<String> {
    let files = fs::read_dir(org_path).ok()?;
    for file in files.flatten() {
        let path = file.path();
        if path.extension().and_then(|e| e.to_str()) != Some("json") {
            continue;
        }
        let Ok(bytes) = fs::read(&path) else {
            continue;
        };
        let Ok(mut val) = serde_json::from_slice::<Value>(&bytes) else {
            continue;
        };
        if val["cliSessionId"].as_str() == Some(bare) {
            let session_id = val["sessionId"]
                .as_str()
                .map(str::to_owned)
                .unwrap_or_else(|| format!("local_{bare}"));
            if val["isArchived"] == true {
                val["isArchived"] = json!(false);
                let _ = write_atomic(&path, val.to_string().as_bytes());
            }
            return Some(session_id);
        }
    }
    None
}

/// Writes through a sibling temp file and a rename, so Claude Desktop never
/// reads a half-written record.
fn write_atomic(path: &Path, bytes: &[u8]) -> std::io::Result<()> {
    let temp = path.with_extension("json.agent-hud-tmp");
    fs::write(&temp, bytes)?;
    fs::rename(&temp, path).inspect_err(|_| {
        let _ = fs::remove_file(&temp);
    })
}

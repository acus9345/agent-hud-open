//! Companion LAN extension routes for paired mobile apps (Android/iOS):
//! exposes live sessions, pending approvals, conversation details, remote
//! approval answering, and prompt sending/starting without altering the
//! read-only `/v1/snapshot` / `/v1/changes` / `/v1/events` feed schemas.
use crate::{
    backend::Backend,
    messages::AgentMessages,
    service::{wake_collector, REQUEST},
};
use agent_hud_messaging::UserTarget;
use agent_hud_share::{LanExtension, LanHandled, LanRequest};
use serde_json::{json, Value};
use std::{
    path::PathBuf,
    sync::{Arc, Condvar, Mutex, PoisonError, RwLock, Weak},
};

#[path = "companion_files.rs"]
mod files;
#[path = "companion_open.rs"]
mod open;
pub(crate) use open::open_session_on_desktop;
#[path = "companion_prefs.rs"]
mod prefs;
#[path = "companion_upload.rs"]
mod upload;

const MAX_COMPANION_SESSIONS: usize = 60;

pub(super) fn extension(
    backend: Weak<Mutex<Backend>>,
    latest_snapshot: Arc<RwLock<Option<Arc<Value>>>>,
    wake: Arc<(Mutex<u8>, Condvar)>,
    app_dir: Option<PathBuf>,
) -> LanExtension {
    Arc::new(move |request, _auth| {
        if !request.path.starts_with("/v1/companion/") {
            return Ok(None);
        }
        Ok(Some(dispatch(
            request,
            &backend,
            &latest_snapshot,
            &wake,
            app_dir.as_deref(),
        )))
    })
}

fn dispatch(
    request: &LanRequest,
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    app_dir: Option<&std::path::Path>,
) -> LanHandled {
    match (request.method.as_str(), request.path.as_str()) {
        ("GET", "/v1/companion/state") => handle_state(backend, latest_snapshot, app_dir),
        ("POST", "/v1/companion/session-detail") => handle_session_detail(&request.body, backend),
        ("POST", "/v1/companion/approval") => handle_approval(&request.body, backend, wake),
        ("POST", "/v1/companion/send") => {
            handle_send(&request.body, backend, latest_snapshot, wake, app_dir)
        }
        ("POST", "/v1/companion/send-now") => open::handle_send_now(&request.body, backend, wake),
        ("POST", "/v1/companion/open") => {
            open::handle_open(&request.body, backend, latest_snapshot)
        }
        ("POST", "/v1/companion/upload") => upload::handle_upload(&request.body),
        ("POST", "/v1/companion/file") => files::handle_file(&request.body),
        _ => json_reply(404, json!({"error": "Unknown companion route"})),
    }
}

fn json_reply(status: u16, body: Value) -> LanHandled {
    LanHandled {
        status,
        remember: true,
        body: Some(body),
    }
}

fn messages_handle(backend: &Weak<Mutex<Backend>>) -> Option<Arc<AgentMessages>> {
    let backend = backend.upgrade()?;
    let guard = backend.lock().unwrap_or_else(PoisonError::into_inner);
    guard.messages.clone()
}

fn trim_sessions(raw: &Value, limit: usize) -> Value {
    let Some(list) = raw.as_array() else {
        return json!([]);
    };
    let mut rows: Vec<&Value> = list.iter().collect();
    rows.sort_by(|a, b| {
        let rank = |s: &Value| match s["state"].as_str() {
            Some("waiting") => 0u8,
            Some("running") => 1u8,
            _ => 2u8,
        };
        rank(a)
            .cmp(&rank(b))
            .then_with(|| b["updatedAt"].as_str().cmp(&a["updatedAt"].as_str()))
    });
    Value::Array(rows.into_iter().take(limit).cloned().collect())
}

fn handle_state(
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
    app_dir: Option<&std::path::Path>,
) -> LanHandled {
    let snapshot = latest_snapshot
        .read()
        .unwrap_or_else(PoisonError::into_inner)
        .clone();
    let Some(snapshot) = snapshot else {
        return json_reply(503, json!({"error": "Application data is loading"}));
    };
    let (targets, agent_messages) = match messages_handle(backend) {
        Some(messages) => {
            let history = messages.history(&snapshot);
            let targets = messages
                .as_user(|m, host| m.user_sessions(host))
                .unwrap_or_else(|_| json!({"sessions": [], "starts": []}));
            (
                targets,
                history
                    .get("messages")
                    .cloned()
                    .unwrap_or_else(|| json!([])),
            )
        }
        None => (json!({"sessions": [], "starts": []}), json!([])),
    };
    json_reply(
        200,
        json!({
            "schemaVersion": 1,
            "generatedAtMs": chrono::Utc::now().timestamp_millis(),
            "approvals": snapshot.get("approvals").cloned().unwrap_or_else(|| json!([])),
            "sessions": trim_sessions(&snapshot["sessions"], MAX_COMPANION_SESSIONS),
            "targets": targets,
            "agentMessages": agent_messages,
            "events": snapshot.get("events").cloned().unwrap_or_else(|| json!([])),
            "indexing": snapshot.get("indexing").cloned().unwrap_or(Value::Null),
            "preferences": prefs::read_preferences(app_dir, &snapshot),
        }),
    )
}

fn parse_session_detail_body(body: &[u8]) -> Result<(String, usize, usize), String> {
    let payload: Value =
        serde_json::from_slice(body).map_err(|_| "Request body must be valid JSON")?;
    let id = payload["id"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or("Session id is required")?
        .to_owned();
    let message_limit = payload["messageLimit"]
        .as_u64()
        .map(|n| n.clamp(1, 800) as usize)
        .unwrap_or(400);
    let call_limit = payload["callLimit"]
        .as_u64()
        .map(|n| n.clamp(1, 200) as usize)
        .unwrap_or(80);
    Ok((id, message_limit, call_limit))
}

fn handle_session_detail(body: &[u8], backend: &Weak<Mutex<Backend>>) -> LanHandled {
    let (id, message_limit, call_limit) = match parse_session_detail_body(body) {
        Ok(parsed) => parsed,
        Err(error) => return json_reply(400, json!({"error": error})),
    };
    let Some(backend) = backend.upgrade() else {
        return json_reply(503, json!({"error": "Application is stopping"}));
    };
    let result = {
        let mut guard = backend.lock().unwrap_or_else(PoisonError::into_inner);
        let mut res = guard.runtime.local_query(
            "session_detail",
            json!({
                "id": id,
                "messageTail": true,
                "messageLimit": message_limit,
                "callLimit": call_limit,
            }),
        );
        if let Ok(ref mut detail) = res {
            backfill_tail(&mut guard, &id, message_limit, detail);
        }
        res
    };
    match result {
        Ok(detail) => json_reply(200, detail),
        Err(error) => json_reply(400, json!({"error": error})),
    }
}

/// A tail page cut short (fewer than `SHORT_TAIL` messages while earlier
/// ones exist) is refilled by paging forward from the start, at most
/// `MAX_PAGES` pages, keeping the last `message_limit` messages.
fn backfill_tail(guard: &mut Backend, id: &str, message_limit: usize, detail: &mut Value) {
    const SHORT_TAIL: usize = 120;
    const MAX_PAGES: usize = 8;
    let count = detail["messages"].as_array().map_or(0, Vec::len);
    if detail["messagesTruncatedBefore"] != true || count >= SHORT_TAIL {
        return;
    }
    let mut all = Vec::new();
    let mut cursor = 0u64;
    for _ in 0..MAX_PAGES {
        let Ok(page) = guard.runtime.local_query(
            "session_detail",
            json!({"id": id, "messageCursor": cursor, "messageLimit": message_limit, "callLimit": 1}),
        ) else {
            break;
        };
        if let Some(rows) = page["messages"].as_array() {
            all.extend(rows.iter().cloned());
            if all.len() > message_limit {
                all.drain(..all.len() - message_limit);
            }
        }
        match page["nextMessageCursor"].as_u64() {
            Some(next) if next > cursor && page["messagesComplete"] != true => cursor = next,
            _ => break,
        }
    }
    if all.len() > count {
        detail["messages"] = Value::Array(all);
    }
}

fn parse_approval_body(body: &[u8]) -> Result<(String, Value), String> {
    let payload: Value =
        serde_json::from_slice(body).map_err(|_| "Request body must be valid JSON")?;
    let id = payload["id"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or("Approval id is required")?
        .to_owned();
    let decision = payload
        .get("decision")
        .filter(|v| v.is_object())
        .cloned()
        .ok_or("Decision object is required")?;
    let kind = decision["kind"].as_str().unwrap_or_default();
    if !matches!(
        kind,
        "allow" | "deny" | "always" | "leave" | "answer" | "approve_plan" | "keep_planning"
    ) {
        return Err("Unsupported approval decision kind".into());
    }
    Ok((id, decision))
}

fn handle_approval(
    body: &[u8],
    backend: &Weak<Mutex<Backend>>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
) -> LanHandled {
    let (id, decision) = match parse_approval_body(body) {
        Ok(parsed) => parsed,
        Err(error) => return json_reply(400, json!({"error": error})),
    };
    let Some(backend) = backend.upgrade() else {
        return json_reply(503, json!({"error": "Application is stopping"}));
    };
    let result = {
        let mut guard = backend.lock().unwrap_or_else(PoisonError::into_inner);
        guard
            .runtime
            .request("answer_request", json!({"id": id, "decision": decision}))
    };
    match result {
        Ok(value) => {
            wake_collector(wake, REQUEST);
            json_reply(200, json!({"ok": true, "result": value}))
        }
        Err(error) => json_reply(400, json!({"error": error})),
    }
}

fn parse_send_body(body: &[u8]) -> Result<(UserTarget, String, bool), String> {
    let payload: Value =
        serde_json::from_slice(body).map_err(|_| "Request body must be valid JSON")?;
    let send_now = payload["sendNow"].as_bool().unwrap_or(false);
    let text = payload["text"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .ok_or("Message text is required")?
        .to_owned();
    let target: UserTarget =
        serde_json::from_value(payload).map_err(|_| "Invalid send target payload")?;
    Ok((target, text, send_now))
}

fn handle_send(
    body: &[u8],
    backend: &Weak<Mutex<Backend>>,
    latest_snapshot: &Arc<RwLock<Option<Arc<Value>>>>,
    wake: &Arc<(Mutex<u8>, Condvar)>,
    app_dir: Option<&std::path::Path>,
) -> LanHandled {
    let (target, text, send_now) = match parse_send_body(body) {
        Ok(parsed) => parsed,
        Err(error) => return json_reply(400, json!({"error": error})),
    };
    open::handle_send_with_wake(
        &target,
        &text,
        send_now,
        backend,
        latest_snapshot,
        wake,
        app_dir,
    )
}

#[cfg(test)]
#[path = "companion_tests.rs"]
mod tests;

//! `peek_session`: read-only inspection of another session's latest state and reply.
use super::*;

const DEFAULT_MAX_CHARS: usize = 1200;
const MIN_MAX_CHARS: usize = 200;
const MAX_MAX_CHARS: usize = 4000;

fn clip(text: &str, max_chars: usize) -> String {
    let trimmed = text.trim();
    if trimmed.chars().count() <= max_chars {
        return trimmed.to_owned();
    }
    let prefix: String = trimmed.chars().take(max_chars).collect();
    format!("{}…", prefix.trim_end())
}

fn non_empty(value: &Value) -> Option<&str> {
    value
        .as_str()
        .map(str::trim)
        .filter(|text| !text.is_empty())
}

fn latest_assistant_reply(messages: &[Value]) -> Option<String> {
    messages
        .iter()
        .rev()
        .find(|row| {
            row["role"] == "assistant"
                && row["toolCall"].is_null()
                && non_empty(&row["text"]).is_some()
        })
        .or_else(|| {
            messages
                .iter()
                .rev()
                .find(|row| row["role"] == "assistant" && non_empty(&row["text"]).is_some())
        })
        .and_then(|row| non_empty(&row["text"]))
        .map(str::to_owned)
}

impl Messaging {
    pub(crate) fn peek(&self, arguments: &Value, host: &Host<'_>) -> Result<Value> {
        let language = language(host.snapshot);
        let requested = arguments["session_id"]
            .as_str()
            .filter(|id| !id.is_empty())
            .ok_or("session_id is required")?;
        let max_chars = arguments["max_chars"]
            .as_u64()
            .and_then(|n| usize::try_from(n).ok())
            .unwrap_or(DEFAULT_MAX_CHARS)
            .clamp(MIN_MAX_CHARS, MAX_MAX_CHARS);

        let snap_session = find(host.snapshot, requested);
        let canonical_id = snap_session
            .and_then(|s| s["id"].as_str())
            .filter(|id| !id.is_empty())
            .unwrap_or(requested);

        let snap_live = snap_session
            .and_then(|s| non_empty(&s["liveFacts"]["message"]))
            .map(str::to_owned);

        let tail = if snap_live.is_none() || snap_session.is_none() {
            (host.query)(
                "session_detail",
                json!({"id": canonical_id, "messageTail": true, "messageLimit": 4, "callLimit": 1}),
            )
            .ok()
        } else {
            None
        };

        let detail_session = tail
            .as_ref()
            .map(|page| &page["session"])
            .filter(|s| s.is_object());
        let Some(session) = snap_session.or(detail_session) else {
            return Ok(refused(&words::unknown(language)));
        };

        let tail_reply = tail
            .as_ref()
            .and_then(|page| page["messages"].as_array())
            .and_then(|rows| latest_assistant_reply(rows));
        let live_msg = snap_live.or_else(|| {
            detail_session
                .and_then(|s| non_empty(&s["liveFacts"]["message"]))
                .map(str::to_owned)
        });

        let last_message = live_msg
            .as_deref()
            .or(tail_reply.as_deref())
            .map(|text| clip(text, max_chars));
        let recent_reply = tail_reply
            .as_deref()
            .or(live_msg.as_deref())
            .map(|text| clip(text, max_chars));

        let client = session["source"].as_str().unwrap_or_default();
        let project = session["cwd"]
            .as_str()
            .or_else(|| session["project"].as_str());
        let model = session["modelName"]
            .as_str()
            .or_else(|| session["model"].as_str())
            .filter(|m| !m.is_empty());

        Ok(json!({
            "id": session["id"].as_str().unwrap_or(canonical_id),
            "client": client,
            "title": session["title"],
            "project": project,
            "state": state(session),
            "model": model,
            "updatedAt": session["updatedAt"],
            "lastMessage": last_message,
            "recentReply": recent_reply,
        }))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use agent_hud_platform::delivery::{
        fixtures::StartFixture, CodexTransport, LocalClientPaths, Receipt,
    };
    use std::cell::Cell;
    use std::time::Duration;

    struct DummyCodex;
    impl CodexTransport for DummyCodex {
        fn deliver(
            &self,
            _data: &Path,
            _target: &SessionTarget,
            _request: &SendRequest,
            _timeout: Duration,
        ) -> delivery::Result<Receipt> {
            unreachable!()
        }
    }

    fn test_messaging(fixture: &StartFixture) -> Messaging {
        let dir = &fixture.root;
        let clients = Arc::new(
            LocalClients::with_transport(
                LocalClientPaths {
                    claude_channel: dir.join("claude-channel"),
                    codex_data: fixture.home().join(".codex"),
                    receipts: dir.join("client-receipts"),
                },
                Vec::new(),
                Arc::new(DummyCodex),
                Duration::from_secs(1),
            )
            .unwrap(),
        );
        Messaging::new(
            dir,
            Places {
                codex_data: fixture.home().join(".codex"),
                applications: Vec::new(),
                codex_command: false,
            },
            clients,
            fixture.starts(),
            Arc::new(|_| {}),
            Arc::new(|_| {}),
        )
    }

    #[test]
    fn peek_session_uses_snapshot_live_facts_without_querying_detail() {
        let fixture = StartFixture::new();
        let mut messaging = test_messaging(&fixture);
        let queries = Cell::new(0);
        let query = |_: &str, _: Value| -> crate::Result<Value> {
            queries.set(queries.get() + 1);
            Ok(json!({}))
        };
        let snapshot = json!({
            "settings": { "agentMessages": true },
            "sessions": [{
                "id": "codex-1",
                "source": "codex",
                "title": "Parser Review",
                "cwd": "/repo/app",
                "state": "idle",
                "model": "gpt-5",
                "updatedAt": "2026-11-22T01:00:00Z",
                "liveFacts": { "message": "  All parser checks passed.  " }
            }]
        });
        let host = Host {
            snapshot: &snapshot,
            query: &query,
            conversations: None,
        };
        let out = messaging
            .call(
                &json!({"name": "peek_session", "arguments": { "session_id": "codex-1" }}),
                &host,
                1000,
            )
            .unwrap();
        assert_eq!(queries.get(), 0);
        assert_eq!(out["id"], "codex-1");
        assert_eq!(out["client"], "codex");
        assert_eq!(out["state"], "idle");
        assert_eq!(out["lastMessage"], "All parser checks passed.");
        assert_eq!(out["recentReply"], "All parser checks passed.");
    }

    #[test]
    fn peek_session_falls_back_to_tail_query_and_clamps_max_chars() {
        let fixture = StartFixture::new();
        let mut messaging = test_messaging(&fixture);
        let query = |method: &str, params: Value| -> crate::Result<Value> {
            assert_eq!(method, "session_detail");
            assert_eq!(params["messageTail"], true);
            assert_eq!(params["messageLimit"], 4);
            assert_eq!(params["callLimit"], 1);
            Ok(json!({
                "session": {
                    "id": "antigravity:abc",
                    "source": "antigravity",
                    "title": "UI Worker",
                    "cwd": "/repo/ui",
                    "state": "running",
                    "model": "gemini-3",
                    "updatedAt": "2026-11-22T02:00:00Z"
                },
                "messages": [
                    { "role": "user", "text": "Inspect scrolling", "toolCall": null },
                    { "role": "assistant", "text": &"x".repeat(300), "toolCall": null }
                ]
            }))
        };
        let snapshot = json!({
            "settings": { "agentMessages": true },
            "sessions": [{
                "id": "antigravity:abc",
                "source": "antigravity",
                "title": "UI Worker",
                "cwd": "/repo/ui",
                "state": "running",
                "model": "gemini-3",
                "updatedAt": "2026-11-22T02:00:00Z",
                "liveFacts": {}
            }]
        });
        let host = Host {
            snapshot: &snapshot,
            query: &query,
            conversations: None,
        };
        // Passing max_chars: 50 clamps up to MIN_MAX_CHARS (200).
        let out = messaging
            .call(
                &json!({"name": "peek_session", "arguments": { "session_id": "abc", "max_chars": 50 }}),
                &host,
                1000,
            )
            .unwrap();
        assert_eq!(out["id"], "antigravity:abc");
        assert_eq!(out["state"], "running");
        assert_eq!(
            out["lastMessage"].as_str().unwrap(),
            format!("{}…", "x".repeat(200))
        );
        assert_eq!(out["recentReply"], out["lastMessage"]);
    }
}

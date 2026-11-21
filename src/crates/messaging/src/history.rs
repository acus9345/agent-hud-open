//! Reading the week-long journal of inter-agent messages for display in Sessions.
use super::*;

fn enrich_party(snapshot: &Value, party: &Party) -> Value {
    let live = (!party.session_id.is_empty())
        .then(|| find_in(snapshot, &party.client, &party.session_id))
        .flatten();
    let session_id = live
        .and_then(|session| session["id"].as_str())
        .filter(|id| !id.is_empty())
        .unwrap_or(&party.session_id);
    let title = live
        .and_then(|session| session["title"].as_str())
        .or(party.title.as_deref());
    json!({
        "client": party.client,
        "sessionId": session_id,
        "title": title,
    })
}

impl Messaging {
    /// All recorded inter-agent messages (newest first), with session titles and
    /// canonical session IDs enriched from `snapshot`.
    pub fn history(&self, snapshot: &Value) -> Value {
        let items: Vec<Value> = self
            .journal
            .all()
            .iter()
            .rev()
            .map(|message| {
                json!({
                    "id": message.id,
                    "kind": message.kind,
                    "chain": message.chain,
                    "count": message.count,
                    "from": enrich_party(snapshot, &message.from),
                    "to": enrich_party(snapshot, &message.to),
                    "replyTo": message.reply_to,
                    "repliedBy": message.replied_by,
                    "sentAtMs": message.sent_at_ms,
                    "state": message.state,
                    "reason": message.reason,
                    "answered": message.answered,
                    "steered": message.steered_into.is_some(),
                    "text": message.text,
                })
            })
            .collect();
        json!({ "messages": items })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use agent_hud_platform::delivery::{
        fixtures::StartFixture, CodexTransport, LocalClientPaths, Receipt, SendRequest,
    };
    use std::{path::Path, time::Duration};

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
    fn legacy_journal_without_text_deserializes_to_none_and_history_emits_null() {
        let fixture = StartFixture::new();
        let path = fixture.root.join("messaging/messages.json");
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(
            &path,
            serde_json::to_vec(&json!({
                "messages": [{
                    "id": "m-old",
                    "kind": "send",
                    "chain": "m-old",
                    "count": 1,
                    "from": { "client": "claude", "sessionId": "s1", "title": "Planner" },
                    "to": { "client": "codex", "sessionId": "s2", "title": null },
                    "form": "labelled",
                    "replyTo": null,
                    "requestId": "req-old",
                    "sentAtMs": 1_700_000_000_000i64,
                    "cursor": null,
                    "fingerprint": "abc",
                    "state": "queued",
                    "reason": null,
                    "answered": false,
                    "repliedBy": null,
                    "steeredInto": null
                }]
            }))
            .unwrap(),
        )
        .unwrap();

        let journal = Journal::load(path);
        assert_eq!(journal.all().len(), 1);
        assert_eq!(journal.all()[0].text, None);

        let messaging = test_messaging(&fixture);
        let out = messaging.history(&Value::Null);
        assert_eq!(out["messages"].as_array().unwrap().len(), 1);
        assert_eq!(out["messages"][0]["id"], "m-old");
        assert!(out["messages"][0]["text"].is_null());
    }

    #[test]
    fn message_with_text_round_trips_and_enriches_party_from_snapshot() {
        let fixture = StartFixture::new();
        let path = fixture.root.join("messaging/messages.json");
        let mut journal = Journal::load(path.clone());
        journal.add(Message {
            id: "m-new".into(),
            kind: Kind::Start,
            chain: "m-new".into(),
            count: 1,
            from: Party {
                client: "claude".into(),
                session_id: "s-from".into(),
                title: Some("Architect".into()),
            },
            to: Party {
                client: "antigravity".into(),
                session_id: "abc-123".into(),
                title: None,
            },
            form: PromptForm::Labelled,
            reply_to: None,
            request_id: "req-new".into(),
            sent_at_ms: 1_700_000_005_000i64,
            cursor: Some(0),
            fingerprint: "def".into(),
            state: "started".into(),
            reason: None,
            turn: None,
            answered: false,
            replied_by: None,
            steered_into: Some("turn-2".into()),
            call: None,
            text: Some("Please implement the parser tests.".into()),
        });

        let messaging = test_messaging(&fixture);
        let snapshot = json!({
            "sessions": [
                { "source": "claude", "id": "s-from", "title": "Architect" },
                { "source": "antigravity", "id": "antigravity:abc-123", "title": "Parser Worker" }
            ]
        });
        let out = messaging.history(&snapshot);
        let entry = &out["messages"][0];
        assert_eq!(entry["id"], "m-new");
        assert_eq!(entry["kind"], "start");
        assert_eq!(entry["steered"], true);
        assert_eq!(entry["text"], "Please implement the parser tests.");
        assert_eq!(entry["to"]["sessionId"], "antigravity:abc-123");
        assert_eq!(entry["to"]["title"], "Parser Worker");
    }
}

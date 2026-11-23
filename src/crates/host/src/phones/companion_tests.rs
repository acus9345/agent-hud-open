use super::*;

#[test]
fn parses_session_detail_and_clamps_limits() {
    let (id, messages, calls) =
        parse_session_detail_body(br#"{"id":"s-1","messageLimit":5000,"callLimit":0}"#).unwrap();
    assert_eq!(id, "s-1");
    assert_eq!(messages, 800);
    assert_eq!(calls, 1);
    assert!(parse_session_detail_body(br#"{"id":"  "}"#).is_err());
}

#[test]
fn validates_approval_decision_kind() {
    let (id, decision) =
        parse_approval_body(br#"{"id":"req-1","decision":{"kind":"allow"}}"#).unwrap();
    assert_eq!(id, "req-1");
    assert_eq!(decision["kind"], "allow");
    assert!(parse_approval_body(br#"{"id":"req-1","decision":{"kind":"bogus"}}"#).is_err());
}

#[test]
fn parses_send_targets_for_existing_and_new_sessions() {
    let (target, text, send_now) = parse_send_body(
        br#"{"kind":"session","client":"codex","sessionId":"x-1","text":"Run tests","sendNow":true}"#,
    )
    .unwrap();
    assert_eq!(text, "Run tests");
    assert!(send_now);
    assert_eq!(
        target,
        UserTarget::Session {
            client: "codex".into(),
            session_id: "x-1".into()
        }
    );
    let (target, text, send_now) = parse_send_body(
        br#"{"kind":"new","client":"claude","folder":"/work","text":"Check build"}"#,
    )
    .unwrap();
    assert_eq!(text, "Check build");
    assert!(!send_now);
    assert_eq!(
        target,
        UserTarget::New {
            client: "claude".into(),
            folder: "/work".into()
        }
    );
}

#[test]
fn trims_and_orders_waiting_and_running_sessions_first() {
    let raw = json!([
        {"id": "idle-new", "state": "idle", "updatedAt": "2026-11-22T12:00:00Z"},
        {"id": "run-old", "state": "running", "updatedAt": "2026-11-22T10:00:00Z"},
        {"id": "wait-old", "state": "waiting", "updatedAt": "2026-11-22T09:00:00Z"}
    ]);
    let trimmed = trim_sessions(&raw, 2);
    let ids: Vec<&str> = trimmed
        .as_array()
        .unwrap()
        .iter()
        .map(|row| row["id"].as_str().unwrap())
        .collect();
    assert_eq!(ids, vec!["wait-old", "run-old"]);
}

#[test]
fn open_route_rejects_missing_session_id() {
    let backend = Weak::new();
    let snapshot = Arc::new(RwLock::new(None));
    let reply = open::handle_open(br#"{"id":"   "}"#, &backend, &snapshot);
    assert_eq!(reply.status, 400);
}

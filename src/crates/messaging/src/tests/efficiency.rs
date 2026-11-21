use super::*;

#[test]
fn send_message_expect_reply_false_omits_reply_footer_and_marks_answered() {
    let mut world = World::on();
    let answer = world.call(
        Some(("claude", CLAUDE)),
        "send_message",
        json!({
            "to": CODEX,
            "text": "FYI build finished cleanly.",
            "expect_reply": false
        }),
    );
    assert_eq!(answer["delivered"], true);
    let sent = world.codex.0.lock().unwrap().pop().unwrap();
    assert!(
        sent.text.contains("FYI build finished cleanly."),
        "{}",
        sent.text
    );
    assert!(!sent.text.contains("Reply with "), "{}", sent.text);
    assert!(!sent.text.contains("reply_to:"), "{}", sent.text);

    let recorded = world.newest().unwrap();
    assert!(
        recorded.answered,
        "one-way send should mark answered=true immediately"
    );
    assert!(
        !world.messaging.waiting(),
        "answered one-way send should not poll"
    );

    // Default expect_reply keeps the reply prompt and unanswered status.
    let follow = world.call(
        Some(("claude", CLAUDE)),
        "send_message",
        json!({
            "to": CODEX,
            "text": "Please review this diff."
        }),
    );
    assert_eq!(follow["delivered"], true);
    let follow_sent = world.codex.0.lock().unwrap().pop().unwrap();
    assert!(
        follow_sent.text.contains("reply_to:"),
        "{}",
        follow_sent.text
    );
    assert!(!world.newest().unwrap().answered);
}

#[test]
fn send_message_rejects_non_boolean_expect_reply() {
    let mut world = World::on();
    let err = world
        .messaging
        .call(
            &json!({
                "name": "send_message",
                "arguments": { "to": CODEX, "text": "Hi", "expect_reply": "no" },
                "caller": { "client": "claude", "sessionId": CLAUDE, "turnId": null }
            }),
            &Host {
                snapshot: &world.snapshot,
                query: &world.transcripts.query(),
                conversations: None,
            },
            1000,
        )
        .unwrap_err();
    assert!(err.contains("expect_reply"));
}

#[test]
fn list_sessions_filters_by_client_project_reachable_and_compact() {
    let mut world = World::on();

    // Filter by client.
    let claude_only = world.call(
        Some(("codex", CODEX)),
        "list_sessions",
        json!({ "client": "claude" }),
    );
    let rows = claude_only["sessions"].as_array().unwrap();
    assert_eq!(rows.len(), 2);
    assert!(rows.iter().all(|row| row["client"] == "claude"));
    assert!(claude_only.get("startFolders").is_some());

    // Filter by project substring + reachable_only.
    let reachable_only = world.call(
        Some(("codex", CODEX)),
        "list_sessions",
        json!({ "project": "project", "reachable_only": true }),
    );
    let reach_rows = reachable_only["sessions"].as_array().unwrap();
    assert!(reach_rows.iter().any(|row| row["id"] == CLAUDE));
    assert!(reach_rows.iter().all(|row| row["reachable"] == json!(true)));
    assert!(!reach_rows.iter().any(|row| row["id"] == QUIET));

    // Compact mode omits startFolders, steerReason, and verbose reason on reachable rows.
    let compact = world.call(
        Some(("codex", CODEX)),
        "list_sessions",
        json!({ "compact": true }),
    );
    assert!(compact.get("startFolders").is_none());
    let compact_rows = compact["sessions"].as_array().unwrap();
    let reachable_claude = compact_rows.iter().find(|r| r["id"] == CLAUDE).unwrap();
    assert_eq!(reachable_claude["reachable"], json!(true));
    assert!(reachable_claude.get("reason").is_none());
    assert!(reachable_claude.get("steerReason").is_none());

    // Unreachable sessions still explain why in compact mode.
    let unreachable_quiet = compact_rows.iter().find(|r| r["id"] == QUIET).unwrap();
    assert_eq!(unreachable_quiet["reachable"], json!(false));
    assert!(unreachable_quiet.get("reason").is_some());
    assert!(unreachable_quiet.get("steerReason").is_none());
}

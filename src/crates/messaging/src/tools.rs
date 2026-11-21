//! The MCP tools agents see, as the MCP relay lists them.
use super::*;

impl Messaging {
    /// The group's tools while it is on, nothing while it is off.
    pub fn tools(enabled: bool) -> Value {
        if !enabled {
            return json!({"tools":[]});
        }
        json!({"tools":[
            {"name":"list_sessions","readOnly":true,
             "description":"List main agent sessions visible to Agent HUD with id, client, title, project, state, model, reachable/reason, steerable/steerReason, self, and startFolders. Supports optional client, project, reachable_only, and compact filters.",
             "inputSchema":{"type":"object","properties":{
                "client":{"type":"string","description":"Filter to one client source (e.g. claude, codex, antigravity)."},
                "project":{"type":"string","description":"Filter by project folder path substring."},
                "reachable_only":{"type":"boolean","description":"When true, return only currently reachable sessions."},
                "compact":{"type":"boolean","description":"Omit startFolders and verbose reason/steerReason fields on reachable sessions."}
             },"additionalProperties":false}},
            {"name":"peek_session","readOnly":true,
             "description":"Read another session's current state, model, lastMessage, and recentReply without disturbing it. Uses live snapshot first and falls back to transcript tail if needed.",
             "inputSchema":{"type":"object","properties":{
                "session_id":{"type":"string","description":"Target session id from list_sessions."},
                "max_chars":{"type":"integer","minimum":200,"maximum":4000,"description":"Max characters for lastMessage/recentReply (default 1200, clamped 200..4000)."}
             },"required":["session_id"],"additionalProperties":false}},
            {"name":"list_models","readOnly":true,
             "description":"List available models, quotas, defaults, and atStart/perMessage support for Claude Code, Codex, Antigravity, and DeepSeek Harness. Omit model in send_message/start_session unless the user asked for one.",
             "inputSchema":{"type":"object","properties":{"client":{"type":"string","enum":["claude","codex","antigravity","deepseek"],"description":"One client; all four when left out."}},"additionalProperties":false}},
            {"name":"send_message","readOnly":false,
             "description":"Send text to another main session (sub-agents reply in their own turn, never via send_message). Default delivery is queue: waits until idle/turn end (except Qoder rewake or open Pi/OpenCode observers). delivery=steer injects into an active turn on Codex or DeepSeek Harness only when the user explicitly asked; steer is refused (never queued) if idle, waiting, a reply, or unsupported. Set expect_reply=false for one-way FYI updates (omits reply prompt and skips reply polling). Pass reply_to with a received message id to answer its sender. Finish your thought in one message (max 10 consecutive passes by default before user input). Returns delivered true/false/null.",
             "inputSchema":{"type":"object","properties":{
                "to":{"type":"string","description":"Recipient session id from list_sessions (optional if reply_to is set)."},
                "text":{"type":"string","description":"Complete message text."},
                "reply_to":{"type":"string","description":"Received message id to reply to its sender."},
                "expect_reply":{"type":"boolean","description":"Default true. Set false for one-way FYI/status updates that need no reply."},
                "delivery":{"type":"string","enum":["queue","steer"],"description":"Default queue. steer only when user explicitly asked (Codex/DeepSeek Harness only)."},
                "model":{"type":"string","description":"Only when user asked: model id/name (Antigravity/DeepSeek Harness perMessage)."}
             },"required":["text"],"additionalProperties":false}},
            {"name":"start_session","readOnly":false,
             "description":"Start a new main Claude Code, Codex, Antigravity, or DeepSeek Harness session with text as its first message in a known folder from list_sessions startFolders (default: your project; empty for task folder). Counts as one pass. Main sessions only. Returns delivered true with sessionId once assigned.",
             "inputSchema":{"type":"object","properties":{"client":{"type":"string","enum":["claude","codex","antigravity","deepseek"]},"folder":{"type":"string","description":"Folder from startFolders, or empty for task folder. Defaults to your project."},"text":{"type":"string","description":"Initial message text."},"model":{"type":"string","description":"Only when user asked (clients with atStart true)."}},"required":["client","text"],"additionalProperties":false}}
        ]})
    }
}

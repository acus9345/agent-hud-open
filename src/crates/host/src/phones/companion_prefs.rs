//! Reads the desktop's active HUD display preferences (`presentation.json` +
//! `snapshot.settings`) so paired mobile companions mirror the exact same
//! provider order, visible/hidden quota windows, and percentage display mode.
use serde_json::{json, Map, Value};
use std::{fs, path::Path};

pub(super) fn read_preferences(app_dir: Option<&Path>, snapshot: &Value) -> Value {
    let settings = &snapshot["settings"];
    let quota_display = settings["quotaDisplay"].as_str().unwrap_or("consumed");
    let animations = settings["animations"].as_bool().unwrap_or(true);
    let layout = app_dir
        .map(|dir| dir.join("presentation.json"))
        .and_then(|path| fs::read(path).ok())
        .and_then(|bytes| serde_json::from_slice::<Value>(&bytes).ok())
        .map(|saved| active_layout(&saved))
        .unwrap_or_else(|| json!({}));

    json!({
        "quotaDisplay": quota_display,
        "animations": animations,
        "order": layout.get("order").cloned().unwrap_or_else(|| json!([])),
        "hudAgents": layout["hud"]["agents"].as_array().cloned().map(Value::Array).unwrap_or_else(|| json!([])),
        "blocks": layout.get("blocks").cloned().unwrap_or_else(|| json!({})),
        "hiddenSections": layout.get("hiddenSections").cloned().unwrap_or_else(|| json!([])),
    })
}

/// Picks the effective panel layout: merges the first screen-specific layout
/// over `sharedLayout` so per-screen customizations on the PC apply on mobile.
fn active_layout(saved: &Value) -> Value {
    let mut merged = saved["sharedLayout"]
        .as_object()
        .cloned()
        .unwrap_or_default();

    if let Some(screen_layout) = saved["layouts"]
        .as_object()
        .and_then(|map| {
            map.values()
                .find(|v| v.is_object() && !v.as_object().unwrap().is_empty())
        })
        .and_then(Value::as_object)
    {
        for (key, value) in screen_layout {
            if key == "blocks" {
                let mut blocks = merged
                    .get("blocks")
                    .and_then(Value::as_object)
                    .cloned()
                    .unwrap_or_default();
                if let Some(screen_blocks) = value.as_object() {
                    merge_blocks(&mut blocks, screen_blocks);
                }
                merged.insert("blocks".into(), Value::Object(blocks));
            } else {
                merged.insert(key.clone(), value.clone());
            }
        }
    }
    Value::Object(merged)
}

fn merge_blocks(target: &mut Map<String, Value>, patch: &Map<String, Value>) {
    for (k, v) in patch {
        target.insert(k.clone(), v.clone());
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn extracts_order_hud_agents_and_blocks_from_presentation_json() {
        let dir = tempfile::tempdir().unwrap();
        let saved = json!({
            "version": 3,
            "sharedLayout": {
                "blocks": {
                    "claude": { "hiddenRows": ["5h"] }
                }
            },
            "layouts": {
                "display-1": {
                    "order": ["claude", "antigravity", "api:deepseek"],
                    "hud": { "agents": ["claude", "antigravity", "deepseek"] },
                    "blocks": {
                        "antigravity": { "hiddenRows": ["gemini-4-argon"] }
                    }
                }
            }
        });
        fs::write(dir.path().join("presentation.json"), saved.to_string()).unwrap();

        let snap = json!({ "settings": { "quotaDisplay": "remaining", "animations": true } });
        let prefs = read_preferences(Some(dir.path()), &snap);
        assert_eq!(prefs["quotaDisplay"], "remaining");
        assert_eq!(prefs["order"][0], "claude");
        assert_eq!(prefs["hudAgents"][2], "deepseek");
        assert_eq!(prefs["blocks"]["claude"]["hiddenRows"][0], "5h");
        assert_eq!(
            prefs["blocks"]["antigravity"]["hiddenRows"][0],
            "gemini-4-argon"
        );
    }
}

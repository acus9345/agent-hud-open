//! Chunked file download / preview handler for paired mobile companions (`POST /v1/companion/file`).
//! Supports `file:///...` links (with `#L10-L20` fragments and `%20` percent encoding)
//! as well as direct OS file paths, reading bounded chunks up to 512 KiB per call.
use super::json_reply;
use agent_hud_share::LanHandled;
use base64::Engine as _;
use serde_json::{json, Value};
use std::{
    fs::File,
    io::{Read, Seek, SeekFrom},
    path::{Path, PathBuf},
};

const MAX_FILE_BYTES: u64 = 64 * 1024 * 1024;
const MAX_CHUNK_BYTES: usize = 512 * 1024;

pub(super) fn handle_file(body: &[u8]) -> LanHandled {
    let payload: Value = match serde_json::from_slice(body) {
        Ok(v) => v,
        Err(_) => return json_reply(400, json!({"error": "Request body must be valid JSON"})),
    };
    let Some(raw_path) = payload["path"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
    else {
        return json_reply(400, json!({"error": "File path is required"}));
    };
    let offset = payload["offset"].as_u64().unwrap_or(0);
    let length = payload["length"]
        .as_u64()
        .map(|n| (n as usize).min(MAX_CHUNK_BYTES))
        .unwrap_or(MAX_CHUNK_BYTES);

    let path = normalize_file_path(raw_path);
    let meta = match std::fs::metadata(&path) {
        Ok(m) if m.is_file() => m,
        Ok(_) => return json_reply(400, json!({"error": "Path is not a regular file"})),
        Err(_) => return json_reply(404, json!({"error": "File not found on desktop"})),
    };
    let total_size = meta.len();
    if total_size > MAX_FILE_BYTES {
        return json_reply(400, json!({"error": "File exceeds 64 MiB download limit"}));
    }
    let filename = path
        .file_name()
        .and_then(|s| s.to_str())
        .unwrap_or("file")
        .to_owned();
    let mime = guess_mime(&path);

    if length == 0 || offset >= total_size {
        return json_reply(
            200,
            json!({
                "ok": true,
                "filename": filename,
                "mimeType": mime,
                "size": total_size,
                "offset": offset.min(total_size),
                "dataBase64": "",
                "hasMore": offset < total_size,
            }),
        );
    }

    let mut file = match File::open(&path) {
        Ok(f) => f,
        Err(err) => return json_reply(403, json!({"error": format!("Cannot open file: {err}")})),
    };
    if let Err(err) = file.seek(SeekFrom::Start(offset)) {
        return json_reply(500, json!({"error": format!("Cannot seek file: {err}")}));
    }
    let to_read = (total_size - offset).min(length as u64) as usize;
    let mut buf = vec![0u8; to_read];
    if let Err(err) = file.read_exact(&mut buf) {
        return json_reply(500, json!({"error": format!("Cannot read file: {err}")}));
    }
    let next_offset = offset + buf.len() as u64;
    let encoded = base64::engine::general_purpose::STANDARD.encode(&buf);

    json_reply(
        200,
        json!({
            "ok": true,
            "filename": filename,
            "mimeType": mime,
            "size": total_size,
            "offset": offset,
            "dataBase64": encoded,
            "hasMore": next_offset < total_size,
        }),
    )
}

fn normalize_file_path(raw: &str) -> PathBuf {
    let trimmed = raw.trim();
    let without_fragment = trimmed.split('#').next().unwrap_or(trimmed);
    let stripped = without_fragment
        .strip_prefix("file://localhost")
        .or_else(|| without_fragment.strip_prefix("file://"))
        .unwrap_or(without_fragment);
    let decoded = percent_decode(stripped);
    let cleaned = decoded.strip_prefix(r"\\?\").unwrap_or(&decoded);
    // Handle `file:///C:/...` -> `C:/...` on Windows while keeping `/Users/...` on Unix.
    let final_str = if cleaned.len() >= 3
        && cleaned.as_bytes()[0] == b'/'
        && cleaned.as_bytes()[1].is_ascii_alphabetic()
        && cleaned.as_bytes()[2] == b':'
    {
        &cleaned[1..]
    } else {
        cleaned
    };
    PathBuf::from(final_str)
}

fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i] == b'%' && i + 2 < bytes.len() {
            if let (Some(hi), Some(lo)) = (hex_val(bytes[i + 1]), hex_val(bytes[i + 2])) {
                out.push((hi << 4) | lo);
                i += 3;
                continue;
            }
        }
        out.push(bytes[i]);
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn hex_val(b: u8) -> Option<u8> {
    match b {
        b'0'..=b'9' => Some(b - b'0'),
        b'a'..=b'f' => Some(b - b'a' + 10),
        b'A'..=b'F' => Some(b - b'A' + 10),
        _ => None,
    }
}

fn guess_mime(path: &Path) -> &'static str {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(str::to_ascii_lowercase)
        .unwrap_or_default();
    match ext.as_str() {
        "png" => "image/png",
        "jpg" | "jpeg" => "image/jpeg",
        "webp" => "image/webp",
        "gif" => "image/gif",
        "svg" => "image/svg+xml",
        "pdf" => "application/pdf",
        "apk" => "application/vnd.android.package-archive",
        "zip" | "gz" | "tar" => "application/octet-stream",
        "json" => "application/json",
        "md" | "markdown" => "text/markdown",
        "txt" | "log" | "rs" | "kt" | "kts" | "ts" | "tsx" | "js" | "mjs" | "css" | "html"
        | "xml" | "yml" | "yaml" | "toml" | "sh" | "ps1" | "py" | "java" | "c" | "cpp" | "h"
        | "go" | "swift" | "gradle" | "properties" | "cfg" | "conf" | "ini" => "text/plain",
        _ => "application/octet-stream",
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_file_uris_with_spaces_and_line_fragments() {
        let p = normalize_file_path("file:///C:/Users/User/Agent%20HUD.exe#L10-L20");
        assert_eq!(p, PathBuf::from("C:/Users/User/Agent HUD.exe"));
        let u = normalize_file_path("file:///Users/me/notes.md#L1");
        assert_eq!(u, PathBuf::from("/Users/me/notes.md"));
    }

    #[test]
    fn reads_file_in_chunks() {
        let dir = tempfile::tempdir().unwrap();
        let file_path = dir.path().join("sample.txt");
        std::fs::write(&file_path, b"abcdefghij").unwrap();

        let r1 = handle_file(
            json!({"path": file_path.to_string_lossy(), "offset": 0, "length": 4})
                .to_string()
                .as_bytes(),
        );
        assert_eq!(r1.status, 200);
        let b1 = r1.body.unwrap();
        assert_eq!(b1["size"], 10);
        assert_eq!(b1["hasMore"], true);
        assert_eq!(
            base64::engine::general_purpose::STANDARD
                .decode(b1["dataBase64"].as_str().unwrap())
                .unwrap(),
            b"abcd"
        );

        let r2 = handle_file(
            json!({"path": file_path.to_string_lossy(), "offset": 4, "length": 100})
                .to_string()
                .as_bytes(),
        );
        let b2 = r2.body.unwrap();
        assert_eq!(b2["hasMore"], false);
        assert_eq!(
            base64::engine::general_purpose::STANDARD
                .decode(b2["dataBase64"].as_str().unwrap())
                .unwrap(),
            b"efghij"
        );
    }
}

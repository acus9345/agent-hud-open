//! Chunked file upload handler for paired mobile companions (`POST /v1/companion/upload`).
//! Each chunk stays well below `MAX_REQUEST` (1 MiB) while allowing photos,
//! screenshots, PDFs, and logs up to 20 MiB to be transferred over LAN/Tailscale.
use agent_hud_share::LanHandled;
use base64::Engine as _;
use serde_json::{json, Value};
use std::{
    fs::{self, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    time::{Duration, SystemTime},
};

const MAX_CHUNKS: usize = 40;
const MAX_UPLOAD_BYTES: u64 = 20 * 1024 * 1024;
const STALE_PART_AGE: Duration = Duration::from_secs(3600);

fn json_reply(status: u16, body: Value) -> LanHandled {
    LanHandled {
        status,
        remember: true,
        body: Some(body),
    }
}

pub(super) fn handle_upload(body: &[u8]) -> LanHandled {
    let dir = match uploads_dir() {
        Ok(dir) => dir,
        Err(error) => return json_reply(500, json!({"error": error})),
    };
    handle_upload_in(body, &dir)
}

fn handle_upload_in(body: &[u8], dir: &Path) -> LanHandled {
    let req = match parse_upload_body(body) {
        Ok(parsed) => parsed,
        Err(error) => return json_reply(400, json!({"error": error})),
    };
    if let Err(error) = fs::create_dir_all(dir) {
        return json_reply(
            500,
            json!({"error": format!("Cannot create uploads dir: {error}")}),
        );
    }
    prune_stale_parts(dir);

    let safe_name = sanitize_filename(&req.filename);
    let final_name = format!("{}-{safe_name}", req.upload_id);
    let part_path = dir.join(format!("{final_name}.part"));
    let final_path = dir.join(&final_name);

    let result = (|| -> Result<u64, String> {
        let mut opts = OpenOptions::new();
        opts.create(true).write(true);
        if req.chunk_index == 0 {
            opts.truncate(true);
        } else {
            opts.append(true);
        }
        let mut file = opts
            .open(&part_path)
            .map_err(|e| format!("Cannot open upload file: {e}"))?;
        let current_len = file.metadata().map(|m| m.len()).unwrap_or(0);
        if current_len.saturating_add(req.bytes.len() as u64) > MAX_UPLOAD_BYTES {
            let _ = fs::remove_file(&part_path);
            return Err("Upload exceeds 20 MiB limit".into());
        }
        file.write_all(&req.bytes)
            .map_err(|e| format!("Cannot write upload chunk: {e}"))?;
        file.flush()
            .map_err(|e| format!("Cannot flush upload: {e}"))?;
        Ok(current_len + req.bytes.len() as u64)
    })();

    let written = match result {
        Ok(n) => n,
        Err(error) => return json_reply(400, json!({"error": error})),
    };

    if req.chunk_index + 1 >= req.total_chunks {
        if let Err(error) = fs::rename(&part_path, &final_path) {
            return json_reply(
                500,
                json!({"error": format!("Cannot finalize upload: {error}")}),
            );
        }
        json_reply(
            200,
            json!({
                "ok": true,
                "complete": true,
                "path": final_path.to_string_lossy(),
                "filename": safe_name,
                "size": written,
            }),
        )
    } else {
        json_reply(
            200,
            json!({
                "ok": true,
                "complete": false,
                "chunkIndex": req.chunk_index,
                "size": written,
            }),
        )
    }
}

struct UploadChunk {
    upload_id: String,
    filename: String,
    chunk_index: usize,
    total_chunks: usize,
    bytes: Vec<u8>,
}

fn parse_upload_body(body: &[u8]) -> Result<UploadChunk, String> {
    let payload: Value =
        serde_json::from_slice(body).map_err(|_| "Request body must be valid JSON")?;
    let upload_id = payload["uploadId"]
        .as_str()
        .map(str::trim)
        .filter(|s| {
            (8..=64).contains(&s.len())
                && s.bytes()
                    .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
        })
        .ok_or("Invalid uploadId")?
        .to_owned();
    let filename = payload["filename"]
        .as_str()
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("attachment.bin")
        .to_owned();
    let total_chunks = payload["totalChunks"]
        .as_u64()
        .map(|n| n as usize)
        .filter(|n| (1..=MAX_CHUNKS).contains(n))
        .ok_or("totalChunks must be between 1 and 40")?;
    let chunk_index = payload["chunkIndex"]
        .as_u64()
        .map(|n| n as usize)
        .filter(|n| *n < total_chunks)
        .ok_or("chunkIndex out of bounds")?;
    let encoded = payload["dataBase64"]
        .as_str()
        .ok_or("dataBase64 is required")?;
    let bytes = base64::engine::general_purpose::STANDARD
        .decode(encoded)
        .map_err(|_| "dataBase64 must be valid base64")?;
    Ok(UploadChunk {
        upload_id,
        filename,
        chunk_index,
        total_chunks,
        bytes,
    })
}

fn sanitize_filename(raw: &str) -> String {
    let base = raw.rsplit(['/', '\\']).next().unwrap_or(raw);
    let cleaned: String = base
        .chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_') {
                c
            } else {
                '_'
            }
        })
        .take(80)
        .collect();
    let trimmed = cleaned.trim_matches('.');
    if trimmed.is_empty() {
        "attachment.bin".into()
    } else {
        trimmed.into()
    }
}

fn uploads_dir() -> Result<PathBuf, String> {
    if let Ok(local) = std::env::var("LOCALAPPDATA") {
        return Ok(PathBuf::from(local)
            .join("Agent HUD")
            .join("companion-uploads"));
    }
    if let Ok(home) = std::env::var("HOME") {
        return Ok(PathBuf::from(home)
            .join(".agent-hud")
            .join("companion-uploads"));
    }
    Ok(std::env::temp_dir().join("agent-hud-companion-uploads"))
}

fn prune_stale_parts(dir: &Path) {
    let Ok(entries) = fs::read_dir(dir) else {
        return;
    };
    let now = SystemTime::now();
    for entry in entries.flatten() {
        let path = entry.path();
        if path.extension().and_then(|e| e.to_str()) != Some("part") {
            continue;
        }
        let expired = entry
            .metadata()
            .and_then(|m| m.modified())
            .ok()
            .and_then(|m| now.duration_since(m).ok())
            .is_some_and(|age| age > STALE_PART_AGE);
        if expired {
            let _ = fs::remove_file(path);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitizes_traversal_and_special_characters() {
        assert_eq!(sanitize_filename("../../evil file!.png"), "evil_file_.png");
        assert_eq!(sanitize_filename("..."), "attachment.bin");
    }

    #[test]
    fn assembles_chunked_upload_and_returns_final_path() {
        let dir = tempfile::tempdir().unwrap();
        let part1 = base64::engine::general_purpose::STANDARD.encode(b"hello ");
        let part2 = base64::engine::general_purpose::STANDARD.encode(b"world");
        let r1 = handle_upload_in(
            json!({
                "uploadId": "upload-12345678",
                "filename": "notes.txt",
                "chunkIndex": 0,
                "totalChunks": 2,
                "dataBase64": part1
            })
            .to_string()
            .as_bytes(),
            dir.path(),
        );
        assert_eq!(r1.status, 200);
        assert_eq!(r1.body.unwrap()["complete"], false);

        let r2 = handle_upload_in(
            json!({
                "uploadId": "upload-12345678",
                "filename": "notes.txt",
                "chunkIndex": 1,
                "totalChunks": 2,
                "dataBase64": part2
            })
            .to_string()
            .as_bytes(),
            dir.path(),
        );
        assert_eq!(r2.status, 200);
        let body = r2.body.unwrap();
        assert_eq!(body["complete"], true);
        assert_eq!(body["size"], 11);
        let written = fs::read_to_string(body["path"].as_str().unwrap()).unwrap();
        assert_eq!(written, "hello world");
    }
}

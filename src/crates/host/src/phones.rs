//! The host's own sharing with paired phones: quota readings, token usage,
//! and companion control routes over the LAN link, for a run with no product
//! that shares instead.
use crate::{backend::Backend, extension::ProductExtension};
use agent_hud_share::PhoneShare;
use serde_json::Value;
use std::{
    path::PathBuf,
    sync::{Arc, Condvar, Mutex, PoisonError, RwLock, Weak},
};

#[path = "phones/companion.rs"]
mod companion;

/// `directory` is None for the demo, which shares nothing.
pub(crate) fn start(
    directory: Option<PathBuf>,
    extension: Option<&dyn ProductExtension>,
    backend: Weak<Mutex<Backend>>,
    latest_snapshot: Arc<RwLock<Option<Arc<Value>>>>,
    wake: Arc<(Mutex<u8>, Condvar)>,
) -> Option<Arc<PhoneShare>> {
    let directory = directory?;
    if extension.is_some_and(|product| product.shares_with_phones()) {
        return None;
    }
    let query_backend = backend.clone();
    let query = Arc::new(move |method: &str, params| {
        let backend = query_backend.upgrade().ok_or("Application is stopping")?;
        let mut backend = backend.lock().unwrap_or_else(PoisonError::into_inner);
        backend.runtime.local_query(method, params)
    });
    let app_dir = directory.parent().map(std::path::Path::to_path_buf);
    let routes = companion::extension(backend, latest_snapshot, wake, app_dir);
    match PhoneShare::start_with_extension(directory, query, routes) {
        Ok(share) => Some(Arc::new(share)),
        Err(error) => {
            eprintln!("Sharing with phones could not start: {error}");
            None
        }
    }
}

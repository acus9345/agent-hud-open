//! Windows: the computer name from the environment, grants in the current
//! user's Credential Manager.
use agent_hud_lan::credentials::{SecretStore, WindowsSecrets};
use std::sync::Arc;

pub fn computer_name() -> String {
    super::super::environment_name("COMPUTERNAME").unwrap_or_else(|| "Windows PC".into())
}
pub(crate) fn secrets() -> Option<Arc<dyn SecretStore>> {
    Some(Arc::new(WindowsSecrets))
}
pub(crate) fn configure_background_command(command: &mut std::process::Command) {
    use std::os::windows::process::CommandExt;
    const CREATE_NO_WINDOW: u32 = 0x0800_0000;
    command.creation_flags(CREATE_NO_WINDOW);
}

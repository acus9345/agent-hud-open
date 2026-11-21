//! Windows: the app keeps its taskbar entry, registers Ctrl+Alt+H to open
//! Settings, and applies Win11 DWM rounded corners & dark borders to frameless
//! auxiliary windows.
use tauri::Manager;

#[path = "windows/hotkey.rs"]
mod hotkey;
#[path = "windows/session.rs"]
mod session;

pub(crate) fn prepare(app: &tauri::AppHandle) -> Result<(), String> {
    session::install(app)
}

pub(crate) fn install(app: &tauri::AppHandle) {
    hotkey::install(app);
}

pub(crate) fn shutdown(app: &tauri::AppHandle) {
    hotkey::shutdown(app);
}

pub(crate) fn set_window_border(window: &tauri::WebviewWindow, light: bool) {
    use windows::Win32::{
        Foundation::HWND,
        Graphics::Dwm::{DwmSetWindowAttribute, DWMWA_BORDER_COLOR, DWMWA_COLOR_DEFAULT},
    };
    let Ok(raw) = window.hwnd() else {
        return;
    };
    let hwnd = HWND(raw.0);
    let border = if light {
        DWMWA_COLOR_DEFAULT
    } else {
        0x0026_2222u32
    };
    unsafe {
        let _ = DwmSetWindowAttribute(
            hwnd,
            DWMWA_BORDER_COLOR,
            (&raw const border).cast(),
            size_of::<u32>() as u32,
        );
    }
}

pub(crate) fn window_opened(window: &tauri::WebviewWindow) {
    use windows::Win32::{
        Foundation::HWND,
        Graphics::Dwm::{DwmSetWindowAttribute, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND},
    };
    let Ok(raw) = window.hwnd() else {
        return;
    };
    let hwnd = HWND(raw.0);
    let corner = DWMWCP_ROUND.0 as u32;
    unsafe {
        let _ = DwmSetWindowAttribute(
            hwnd,
            DWMWA_WINDOW_CORNER_PREFERENCE,
            (&raw const corner).cast(),
            size_of::<u32>() as u32,
        );
    }
    let light = crate::appearance::resolved_light(window.app_handle(), window);
    set_window_border(window, light);
}

pub(crate) fn windows_changed(_app: &tauri::AppHandle) {}

/// No Dock: Settings opens from the tray menu.
pub(crate) fn reopened(_event: &tauri::RunEvent) -> bool {
    false
}

/// The screen under the pointer, both in physical pixels here.
pub(crate) fn pointer_monitor(app: &tauri::AppHandle) -> Option<tauri::Monitor> {
    let pointer = app.cursor_position().ok()?;
    app.monitor_from_point(pointer.x, pointer.y).ok().flatten()
}

pub(crate) const CUSTOM_CHROME: bool = true;

pub(crate) fn toolbar_chrome<'a>(
    builder: tauri::WebviewWindowBuilder<'a, tauri::Wry, tauri::AppHandle>,
) -> tauri::WebviewWindowBuilder<'a, tauri::Wry, tauri::AppHandle> {
    builder.decorations(false).shadow(true)
}

pub(crate) fn system_languages() -> Vec<String> {
    use windows::Win32::Globalization::{GetUserDefaultUILanguage, LCIDToLocaleName};
    // The UI language's name, such as "zh-TW" or "ja-JP".
    let mut name = [0u16; 85];
    let length = unsafe { LCIDToLocaleName(GetUserDefaultUILanguage() as u32, Some(&mut name), 0) };
    if length <= 1 {
        return Vec::new();
    }
    vec![String::from_utf16_lossy(&name[..length as usize - 1])]
}

/// A message box from a process that has not started Tauri yet.
pub(crate) fn alert(title: &str, body: &str, _ok: &str) {
    use windows::{
        core::HSTRING,
        Win32::UI::WindowsAndMessaging::{MessageBoxW, MB_ICONINFORMATION, MB_OK},
    };
    unsafe {
        MessageBoxW(
            None,
            &HSTRING::from(body),
            &HSTRING::from(title),
            MB_OK | MB_ICONINFORMATION,
        );
    }
}

/// No shortcut for the clipboard window on this system yet.
pub(crate) const CLIP_SHORTCUT: bool = false;

pub(crate) fn clip_shortcut(_app: &tauri::AppHandle, _shortcut: Option<crate::inspect::Shortcut>) {}

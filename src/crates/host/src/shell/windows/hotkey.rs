//! Global Windows shortcut (`Ctrl+Alt+H`) to open Settings, matching macOS's `⌘⌥H`.
use std::sync::{
    atomic::{AtomicU32, Ordering},
    Arc,
};
use tauri::Manager;
use windows::{
    core::w,
    Win32::{
        Foundation::{HWND, LPARAM, LRESULT, WPARAM},
        System::{LibraryLoader::GetModuleHandleW, Threading::GetCurrentThreadId},
        UI::{
            Input::KeyboardAndMouse::{
                RegisterHotKey, UnregisterHotKey, MOD_ALT, MOD_CONTROL, MOD_NOREPEAT,
            },
            WindowsAndMessaging::{
                CreateWindowExW, DefWindowProcW, DestroyWindow, DispatchMessageW, GetMessageW,
                PostThreadMessageW, RegisterClassW, TranslateMessage, HWND_MESSAGE, MSG,
                WINDOW_EX_STYLE, WINDOW_STYLE, WM_HOTKEY, WM_QUIT, WNDCLASSW,
            },
        },
    },
};

const HOTKEY_ID: i32 = 1;

struct HotkeyGuard(Arc<AtomicU32>);

impl Drop for HotkeyGuard {
    fn drop(&mut self) {
        let thread = self.0.load(Ordering::Acquire);
        if thread != 0 {
            unsafe {
                let _ = PostThreadMessageW(thread, WM_QUIT, WPARAM(0), LPARAM(0));
            }
        }
    }
}

pub(super) fn install(app: &tauri::AppHandle) {
    let thread_id = Arc::new(AtomicU32::new(0));
    app.manage(HotkeyGuard(Arc::clone(&thread_id)));
    let handle = app.clone();
    let _ = std::thread::Builder::new()
        .name("agenthud-win-hotkey".into())
        .spawn(move || run(handle, thread_id));
}

unsafe extern "system" fn wnd_proc(
    hwnd: HWND,
    msg: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    unsafe { DefWindowProcW(hwnd, msg, wparam, lparam) }
}

fn run(app: tauri::AppHandle, thread_slot: Arc<AtomicU32>) {
    unsafe {
        let Ok(module) = GetModuleHandleW(None) else {
            return;
        };
        let class = WNDCLASSW {
            hInstance: module.into(),
            lpszClassName: w!("AgentHudGlobalHotkey"),
            lpfnWndProc: Some(wnd_proc),
            ..Default::default()
        };
        let _ = RegisterClassW(&class);
        let Ok(hwnd) = CreateWindowExW(
            WINDOW_EX_STYLE(0),
            w!("AgentHudGlobalHotkey"),
            w!("Agent HUD hotkey"),
            WINDOW_STYLE(0),
            0,
            0,
            0,
            0,
            Some(HWND_MESSAGE),
            None,
            Some(module.into()),
            None,
        ) else {
            return;
        };
        if RegisterHotKey(
            Some(hwnd),
            HOTKEY_ID,
            MOD_CONTROL | MOD_ALT | MOD_NOREPEAT,
            u32::from(b'H'),
        )
        .is_err()
        {
            let _ = DestroyWindow(hwnd);
            return;
        }
        thread_slot.store(GetCurrentThreadId(), Ordering::Release);
        let mut msg = MSG::default();
        while GetMessageW(&mut msg, None, 0, 0).as_bool() {
            if msg.message == WM_HOTKEY && msg.wParam.0 == HOTKEY_ID as usize {
                crate::auxiliary::show(&app);
            }
            let _ = TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        let _ = UnregisterHotKey(Some(hwnd), HOTKEY_ID);
        let _ = DestroyWindow(hwnd);
    }
}

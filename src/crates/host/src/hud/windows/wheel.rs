//! Inactive-window wheel forwarding for Windows: while `MouseWheelRouting = 0`,
//! Windows sends wheel messages only to the foreground window, so scrolling over
//! an unfocused HUD overlay uses `WH_MOUSE_LL` inside active HUD regions.
use std::{
    collections::HashMap,
    sync::{
        atomic::{AtomicBool, AtomicU32, Ordering},
        mpsc::{self, SyncSender},
        Arc, Mutex, MutexGuard, OnceLock, PoisonError,
    },
    time::Duration,
};
use tauri::Emitter;
use windows::Win32::{
    Foundation::{HINSTANCE, HWND, LPARAM, LRESULT, WPARAM},
    System::{LibraryLoader::GetModuleHandleW, Threading::GetCurrentThreadId},
    UI::{Input::KeyboardAndMouse::GetAsyncKeyState, WindowsAndMessaging as wm},
};

use super::super::{global_units_per_point, HudGeometry, InteractiveRegion};

const WM_SYNC_WHEEL: u32 = wm::WM_APP + 1;
const VK_SHIFT: i32 = 0x10;
const VK_CONTROL: i32 = 0x11;

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
struct HudWheelEvent {
    screen_id: String,
    x: f64,
    y: f64,
    delta_x: f64,
    delta_y: f64,
}

struct WheelTarget {
    app: tauri::AppHandle,
    screen_id: String,
    hwnd: isize,
    origin: [f64; 3], // [canvas_x, canvas_y, scale]
    regions: Vec<InteractiveRegion>,
}

impl WheelTarget {
    fn contains(&self, x: f64, y: f64) -> bool {
        self.regions
            .iter()
            .any(|r| x >= r.x && y >= r.y && x < r.x + r.width && y < r.y + r.height)
    }

    fn matches(&self, hwnd: isize, origin: [f64; 3], regions: &[InteractiveRegion]) -> bool {
        self.hwnd == hwnd
            && self.origin == origin
            && self.regions.len() == regions.len()
            && self.regions.iter().zip(regions).all(|(a, b)| {
                a.x == b.x && a.y == b.y && a.width == b.width && a.height == b.height
            })
    }
}

struct WheelController {
    targets: Mutex<HashMap<String, WheelTarget>>,
    armed: AtomicBool,
    thread_id: AtomicU32,
    sender: SyncSender<(tauri::AppHandle, String, HudWheelEvent)>,
}

static WHEEL: OnceLock<Arc<WheelController>> = OnceLock::new();

fn lock_targets(state: &WheelController) -> MutexGuard<'_, HashMap<String, WheelTarget>> {
    state.targets.lock().unwrap_or_else(PoisonError::into_inner)
}

fn controller() -> &'static Arc<WheelController> {
    WHEEL.get_or_init(|| {
        let (sender, receiver) =
            mpsc::sync_channel::<(tauri::AppHandle, String, HudWheelEvent)>(64);
        let (ready_tx, ready_rx) = mpsc::sync_channel::<()>(1);
        let state = Arc::new(WheelController {
            targets: Mutex::new(HashMap::new()),
            armed: AtomicBool::new(false),
            thread_id: AtomicU32::new(0),
            sender,
        });
        let _ = std::thread::Builder::new()
            .name("agenthud-hud-wheel-emit".into())
            .spawn(move || {
                while let Ok((app, label, event)) = receiver.recv() {
                    if let Err(error) = app.emit_to(&label, "hud:wheel", &event) {
                        eprintln!("Agent HUD wheel event: {error}");
                    }
                }
            });
        let hook_state = Arc::clone(&state);
        let _ = std::thread::Builder::new()
            .name("agenthud-hud-wheel".into())
            .spawn(move || run_hook_loop(hook_state, ready_tx));
        let _ = ready_rx.recv_timeout(Duration::from_millis(250));
        state
    })
}

fn post_thread(state: &WheelController, msg: u32) {
    let tid = state.thread_id.load(Ordering::Acquire);
    if tid != 0 {
        unsafe {
            let _ = wm::PostThreadMessageW(tid, msg, WPARAM(0), LPARAM(0));
        }
    }
}

unsafe fn sync_hook(hook: &mut Option<wm::HHOOK>, armed: bool) {
    if armed && hook.is_none() {
        if let Ok(module) = unsafe { GetModuleHandleW(None) } {
            *hook = unsafe {
                wm::SetWindowsHookExW(
                    wm::WH_MOUSE_LL,
                    Some(low_level_mouse_proc),
                    Some(HINSTANCE::from(module)),
                    0,
                )
            }
            .ok();
        }
    } else if !armed {
        if let Some(installed) = hook.take() {
            let _ = unsafe { wm::UnhookWindowsHookEx(installed) };
        }
    }
}

fn run_hook_loop(state: Arc<WheelController>, ready_tx: SyncSender<()>) {
    let mut hook: Option<wm::HHOOK> = None;
    unsafe {
        let mut msg = wm::MSG::default();
        // Force message-queue creation before exposing `thread_id`.
        let _ = wm::PeekMessageW(&mut msg, None, 0, 0, wm::PM_NOREMOVE);
        state
            .thread_id
            .store(GetCurrentThreadId(), Ordering::Release);
        let _ = ready_tx.send(());
        sync_hook(&mut hook, state.armed.load(Ordering::Acquire));
        while wm::GetMessageW(&mut msg, None, 0, 0).0 > 0 {
            if msg.message == WM_SYNC_WHEEL {
                sync_hook(&mut hook, state.armed.load(Ordering::Acquire));
            }
            let _ = wm::TranslateMessage(&msg);
            wm::DispatchMessageW(&msg);
        }
        sync_hook(&mut hook, false);
    }
}

pub(super) fn wheel_deltas(
    horizontal: bool,
    raw_delta: i16,
    scroll_lines: u32,
    shift_down: bool,
) -> (f64, f64) {
    let lines = match scroll_lines {
        0 => 3.0,
        u32::MAX => 9.0,
        n => f64::from(n.min(20)),
    };
    let px = (f64::from(raw_delta) / 120.0) * (lines * (100.0 / 3.0));
    if horizontal {
        (px, 0.0)
    } else if shift_down {
        (-px, 0.0)
    } else {
        (0.0, -px)
    }
}

fn scroll_lines() -> u32 {
    let mut lines: u32 = 3;
    unsafe {
        let _ = wm::SystemParametersInfoW(
            wm::SPI_GETWHEELSCROLLLINES,
            0,
            Some((&raw mut lines).cast()),
            wm::SYSTEM_PARAMETERS_INFO_UPDATE_FLAGS(0),
        );
    }
    lines
}

fn key_down(vk: i32) -> bool {
    unsafe { GetAsyncKeyState(vk) < 0 }
}

fn is_foreground(hwnd: HWND) -> bool {
    unsafe {
        let fg = wm::GetForegroundWindow();
        !fg.is_invalid()
            && (fg == hwnd
                || wm::IsChild(hwnd, fg).as_bool()
                || wm::GetAncestor(fg, wm::GA_ROOT) == hwnd)
    }
}

fn try_consume_wheel(msg: u32, info: &wm::MSLLHOOKSTRUCT) -> Option<()> {
    let raw_delta = (info.mouseData >> 16) as i16;
    if raw_delta == 0 {
        return None;
    }
    let state = WHEEL.get()?;
    let targets = state.targets.try_lock().ok()?;
    let (app, label, screen_id, x, y) = targets.iter().find_map(|(label, target)| {
        let hwnd = HWND(target.hwnd as *mut core::ffi::c_void);
        if !unsafe { wm::IsWindowVisible(hwnd).as_bool() } {
            return None;
        }
        let [cx, cy, scale] = target.origin;
        let x = f64::from(info.pt.x) / scale - cx;
        let y = f64::from(info.pt.y) / scale - cy;
        (target.contains(x, y) && !is_foreground(hwnd)).then(|| {
            (
                target.app.clone(),
                label.clone(),
                target.screen_id.clone(),
                x,
                y,
            )
        })
    })?;
    drop(targets);
    if key_down(VK_CONTROL) {
        return Some(());
    }
    let (delta_x, delta_y) = wheel_deltas(
        msg == wm::WM_MOUSEHWHEEL,
        raw_delta,
        scroll_lines(),
        key_down(VK_SHIFT),
    );
    let event = HudWheelEvent {
        screen_id,
        x,
        y,
        delta_x,
        delta_y,
    };
    let _ = state.sender.try_send((app, label, event));
    Some(())
}

unsafe extern "system" fn low_level_mouse_proc(
    code: i32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    let msg = wparam.0 as u32;
    if code >= 0 && (msg == wm::WM_MOUSEWHEEL || msg == wm::WM_MOUSEHWHEEL) {
        let info = unsafe { &*(lparam.0 as *const wm::MSLLHOOKSTRUCT) };
        if try_consume_wheel(msg, info).is_some() {
            return LRESULT(1);
        }
    }
    unsafe { wm::CallNextHookEx(None, code, wparam, lparam) }
}

fn target_origin(
    window: &tauri::WebviewWindow,
    geometry: Option<&HudGeometry>,
) -> Option<[f64; 3]> {
    if let Some(geometry) = geometry {
        let scale = global_units_per_point(geometry.scale_factor);
        return Some([geometry.canvas.x, geometry.canvas.y, scale]);
    }
    let pos = window.inner_position().ok()?;
    let scale = window.scale_factor().ok()?;
    Some([f64::from(pos.x) / scale, f64::from(pos.y) / scale, scale])
}

pub(in crate::hud) fn sync_wheel_capture(
    app: &tauri::AppHandle,
    window: &tauri::WebviewWindow,
    screen_id: &str,
    active: bool,
    regions: &[InteractiveRegion],
    geometry: Option<&HudGeometry>,
) {
    let label = window.label();
    if !active || regions.is_empty() {
        if let Some(state) = WHEEL.get() {
            let empty = {
                let mut targets = lock_targets(state);
                targets.remove(label);
                targets.is_empty()
            };
            if empty && state.armed.swap(false, Ordering::AcqRel) {
                post_thread(state, WM_SYNC_WHEEL);
            }
        }
        return;
    }
    let (Ok(raw_hwnd), Some(origin)) = (window.hwnd(), target_origin(window, geometry)) else {
        return;
    };
    let hwnd = raw_hwnd.0 as isize;
    let state = controller();
    {
        let mut targets = lock_targets(state);
        if !targets
            .get(label)
            .is_some_and(|t| t.matches(hwnd, origin, regions))
        {
            targets.insert(
                label.to_owned(),
                WheelTarget {
                    app: app.clone(),
                    screen_id: screen_id.to_owned(),
                    hwnd,
                    origin,
                    regions: regions.to_vec(),
                },
            );
        }
    }
    if !state.armed.swap(true, Ordering::AcqRel) {
        post_thread(state, WM_SYNC_WHEEL);
    }
}

pub(in crate::hud) fn shutdown_wheel_capture() {
    if let Some(state) = WHEEL.get() {
        lock_targets(state).clear();
        state.armed.store(false, Ordering::Release);
        post_thread(state, wm::WM_QUIT);
    }
}

#[cfg(test)]
mod tests {
    use super::wheel_deltas;

    #[test]
    fn wheel_deltas_handles_vertical_up_down_and_fractional_touchpad() {
        assert_eq!(wheel_deltas(false, -120, 3, false), (0.0, 100.0));
        assert_eq!(wheel_deltas(false, 120, 3, false), (0.0, -100.0));
        assert_eq!(wheel_deltas(false, -60, 3, false), (0.0, 50.0));
    }

    #[test]
    fn wheel_deltas_handles_horizontal_shift_and_scroll_lines_clamping() {
        assert_eq!(wheel_deltas(true, 120, 3, false), (100.0, 0.0));
        assert_eq!(wheel_deltas(false, -120, 3, true), (100.0, 0.0));
        assert_eq!(wheel_deltas(false, 120, 3, true), (-100.0, 0.0));
        assert_eq!(wheel_deltas(false, -120, 0, false), (0.0, 100.0));
        assert_eq!(wheel_deltas(false, -120, u32::MAX, false), (0.0, 300.0));
        assert!((wheel_deltas(false, -120, 50, false).1 - (20.0 * (100.0 / 3.0))).abs() < 1e-6);
    }
}

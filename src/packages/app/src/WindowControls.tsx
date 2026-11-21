import { useEffect, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import { Copy, Minus, Square, X } from "lucide-react";

/** True when the host removed OS window decorations in favor of custom web controls. */
export function useCustomFrame(): boolean {
  return Boolean(
    isTauri() &&
    typeof document !== "undefined" &&
    document.documentElement.dataset.customFrame === "true",
  );
}

/** Toggles maximize/restore for the active frameless Tauri window. */
export function toggleWindowMaximize(): void {
  if (!isTauri()) return;
  void getCurrentWebviewWindow()
    .toggleMaximize()
    .catch(() => {});
}

/** Sleek minimize / maximize / close buttons for frameless desktop windows. */
export function WindowControls() {
  const custom = useCustomFrame();
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    if (!custom) return;
    let active = true;
    const win = getCurrentWebviewWindow();
    const sync = () => {
      void win
        .isMaximized()
        .then((next) => {
          if (active) setMaximized(next);
        })
        .catch(() => {});
    };
    sync();
    const listen = win.onResized(sync);
    return () => {
      active = false;
      void listen.then((unlisten) => unlisten()).catch(() => {});
    };
  }, [custom]);

  if (!custom) return null;

  const win = getCurrentWebviewWindow();

  return (
    <div
      className="window-controls"
      role="group"
      aria-label="Window controls"
      onDoubleClick={(event) => event.stopPropagation()}
    >
      <button
        type="button"
        className="window-control-btn"
        aria-label="Minimize"
        onClick={() => void win.minimize()}
      >
        <Minus size={13} strokeWidth={1.7} />
      </button>
      <button
        type="button"
        className="window-control-btn"
        aria-label={maximized ? "Restore" : "Maximize"}
        onClick={() =>
          void win
            .toggleMaximize()
            .then(() => win.isMaximized())
            .then(setMaximized)
            .catch(() => {})
        }
      >
        {maximized ? (
          <Copy size={11} strokeWidth={1.7} />
        ) : (
          <Square size={11} strokeWidth={1.7} />
        )}
      </button>
      <button
        type="button"
        className="window-control-btn window-control-close"
        aria-label="Close"
        onClick={() => void win.close()}
      >
        <X size={13.5} strokeWidth={1.75} />
      </button>
    </div>
  );
}

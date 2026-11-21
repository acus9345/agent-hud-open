import { useEffect, useState, type ReactNode } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import { ClockProvider } from "@agent-hud/ui/format-context";

/** Owns the live `hud:clock` subscription so periodic clock ticks never re-render AppContent. */
export function HUDClockProvider({
  active,
  children,
}: {
  active: boolean;
  children: ReactNode;
}) {
  const [clock, setClock] = useState<number | null>(null);
  useEffect(() => {
    if (!active || !isTauri()) return;
    let disposed = false;
    const subscription = listen<string>(
      "hud:clock",
      ({ payload }) => {
        if (disposed) return;
        const parsed = Date.parse(payload);
        if (Number.isFinite(parsed)) {
          setClock((previous) => (previous === parsed ? previous : parsed));
        }
      },
      {
        target: {
          kind: "WebviewWindow",
          label: getCurrentWebviewWindow().label,
        },
      },
    );
    return () => {
      disposed = true;
      void subscription.then((unlisten) => unlisten());
    };
  }, [active]);
  return <ClockProvider now={clock}>{children}</ClockProvider>;
}

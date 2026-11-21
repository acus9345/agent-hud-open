import {
  lazy,
  Suspense,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import { getCurrentWebviewWindow } from "@tauri-apps/api/webviewWindow";
import { Toaster } from "sonner";
import { ScrollTab, SpringPill } from "@agent-hud/ui/MotionPrimitives";
import { Tabs, TabsList, TabsTrigger } from "@agent-hud/ui/components/tabs";
import { TooltipProvider } from "@agent-hud/ui/components/tooltip";
import type { NativeMethod, Session, Snapshot } from "@agent-hud/protocol";
import { useFormat, useT } from "@agent-hud/ui/format-context";
import { LoadingState } from "./LoadingState";
import { ViewErrorBoundary } from "./ViewErrorBoundary";
import { themeFor } from "./statistics/StatsTheme";
import { PagePlaceholder } from "./statistics/dashboard/Placeholder";
import {
  toggleWindowMaximize,
  useCustomFrame,
  WindowControls,
} from "./WindowControls";

// Statistics does not need the shader/bot renderer, nor the settings panes.
const Statistics = lazy(() => import("./Statistics"));
const SessionStatistics = lazy(() => import("./SessionStatistics"));

/** The Statistics window: its Tokens and Sessions tabs, and the tab or
 * session the host asks it to show. */
export function StatisticsWindow({
  snapshot,
  theme,
  act,
  windowActive,
  demo,
  error,
  refresh,
  sourceMenu,
}: {
  snapshot: Snapshot | null;
  theme: "dark" | "light";
  act: (
    method: NativeMethod,
    params?: Record<string, unknown>,
  ) => Promise<unknown>;
  windowActive: boolean;
  demo: boolean;
  error: string | null;
  refresh: () => Promise<unknown>;
  sourceMenu: ReactNode;
}) {
  const t = useT();
  const { language } = useFormat();
  const [statisticsTab, setStatisticsTab] = useState("tokens");
  const [selectedSession, setSelectedSession] = useState<Session | null>(null);
  useEffect(() => {
    setSelectedSession(null);
  }, [demo]);
  useEffect(() => {
    if (!isTauri()) return;
    const subscription = getCurrentWebviewWindow().listen<string>(
      "statistics:tab",
      (event) => setStatisticsTab(event.payload),
    );
    return () => {
      void subscription.then((unlisten) => unlisten());
    };
  }, []);
  const sessionsRef = useRef<Session[]>([]);
  sessionsRef.current = snapshot?.sessions ?? [];
  useEffect(() => {
    if (!isTauri()) return;
    const applySession = (id: string) => {
      const session = sessionsRef.current.find((s) => s.id === id);
      setSelectedSession(session ?? ({ id } as Session));
      setStatisticsTab("sessions");
    };
    const pendingSession = getCurrentWebviewWindow().listen<string>(
      "statistics:session",
      (event) => {
        applySession(event.payload);
      },
    );
    const pendingProvider = getCurrentWebviewWindow().listen<{
      provider: string;
      at: number;
    }>("statistics:provider", () => {
      setSelectedSession(null);
      setStatisticsTab("tokens");
    });
    void invoke<{ tab?: string | null; sessionId?: string | null } | null>(
      "take_statistics_target",
    ).then((target) => {
      if (!target) return;
      if (target.sessionId) applySession(target.sessionId);
      else if (target.tab) setStatisticsTab(target.tab);
    });
    return () => {
      void pendingSession.then((unlisten) => unlisten());
      void pendingProvider.then((unlisten) => unlisten());
    };
  }, []);
  // Before the page has anything to draw, its shape in grey.
  const placeholder = (
    <PagePlaceholder theme={themeFor(theme === "light", language)} />
  );
  const customFrame = useCustomFrame();
  return (
    <TooltipProvider>
      <div
        className="statistics-window"
        data-theme={theme}
        data-custom-frame={customFrame ? "true" : undefined}
      >
        <header
          className="statistics-toolbar"
          data-tauri-drag-region
          onDoubleClick={
            customFrame
              ? (event) => {
                  if (
                    event.target === event.currentTarget ||
                    (event.target as HTMLElement).classList.contains(
                      "statistics-toolbar-spacer",
                    )
                  ) {
                    toggleWindowMaximize();
                  }
                }
              : undefined
          }
        >
          <span className="statistics-toolbar-spacer" data-tauri-drag-region />
          <Tabs value={statisticsTab} onValueChange={setStatisticsTab}>
            <TabsList>
              <TabsTrigger
                value="tokens"
                style={{
                  position: "relative",
                  background: "transparent",
                  boxShadow: "none",
                }}
              >
                {statisticsTab === "tokens" && (
                  <SpringPill
                    layoutId="statistics-window-tab"
                    style={{
                      borderRadius: 5,
                      background: "var(--segment-selected)",
                      boxShadow: "0 1px 2px #0002",
                    }}
                  />
                )}
                <span style={{ position: "relative", zIndex: 1 }}>
                  {t("Usage")}
                </span>
              </TabsTrigger>
              <TabsTrigger
                value="sessions"
                style={{
                  position: "relative",
                  background: "transparent",
                  boxShadow: "none",
                }}
              >
                {statisticsTab === "sessions" && (
                  <SpringPill
                    layoutId="statistics-window-tab"
                    style={{
                      borderRadius: 5,
                      background: "var(--segment-selected)",
                      boxShadow: "0 1px 2px #0002",
                    }}
                  />
                )}
                <span style={{ position: "relative", zIndex: 1 }}>
                  {t("Sessions")}
                </span>
              </TabsTrigger>
            </TabsList>
          </Tabs>
          <div className="statistics-toolbar-actions">
            {sourceMenu}
            {customFrame && (
              <span className="window-controls-divider" aria-hidden />
            )}
            <WindowControls />
          </div>
        </header>
        {snapshot ? (
          <ViewErrorBoundary onReset={() => setSelectedSession(null)}>
            <Suspense
              fallback={statisticsTab === "tokens" ? placeholder : null}
            >
              <ScrollTab active={statisticsTab === "tokens"}>
                <Statistics
                  snapshot={snapshot}
                  light={theme === "light"}
                  action={act}
                  active={windowActive && statisticsTab === "tokens"}
                  onSelectSession={(session) => {
                    setSelectedSession(session);
                    setStatisticsTab("sessions");
                  }}
                />
              </ScrollTab>
              <ScrollTab active={statisticsTab === "sessions"}>
                <SessionStatistics
                  snapshot={snapshot}
                  light={theme === "light"}
                  action={act}
                  active={windowActive && statisticsTab === "sessions"}
                  initialSessionId={selectedSession?.id ?? null}
                  onClearSelection={() => setSelectedSession(null)}
                />
              </ScrollTab>
            </Suspense>
          </ViewErrorBoundary>
        ) : !error && statisticsTab === "tokens" ? (
          placeholder
        ) : (
          <LoadingState
            label={
              error
                ? t("Couldn’t read local activity")
                : t("Reading local activity…")
            }
            failed={!!error}
            detail={error}
            retryLabel={t("Retry")}
            onRetry={() => void refresh()}
          />
        )}
        <Toaster theme={theme} />
      </div>
    </TooltipProvider>
  );
}

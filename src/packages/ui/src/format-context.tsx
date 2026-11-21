// The interface language and calendar every formatter takes, provided once at
// the root of each window from the app's language setting.
import { createContext, use, useContext, useMemo, type ReactNode } from "react";
import { catalogReady, loadCatalog } from "./catalogs.ts";
import {
  interfaceLanguage,
  systemCalendar,
  translator,
  type FormatSettings,
  type Translate,
} from "./format";

const systemLocale = () =>
  typeof navigator === "undefined" ? "en-US" : navigator.language;

const FormatContext = createContext<FormatSettings>({
  language: interfaceLanguage("system"),
  calendar: systemCalendar(systemLocale()),
});

/** Provides the language for `setting` ("system", "en", "ja", "zh-Hant" …)
 * and this machine's calendar. Suspends while the language's catalog loads. */
export function FormatProvider({
  setting,
  quotaDisplay = "consumed",
  children,
}: {
  setting: string | null | undefined;
  quotaDisplay?: FormatSettings["quotaDisplay"];
  children: ReactNode;
}) {
  const language = interfaceLanguage(setting);
  // A preloaded promise has not been observed by React yet and would suspend
  // once more. `use` may be conditional; skip it only after every catalog is in.
  if (!catalogReady(language)) use(loadCatalog(language));
  const value = useMemo<FormatSettings>(
    () => ({
      language,
      quotaDisplay,
      calendar: systemCalendar(systemLocale()),
    }),
    [language, quotaDisplay],
  );
  return (
    <FormatContext.Provider value={value}>{children}</FormatContext.Provider>
  );
}

export function useFormat(): FormatSettings {
  return useContext(FormatContext);
}

const ClockContext = createContext<number | null>(null);

/** Independent clock timestamp provider so periodic ticks never clone `Snapshot`. */
export function ClockProvider({
  now,
  children,
}: {
  now: number | null | undefined;
  children: ReactNode;
}) {
  const value = now != null && Number.isFinite(now) ? now : null;
  return (
    <ClockContext.Provider value={value}>{children}</ClockContext.Provider>
  );
}

/** Current live clock timestamp in ms, falling back to `fallbackMs` outside a clock feed. */
export function useClockNow(fallbackMs: number): number {
  const clock = useContext(ClockContext);
  return clock != null && Number.isFinite(clock) ? clock : fallbackMs;
}

/** The text of an English key in the interface language:
 * `t("Retry")`, `t("{{count}} hidden", { count })`. */
export function useT(): Translate {
  const { language } = useContext(FormatContext);
  return useMemo(() => translator(language), [language]);
}

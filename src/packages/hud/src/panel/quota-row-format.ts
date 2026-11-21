import type { Quota } from "@agent-hud/protocol";
import {
  dateFormat,
  isChinese,
  locale,
  translate,
  type FormatSettings,
} from "@agent-hud/ui/format";

const MONTHS = [
  "Jan",
  "Feb",
  "Mar",
  "Apr",
  "May",
  "Jun",
  "Jul",
  "Aug",
  "Sep",
  "Oct",
  "Nov",
  "Dec",
] as const;

const two = (value: number) => String(value).padStart(2, "0");

/** Shortens verbose provider quota suffixes and strips repeated group prefixes. */
export function quotaDisplayName(name: string): string {
  const parts = name.split(" · ");
  const deduped =
    parts.length === 2 &&
    parts[1].toLowerCase().startsWith(`${parts[0].toLowerCase()} `)
      ? `${parts[0]} · ${parts[1].slice(parts[0].length + 1)}`
      : name;
  return deduped
    .replace(/\bWeekly Limit(?:\s+Remaining)?\b/i, "Weekly limit")
    .replace(/\bFive Hour Limit(?:\s+Remaining)?\b/i, "5-hour limit")
    .replace(/\bLimit Remaining\b/i, "limit");
}

function wallClock(instant: number, timeZone: string) {
  const parts = Object.fromEntries(
    dateFormat("en-US", {
      timeZone,
      year: "numeric",
      month: "numeric",
      day: "numeric",
      hour: "numeric",
      minute: "numeric",
      weekday: "short",
      hourCycle: "h23",
    })
      .formatToParts(instant)
      .map((part) => [part.type, part.value]),
  );
  const year = Number(parts.year);
  const month = Number(parts.month);
  const day = Number(parts.day);
  return {
    month,
    day,
    hour: Number(parts.hour) % 24,
    minute: Number(parts.minute),
    weekday: parts.weekday,
    dayNumber: Math.floor(Date.UTC(year, month - 1, day) / 86_400_000),
  };
}

/** Formats a quota reset instant as wall-clock time ("Resets at 4:00 PM"). */
export function formatBarReset(
  row: Pick<Quota, "resetAt">,
  now: number,
  settings: FormatSettings,
): string {
  const resetAt = row.resetAt ? Date.parse(row.resetAt) : NaN;
  if (!Number.isFinite(resetAt)) return "";
  if (resetAt <= now) return translate(settings.language, "Pending update");

  const { timeZone } = settings.calendar;
  const resetClock = wallClock(resetAt, timeZone);
  const nowClock = wallClock(now, timeZone);
  const deltaMs = resetAt - now;
  const dayDelta = resetClock.dayNumber - nowClock.dayNumber;

  if (isChinese(settings.language)) {
    const hhmm = `${two(resetClock.hour)}:${two(resetClock.minute)}`;
    if (deltaMs < 86_400_000) return `${hhmm} 重置`; // l10n-ignore
    if (dayDelta < 7) {
      const weekday = dateFormat(locale(settings.language), {
        weekday: "short",
        timeZone,
      }).format(resetAt);
      return `${weekday} ${hhmm} 重置`; // l10n-ignore
    }
    return `${resetClock.month}月${resetClock.day}日 ${hhmm} 重置`; // l10n-ignore
  }

  const period = resetClock.hour >= 12 ? "PM" : "AM";
  const h12 = resetClock.hour % 12 || 12;
  const time12 = `${h12}:${two(resetClock.minute)} ${period}`;
  if (deltaMs < 86_400_000) return `Resets at ${time12}`;
  if (dayDelta < 7) return `Resets ${resetClock.weekday} ${time12}`;
  const monthName = MONTHS[resetClock.month - 1];
  return `Resets ${monthName} ${resetClock.day}, ${time12}`;
}

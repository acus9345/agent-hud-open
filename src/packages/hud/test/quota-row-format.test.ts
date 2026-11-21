import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { addCatalog, cleanDeviceCopy } from "@agent-hud/ui/format";
import {
  formatBarReset,
  quotaDisplayName,
} from "../src/panel/quota-row-format.ts";
import {
  BAR_CRITICAL_FILL,
  BAR_OK_FILL,
  barLevelFill,
  barValueText,
  mutedMeterColor,
} from "../src/tokens.ts";

const locales = join(import.meta.dirname, "../../protocol/locales");
for (const language of ["en", "zh-Hans"] as const) {
  addCatalog(
    language,
    JSON.parse(readFileSync(join(locales, `${language}.json`), "utf8")),
  );
}

const en = {
  language: "en" as const,
  calendar: { timeZone: "UTC", firstWeekday: 1 },
};
const zh = {
  language: "zh-Hans" as const,
  calendar: { timeZone: "UTC", firstWeekday: 2 },
};

test("quotaDisplayName shortens verbose provider quota suffixes", () => {
  assert.equal(
    quotaDisplayName("Gemini Models · Weekly Limit Remaining"),
    "Gemini Models · Weekly limit",
  );
  assert.equal(
    quotaDisplayName("3rd-party Models · Weekly Limit Remaining"),
    "3rd-party Models · Weekly limit",
  );
  assert.equal(
    quotaDisplayName("Gemini Models · Five Hour Limit Remaining"),
    "Gemini Models · 5-hour limit",
  );
  assert.equal(
    quotaDisplayName("Gemini 4 Argon · Gemini 4 Argon Weekly Limit"),
    "Gemini 4 Argon · Weekly limit",
  );
  assert.equal(quotaDisplayName("Weekly Limit"), "Weekly limit");
  assert.equal(quotaDisplayName("Weekly Limit Remaining"), "Weekly limit");
  assert.equal(quotaDisplayName("Session"), "Session");
});

test("formatBarReset formats clock reset times cleanly across ranges and locales", () => {
  const nowMorning = Date.parse("2026-10-21T00:42:00Z"); // Wed ~15h 18m before 4:00 PM
  assert.equal(
    formatBarReset({ resetAt: "2026-10-21T16:00:00Z" }, nowMorning, en),
    "Resets at 4:00 PM",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-21T16:00:00Z" }, nowMorning, zh),
    "16:00 重置",
  );

  const nowNoon = Date.parse("2026-10-21T12:00:00Z");
  assert.equal(
    formatBarReset({ resetAt: "2026-10-22T00:28:00Z" }, nowNoon, en),
    "Resets at 12:28 AM",
  );
  assert.equal(
    formatBarReset(
      { resetAt: "2026-10-21T12:00:00Z" },
      Date.parse("2026-10-21T06:00:00Z"),
      en,
    ),
    "Resets at 12:00 PM",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-22T23:26:00Z" }, nowNoon, en),
    "Resets Thu 11:26 PM",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-22T23:26:00Z" }, nowNoon, zh),
    "周四 23:26 重置",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-29T16:00:00Z" }, nowNoon, en),
    "Resets Oct 29, 4:00 PM",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-29T16:00:00Z" }, nowNoon, zh),
    "10月29日 16:00 重置",
  );

  assert.equal(
    formatBarReset({ resetAt: "2026-10-21T11:00:00Z" }, nowNoon, en),
    "Pending update",
  );
  assert.equal(
    formatBarReset({ resetAt: "2026-10-21T11:00:00Z" }, nowNoon, zh),
    "等待更新",
  );
  assert.equal(formatBarReset({ resetAt: null }, nowNoon, en), "");
});

test("barLevelFill and barValueText use muted sage/terracotta fills and calm grey values", () => {
  assert.equal(BAR_OK_FILL, "#5b7a45");
  assert.equal(BAR_CRITICAL_FILL, "#c34b44");
  assert.equal(barLevelFill("ok", { light: false }), "#5b7a45");
  assert.equal(barLevelFill("ok", { light: true }), "#5b7a45");
  assert.equal(barLevelFill("warning", { light: false }), "#5b7a45");
  assert.equal(barLevelFill("warning", { light: true }), "#5b7a45");
  assert.equal(barLevelFill("critical", { light: false }), "#c34b44");
  assert.equal(barLevelFill("critical", { light: true }), "#c34b44");
  assert.equal(barValueText({ light: false }), "#a6a6a6");
  assert.equal(barValueText({ light: true }), "#5c5c60");
  assert.equal(mutedMeterColor("#3ddc84"), "#5b7a45");
  assert.equal(mutedMeterColor("#ffd23f"), "#5b7a45");
  assert.equal(mutedMeterColor("#ff453a"), "#c34b44");
  assert.equal(mutedMeterColor("#8e8e93"), "#8e8e93");
});

test("cleanDeviceCopy replaces macOS-specific copy cleanly", () => {
  assert.equal(
    cleanDeviceCopy("No usage was recorded on this Mac in the last 30 days"),
    "No usage was recorded on this device in the last 30 days",
  );
  assert.equal(
    cleanDeviceCopy(
      "This Mac's usage cannot be told apart between this provider's accounts",
    ),
    "This device's usage cannot be told apart between this provider's accounts",
  );
});

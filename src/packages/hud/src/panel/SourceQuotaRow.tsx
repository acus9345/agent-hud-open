import { useState } from "react";
import { motion, useReducedMotionConfig } from "motion/react";
import { BAR_SPRING, SNAP_SPRING } from "@agent-hud/ui/lib/motion";
import type { Quota } from "@agent-hud/protocol";
import { quotaDisplayLabel, quotaPercent } from "@agent-hud/ui/format";
import { useFormat } from "@agent-hud/ui/format-context";
import { quotaMetric } from "../quota-metrics";
import { quotaShortName } from "../model";
import { usePanelServices } from "../PanelContext";
import { useGround } from "../ground";
import {
  BAR_CRITICAL_FILL,
  barLevelFill,
  barValueText,
  type Level,
} from "../tokens";
import { FittingName } from "./FittingName";
import { formatBarReset, quotaDisplayName } from "./quota-row-format";
import { palette, tabular, type Measure } from "./theme";

/** One quota window as a bar row: its name, the bar with burn's projection and
 * budget mark, its value and its reset or detail. */
export function SourceQuotaRow({
  row,
  measure,
  now,
  light,
  showReset,
  onStats,
}: {
  row: Quota;
  measure: Measure;
  now: number;
  light: boolean;
  showReset: boolean;
  onStats?: () => void;
}) {
  const ground = useGround(light);
  const colors = palette(ground);
  const services = usePanelServices();
  const [hovered, setHovered] = useState(false);
  const clickable = Boolean(services?.selectProvider || onStats);
  const activate = () => {
    services?.selectProvider?.(row.vendor);
    onStats?.();
  };
  const format = useFormat();
  const reading = quotaMetric(row, measure, now, format);
  const pct = quotaPercent(row.usedPercent, format.quotaDisplay);
  const projection =
    measure === "burn" &&
    reading.projectedPercent != null &&
    reading.projectedPercent > (row.usedPercent ?? 0)
      ? quotaPercent(reading.projectedPercent, format.quotaDisplay)
      : null;
  const solidPercent =
    format.quotaDisplay === "remaining" && projection != null
      ? projection
      : pct;
  const budget = quotaPercent(reading.budgetPercent, format.quotaDisplay);
  const quotaLevel: Level =
    row.status === "critical" || row.status === "warning" ? row.status : "ok";
  const known = reading.available && row.status !== "stale";
  const still = Boolean(useReducedMotionConfig());
  const showsTrack = measure === "quota" || measure === "burn";
  const barRatio = Math.max(0, Math.min(1, (solidPercent ?? 0) / 100));
  const displayName = quotaDisplayName(row.name);
  const shortName = quotaDisplayName(quotaShortName(row));
  const detailText =
    measure === "quota" ? formatBarReset(row, now, format) : reading.detail;
  const showDetail = (showReset || measure !== "quota") && Boolean(detailText);
  const valueColorFinal = barValueText(ground);
  return (
    <motion.div
      className="hud-source-quota-row"
      title={`${displayName} · ${reading.hint}`}
      role={clickable ? "button" : undefined}
      tabIndex={clickable ? 0 : undefined}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={clickable ? activate : undefined}
      whileHover={clickable && !still ? { x: 2 } : undefined}
      whileTap={clickable && !still ? { scale: 0.99 } : undefined}
      transition={still ? { duration: 0 } : SNAP_SPRING}
      onKeyDown={
        clickable
          ? (e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                activate();
              }
            }
          : undefined
      }
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 6,
        padding: "var(--hud-row-padding, 5px) 6px",
        fontSize: 13,
        lineHeight: "17px",
        borderRadius: 8,
        cursor: clickable ? "pointer" : undefined,
        background: hovered && clickable ? colors.selected : "transparent",
        transition: "background 0.14s ease",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "baseline",
          justifyContent: "space-between",
          gap: 10,
          width: "100%",
          minWidth: 0,
        }}
      >
        <FittingName
          name={displayName}
          short={shortName}
          style={{
            flex: "1 1 auto",
            minWidth: 0,
            fontSize: 13,
            lineHeight: "17px",
            fontWeight: 500,
            color: colors.text,
          }}
        />
        <div
          style={{
            display: "flex",
            alignItems: "baseline",
            gap: 8,
            flexShrink: 0,
          }}
        >
          {showDetail && (
            <span
              style={{
                ...tabular,
                whiteSpace: "nowrap",
                fontSize: 12,
                lineHeight: "16px",
                fontWeight: 400,
                color:
                  measure === "burn" && reading.runsOut
                    ? BAR_CRITICAL_FILL
                    : colors.secondary,
              }}
            >
              {detailText}
            </span>
          )}
          <span
            style={{
              ...tabular,
              minWidth: 34,
              textAlign: "right",
              fontSize: reading.available ? 12.5 : 12,
              lineHeight: "16px",
              fontWeight: 500,
              whiteSpace: "nowrap",
              color: known ? valueColorFinal : colors.secondary,
            }}
          >
            {reading.value}
          </span>
        </div>
      </div>
      {showsTrack && (
        <span
          role="meter"
          aria-label={`${displayName} · ${quotaDisplayLabel(format.quotaDisplay, format.language)}`}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={pct ?? undefined}
          style={{
            position: "relative",
            display: "block",
            width: "100%",
            height: 5,
            background: colors.track,
            borderRadius: 999,
            overflow: "hidden",
          }}
        >
          {projection != null && pct != null && (
            <motion.i
              initial={still ? false : { opacity: 0, scaleX: 0 }}
              animate={{ opacity: 0.38, scaleX: 1 }}
              transition={still ? { duration: 0 } : BAR_SPRING}
              style={{
                position: "absolute",
                top: 0,
                left: `${Math.min(pct, projection)}%`,
                display: "block",
                height: 5,
                width: `${Math.abs(projection - pct)}%`,
                borderRadius: 999,
                transformOrigin: "left center",
                background: reading.runsOut
                  ? BAR_CRITICAL_FILL
                  : colors.secondary,
              }}
            />
          )}
          <motion.i
            initial={still ? false : { scaleX: 0 }}
            animate={{ scaleX: barRatio }}
            transition={still ? { duration: 0 } : BAR_SPRING}
            style={{
              position: "absolute",
              inset: "0 auto 0 0",
              display: "block",
              height: 5,
              width: "100%",
              transformOrigin: "left center",
              borderRadius: 999,
              background:
                row.status === "stale"
                  ? colors.secondary
                  : barLevelFill(quotaLevel, ground),
            }}
          />
          {measure === "burn" && budget != null && (
            // Where a steady pace through the cycle would have the bar now.
            <i
              aria-hidden
              style={{
                position: "absolute",
                top: 0,
                bottom: 0,
                left: `calc(${budget}% - 0.75px)`,
                display: "block",
                width: 1.5,
                background: colors.text,
                opacity: 0.6,
              }}
            />
          )}
        </span>
      )}
    </motion.div>
  );
}

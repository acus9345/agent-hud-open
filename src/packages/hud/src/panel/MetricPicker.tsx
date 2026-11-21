import { useId } from "react";
import { motion, useReducedMotionConfig } from "motion/react";
import { SNAP_SPRING } from "@agent-hud/ui/lib/motion";
import { SpringPill } from "@agent-hud/ui/MotionPrimitives";
import { Button } from "@agent-hud/ui/components/button";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@agent-hud/ui/components/tooltip";
import { useT } from "@agent-hud/ui/format-context";
import type { quotaMetricAvailability } from "../quota-metrics";
import { useGround } from "../ground";
import { measures, palette, type Measure } from "./theme";

/** A block's measure buttons: quota, burn rate, reply rate, consumed and cost,
 * each with why it is unavailable where it is. */
export function MetricPicker({
  selected,
  choose,
  light,
  availability,
}: {
  selected: Measure;
  choose: (measure: Measure) => void;
  light: boolean;
  availability: Record<Measure, ReturnType<typeof quotaMetricAvailability>>;
}) {
  const t = useT();
  const pickerId = useId();
  const still = Boolean(useReducedMotionConfig());
  const colors = palette(useGround(light));
  return (
    <TooltipProvider delayDuration={300}>
      <div
        className="hud-source-metric-picker"
        role="group"
        aria-label={t("Quota metric")}
        style={{ display: "flex", gap: 2, paddingRight: 2 }}
      >
        {measures.map(({ key, title, icon: Icon }) => {
          const status = availability[key];
          const hint = `${title} · ${status.hint}`;
          const active = selected === key;
          return (
            <Tooltip key={key}>
              <TooltipTrigger asChild>
                <motion.span
                  title={hint}
                  tabIndex={status.selectable ? undefined : 0}
                  aria-label={status.selectable ? undefined : hint}
                  whileHover={
                    status.selectable && !still ? { scale: 1.08 } : undefined
                  }
                  whileTap={
                    status.selectable && !still ? { scale: 0.92 } : undefined
                  }
                  transition={SNAP_SPRING}
                  style={{ display: "inline-flex" }}
                >
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    aria-label={title}
                    aria-pressed={active}
                    disabled={!status.selectable}
                    onClick={() => choose(key)}
                    style={{
                      position: "relative",
                      width: 20,
                      minWidth: 20,
                      height: 18,
                      minHeight: 18,
                      padding: 0,
                      borderRadius: 5,
                      color: active ? colors.text : colors.tertiary,
                      background: "transparent",
                      boxShadow: "none",
                    }}
                  >
                    {active && (
                      <SpringPill
                        layoutId={`metric-pill-${pickerId}`}
                        style={{
                          borderRadius: 5,
                          background: colors.selected,
                        }}
                      />
                    )}
                    <Icon
                      style={{
                        position: "relative",
                        zIndex: 1,
                        width: 10,
                        height: 10,
                      }}
                      strokeWidth={key === "burn" ? 2.6 : 2}
                      fill={key === "burn" ? "currentColor" : "none"}
                    />
                  </Button>
                </motion.span>
              </TooltipTrigger>
              <TooltipContent
                side="top"
                style={{ maxWidth: 300, whiteSpace: "pre-line" }}
              >
                {hint}
              </TooltipContent>
            </Tooltip>
          );
        })}
      </div>
    </TooltipProvider>
  );
}

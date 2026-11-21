import type { ReactNode } from "react";
import {
  DashboardSection,
  MotionCardButton,
  type SurfaceTier,
} from "@agent-hud/ui/DashboardMotion";
import { number, plain, surfaceStyle, type StatsTheme } from "../StatsTheme";
import { Decrypt } from "../decrypt";
import { TrendLine } from "./TrendLine";
import { Bar } from "./Placeholder";
import { usePendingLook, type Pending } from "./pending";

/** One tile: a label, the figure, one line of context and the figure's trend
 * across the range. Supports primary hero vs secondary vs plain surface tiers. */
export function Figure({
  label,
  loading = false,
  value,
  suffix,
  line,
  hint,
  series,
  active,
  onClick,
  pending,
  theme,
  variant = "secondary",
  index = 0,
}: {
  label: string;
  /** Nothing to show yet: grey bars stand in. */
  loading?: boolean;
  value: string;
  suffix?: string | null;
  line: ReactNode;
  hint?: string;
  series: (number | null)[];
  active?: boolean;
  onClick?: () => void;
  /** The answer it waits for when a filter changes. */
  pending?: Pending | null;
  theme: StatsTheme;
  variant?: SurfaceTier;
  index?: number;
}) {
  const look = usePendingLook(pending);
  const hero = variant === "primary";
  // One figure element throughout, so the digits decrypt into place when
  // the value arrives instead of the tile swapping a bar for text.
  const figure = (
    <span
      style={{
        ...number,
        fontSize: hero ? 30 : 22,
        lineHeight: hero ? "36px" : "28px",
        fontWeight: hero ? 700 : 600,
        letterSpacing: hero ? -0.5 : -0.2,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
      }}
    >
      <Decrypt text={loading ? null : value} waiting={theme.tertiary} />
      {!loading && suffix && (
        <span style={{ fontSize: 12, fontWeight: 400, color: theme.secondary }}>
          {" "}
          {suffix}
        </span>
      )}
    </span>
  );
  const body = loading ? (
    <>
      <span
        style={{
          fontSize: 12,
          fontWeight: hero ? 500 : 400,
          color: theme.secondary,
        }}
      >
        {label}
      </span>
      {figure}
      <Bar width={128} height={10} theme={theme} style={{ margin: "2px 0" }} />
      <Bar height={22} theme={theme} style={{ marginTop: 3 }} />
    </>
  ) : (
    <>
      <span
        style={{
          fontSize: 12,
          fontWeight: hero ? 500 : 400,
          color: theme.secondary,
        }}
      >
        {label}
      </span>
      {figure}
      <span
        style={{
          fontSize: 11,
          lineHeight: "15px",
          minHeight: 15,
          color: theme.secondary,
          overflow: "hidden",
          textOverflow: "ellipsis",
          whiteSpace: "nowrap",
        }}
      >
        {line}
      </span>
      <TrendLine values={series} theme={theme} />
    </>
  );
  const surface = surfaceStyle(theme, variant);
  const style = {
    display: "grid",
    gridTemplateRows: "auto auto auto 28px",
    gap: hero ? 4 : 3,
    padding:
      variant === "plain"
        ? "6px 4px"
        : hero
          ? "16px 18px 14px"
          : "13px 15px 11px",
    ...surface,
    boxShadow: active
      ? `inset 0 0 0 1.5px ${theme.light ? "rgba(0,0,0,.5)" : "rgba(255,255,255,.55)"}`
      : surface.boxShadow,
    transition: "box-shadow 160ms ease, border-color 160ms ease",
    ...look.style,
  } as const;
  const reveal = loading ? look.className : `stats-reveal ${look.className}`;
  return (
    <DashboardSection
      as="div"
      index={index}
      hoverLift={hero}
      style={{ minWidth: 0 }}
    >
      {onClick ? (
        <MotionCardButton
          className={reveal}
          aria-pressed={active}
          onClick={onClick}
          title={hint}
          style={{ ...plain, ...style, width: "100%" }}
        >
          {body}
        </MotionCardButton>
      ) : (
        <div className={reveal} style={style} title={hint}>
          {body}
        </div>
      )}
    </DashboardSection>
  );
}

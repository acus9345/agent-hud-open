import { memo, useState } from "react";
import { ChevronDown } from "lucide-react";
import { Card } from "@agent-hud/ui/components/card";
import { CollapsibleReveal } from "@agent-hud/ui/DashboardMotion";
import { HUDArtwork as HUDLogo } from "@agent-hud/hud/HUDArtwork";
import { vendorKey } from "@agent-hud/hud/model";
import type {
  BillingAccount,
  Quota,
  StatisticsProvider,
} from "@agent-hud/protocol";
import {
  agentAccent,
  compact,
  kindLabel,
  number,
  plain,
  row,
  surfaceStyle,
  truncate,
  useAnimatedNumber,
  type StatsTheme,
} from "./StatsTheme";
import { costHelp, costText } from "./cost-text";
import { ModelList } from "./ModelList";
import { Sparkline } from "./Sparkline";
import { QuotaRows, type QuotaRow } from "./dashboard/QuotaRows";
import { usePendingLook, type Pending } from "./dashboard/pending";
import { moneyAmount } from "@agent-hud/ui/format";
import { useFormat, useT } from "@agent-hud/ui/format-context";

const ALL_KINDS = [
  "input",
  "output",
  "reasoning",
  "cacheWrite",
  "cacheRead",
] as const;

export const ProviderCard = memo(function ProviderCard({
  provider,
  total,
  perRow,
  label,
  theme,
  highlighted = false,
  billing = [],
  quota,
  pending,
}: {
  provider: StatisticsProvider;
  total: number;
  perRow: number;
  label: string;
  theme: StatsTheme;
  highlighted?: boolean;
  billing?: BillingAccount[];
  quota?: { rows: QuotaRow[]; live: Map<string, Quota> };
  pending?: (Pending | null)[];
}) {
  const t = useT();
  const { language } = useFormat();
  const [expanded, setExpanded] = useState(false);
  const look = usePendingLook(...(pending ?? []));
  const animatedTokens = useAnimatedNumber(provider.selectedTokens);
  const animatedShare = useAnimatedNumber(
    total ? Math.round((provider.selectedTokens / total) * 100) : 0,
  );
  const providerBilling = billing.filter(
    (b) => vendorKey(b.vendor) === vendorKey(provider.id),
  );
  const moneyNote = [
    provider.cacheReadShare != null &&
      t("Cache reads {{share}}% of cost", {
        share: Math.round(provider.cacheReadShare * 100),
      }),
    provider.recachedCost != null &&
      t("Cache rewrites ≈{{cost}}", {
        cost: moneyAmount(provider.recachedCost, "USD", language),
      }),
  ]
    .filter(Boolean)
    .join(" · ");
  const balance =
    providerBilling[0] &&
    moneyAmount(
      providerBilling[0].amount,
      providerBilling[0].currency,
      language,
    );
  const accent = agentAccent(provider.id) ?? theme.secondary;
  const caption = { fontSize: 10.5, color: theme.tertiary };
  const statValue = { ...number, fontWeight: 600, color: theme.text };
  const badgeStyle = {
    ...number,
    padding: "2px 7px",
    borderRadius: 100,
    background: theme.segment,
    color: theme.secondary,
    fontSize: 10.5,
  };
  const summaryCells: [string, string][] = [
    [kindLabel("input", language), compact(Math.round(provider.tokens.input))],
    [
      kindLabel("output", language),
      compact(Math.round(provider.tokens.output)),
    ],
    [
      t("Cache"),
      compact(
        Math.round(provider.tokens.cacheRead + provider.tokens.cacheWrite),
      ),
    ],
  ];
  return (
    <Card
      id={`agent-${vendorKey(provider.id)}`}
      className={`statistics-agent-card stats-reveal ${look.className}`}
      style={{
        ...surfaceStyle(theme, "secondary"),
        ...look.style,
        padding: "16px 18px",
        gap: 14,
        transition: "border-color 220ms ease, box-shadow 220ms ease",
        ...(highlighted && {
          border: "1.5px solid #5b7a45",
          boxShadow: "0 0 0 2px rgba(91, 122, 69, 0.22)",
        }),
      }}
    >
      <div style={{ ...row, gap: 8 }}>
        <span
          style={{
            width: 26,
            height: 26,
            borderRadius: 7,
            background: `${accent}28`,
            display: "inline-grid",
            placeItems: "center",
            flexShrink: 0,
          }}
        >
          <HUDLogo source={provider.id} size={14} light={theme.light} />
        </span>
        <strong style={{ ...truncate, fontSize: 13.5, fontWeight: 600 }}>
          {provider.name}
        </strong>
        {balance && <span style={badgeStyle}>{balance}</span>}
        <span
          title={t(
            "Share of the selected range, agents, models and token kinds",
          )}
          style={{ ...badgeStyle, marginLeft: "auto", fontSize: 11 }}
        >
          {total ? `${animatedShare}%` : "—"}
        </span>
      </div>

      <div
        style={{
          display: "flex",
          alignItems: "flex-end",
          justifyContent: "space-between",
          gap: 12,
        }}
      >
        <div style={{ display: "flex", alignItems: "baseline", gap: 16 }}>
          <div>
            <div
              style={{
                fontSize: 24,
                lineHeight: 1.12,
                fontWeight: 600,
                letterSpacing: "-.45px",
                ...number,
              }}
            >
              {compact(animatedTokens)}
            </div>
            <div style={{ color: theme.secondary, fontSize: 11, marginTop: 2 }}>
              {label}
            </div>
          </div>
          <div title={costHelp(provider.cost, language)}>
            <div style={{ ...statValue, fontSize: 15, lineHeight: 1.2 }}>
              {costText(provider.cost, language)}
            </div>
            <div style={{ ...caption, marginTop: 2 }}>{t("Cost")}</div>
          </div>
        </div>
        <div style={{ width: "36%", maxWidth: 112, minWidth: 68 }}>
          <Sparkline values={provider.series} color={accent} />
        </div>
      </div>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
          gap: 8,
          padding: "8px 10px",
          borderRadius: 10,
          background: theme.light
            ? "rgba(0,0,0,0.03)"
            : "rgba(255,255,255,0.028)",
          border: `1px solid ${theme.divider}`,
        }}
      >
        {summaryCells.map(([name, value]) => (
          <div key={name} style={{ display: "grid", gap: 2, minWidth: 0 }}>
            <span style={caption}>{name}</span>
            <strong style={{ ...statValue, fontSize: 12.5 }}>{value}</strong>
          </div>
        ))}
      </div>

      <ModelList
        models={provider.models}
        total={provider.selectedTokens}
        theme={theme}
      />

      {quota && quota.rows.length > 0 && (
        <div
          style={{
            display: "grid",
            gap: 6,
            minWidth: 0,
            paddingTop: 10,
            borderTop: `1px solid ${theme.divider}`,
          }}
        >
          <span
            style={{ fontSize: 11, fontWeight: 500, color: theme.tertiary }}
          >
            {t("Quota by day")}
          </span>
          <QuotaRows
            rows={quota.rows}
            live={quota.live}
            label={perRow >= 3 ? 92 : 108}
            vendors={null}
            theme={theme}
            light={theme.light}
          />
        </div>
      )}

      <div style={{ display: "grid", gap: 8 }}>
        <button
          type="button"
          onClick={() => setExpanded((open) => !open)}
          aria-expanded={expanded}
          style={{
            ...plain,
            ...row,
            gap: 5,
            fontSize: 11,
            color: theme.tertiary,
            justifySelf: "start",
          }}
        >
          <ChevronDown
            size={11}
            style={{
              transform: expanded ? "rotate(180deg)" : "none",
              transition: "transform 160ms ease",
            }}
          />
          <span>{t("Details")}</span>
        </button>
        <CollapsibleReveal open={expanded}>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
              columnGap: 14,
              rowGap: 5,
              paddingTop: 8,
              borderTop: `1px solid ${theme.divider}`,
            }}
          >
            {ALL_KINDS.map((kind) => (
              <div
                key={kind}
                style={{ ...row, justifyContent: "space-between", gap: 6 }}
              >
                <span style={{ fontSize: 11, color: theme.secondary }}>
                  {kindLabel(kind, language)}
                </span>
                <strong style={{ ...statValue, fontSize: 11.5 }}>
                  {compact(Math.round(provider.tokens[kind]))}
                </strong>
              </div>
            ))}
            {moneyNote && (
              <span style={{ ...caption, gridColumn: "1 / -1", marginTop: 3 }}>
                {moneyNote}
              </span>
            )}
          </div>
        </CollapsibleReveal>
      </div>
    </Card>
  );
});

import { useMemo, type ReactNode } from "react";
import { useClockNow } from "@agent-hud/ui/format-context";
import { motion, useReducedMotionConfig } from "motion/react";
import {
  SCROLL_EASE,
  SNAP_SPRING,
  staggerDelay,
} from "@agent-hud/ui/lib/motion";
import type {
  PanelBlockLayout,
  PanelNode,
  Quota,
  Snapshot,
} from "@agent-hud/protocol";
import { blockRows, quotaRowKey, vendorKey, type HUDConfig } from "../model";
import { pickedAgents } from "../resting-agents";
import { SourceProviderBlock } from "./QuotaSection";
import { SourceBalances } from "./SourceBalances";
import { blockLines, COMPACT_LEAST } from "./block-lines";
import {
  card,
  inPanelOrder,
  type densityMetrics,
  type Logo,
  type palette,
} from "./theme";

/** The panel's blocks in lines, each block a card unless the panel's style
 * asks for none. */
export function QuotaBlocks({
  blocks,
  custom,
  layouts,
  width,
  density,
  separated,
  colors,
}: {
  blocks: { key: string; content: ReactNode }[];
  custom: Record<string, PanelNode>;
  layouts?: Record<string, PanelBlockLayout>;
  width: number;
  density: (typeof densityMetrics)[keyof typeof densityMetrics];
  separated: boolean;
  colors: ReturnType<typeof palette>;
}) {
  const span = (key: string) => {
    const asked = custom[key]?.span ?? layouts?.[key]?.span;
    return typeof asked === "string" ? asked : undefined;
  };
  // Blocks that ask for a half, a third or a quarter share a line where the
  // blocks' width holds them: the panel's, or the column's a row of an
  // agent's gives them.
  // A ring with no legend makes a compact block, as narrow as its picker.
  const least = (key: string) => {
    const layout = layouts?.[key];
    return layout?.legend === false &&
      ["rings", "segments", "ring"].includes(layout.widget ?? "")
      ? COMPACT_LEAST
      : undefined;
  };
  const still = Boolean(useReducedMotionConfig());
  const lines = blockLines(blocks, span, width, density.section, least);
  let cardIndex = 0;
  return (
    <div
      className="hud-source-blocks"
      style={{
        display: "flex",
        flexDirection: "column",
        gap: "var(--hud-section-gap)",
      }}
    >
      {lines.map(({ blocks: line, columns }) => (
        <div
          key={line.map((b) => b.key).join("+")}
          style={{
            display: "grid",
            gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
            gap: density.section,
          }}
        >
          {line.map((block) => {
            const delay = staggerDelay(cardIndex++, 0.02, 0.036);
            return (
              <motion.div
                key={block.key}
                data-hud-block={block.key}
                data-hud-half={columns > 1 ? "true" : undefined}
                initial={still ? false : { opacity: 0, y: 8, scale: 0.985 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={
                  still
                    ? { duration: 0 }
                    : { duration: 0.32, delay, ease: SCROLL_EASE }
                }
                whileHover={
                  still ? undefined : { y: -1, transition: SNAP_SPRING }
                }
                style={{ minWidth: 0, ...(separated ? card(colors) : {}) }}
              >
                {block.content}
              </motion.div>
            );
          })}
        </div>
      ))}
    </div>
  );
}

/** The panel's blocks, in its order: each agent's quota block, each API's
 * balance block, and the controls an agent placed among them. */
export function usePanelBlocks({
  snapshot,
  focusVendor,
  config,
  light,
  Logo,
  active,
  onStats,
  custom,
  customAt,
}: {
  snapshot: Snapshot;
  focusVendor?: string;
  config: HUDConfig;
  light: boolean;
  Logo: Logo;
  active: boolean;
  onStats?: () => void;
  custom: Record<string, PanelNode>;
  customAt: (key: string) => ReactNode;
}) {
  const now = useClockNow(Date.parse(snapshot.generatedAt));
  const rows = useMemo(
    () =>
      snapshot.quotas.filter(
        (row) =>
          !focusVendor || vendorKey(row.vendor) === vendorKey(focusVendor),
      ),
    [snapshot.quotas, focusVendor],
  );
  const rowsByVendor = useMemo(() => {
    const groups = new Map<string, Quota[]>();
    for (const row of rows) {
      const group = groups.get(row.vendor) ?? [];
      group.push(row);
      groups.set(row.vendor, group);
    }
    return groups;
  }, [rows]);
  const billing = snapshot.billing.filter(
    (account) =>
      !focusVendor || vendorKey(account.vendor) === vendorKey(focusVendor),
  );
  const candidates = useMemo(
    () => [
      ...new Set([
        ...rows.map((row) => row.vendor),
        ...snapshot.providers
          .filter(
            (provider) =>
              provider.detected &&
              provider.enabled &&
              (!focusVendor ||
                vendorKey(provider.id) === vendorKey(focusVendor)) &&
              !snapshot.billing.some(
                (account) =>
                  vendorKey(account.vendor) === vendorKey(provider.id),
              ),
          )
          .map((provider) => provider.id),
      ]),
    ],
    [rows, snapshot.providers, snapshot.billing, focusVendor],
  );
  // The agents this screen's HUD shows, so the panel that opens from it
  // shows the same ones (Open #85).
  const picked = pickedAgents(
    [...candidates, ...billing.map((account) => account.vendor)].map(vendorKey),
    config.panelLayout,
  );
  const vendorNames = candidates.filter(
    (vendor) =>
      picked.has(vendorKey(vendor)) &&
      !config.panelLayout.blocks?.[vendor]?.hidden,
  );
  const blocks = inPanelOrder<{ key: string; content: ReactNode }>(
    [
      ...vendorNames.map((vendor) => ({
        key: vendor,
        content: (
          <SourceProviderBlock
            vendor={vendor}
            rows={blockRows(
              rowsByVendor.get(vendor) ?? [],
              config.panelLayout.blocks?.[vendor],
            ).map((row) => ({ ...row, key: quotaRowKey(row) }))}
            layout={config.panelLayout.blocks?.[vendor]}
            snapshot={snapshot}
            active={active}
            now={now}
            light={light}
            Logo={Logo}
            showReset={config.resetCountdown}
            accountInfo={snapshot.accounts}
            onStats={onStats}
          />
        ),
      })),
      ...[...new Set(billing.map((account) => account.vendor))]
        .filter(
          (vendor) =>
            picked.has(vendorKey(vendor)) &&
            !config.panelLayout.blocks?.[`api:${vendor}`]?.hidden,
        )
        .map((vendor) => ({
          key: `api:${vendor}`,
          content: (
            <SourceBalances
              billing={billing.filter((account) => account.vendor === vendor)}
              layout={config.panelLayout.blocks?.[`api:${vendor}`]}
              light={light}
              Logo={Logo}
              onStats={onStats}
            />
          ),
        })),
      ...(config.panelOrder ?? [])
        .filter((key) => custom[key] && custom[key].hidden !== true)
        .map((key) => ({ key, content: customAt(key) })),
    ],
    config.panelOrder ?? [],
  );
  return { blocks, vendorNames };
}

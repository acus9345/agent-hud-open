import { ArrowRight, MessageSquare } from "lucide-react";
import { Card } from "@agent-hud/ui/components/card";
import { Button } from "@agent-hud/ui/components/button";
import type { AgentMessageRecord } from "@agent-hud/protocol";
import { age } from "@agent-hud/ui/format";
import { useFormat, useT } from "@agent-hud/ui/format-context";
import {
  filterAgentMessages,
  groupMessageChains,
  sameSessionId,
} from "../lib/agent-messages";
import {
  DeliveryStatePill,
  MessageBodyText,
  PartyChip,
  pillStyle,
} from "./SessionAgentMessagesCard";
import { RowsPlaceholder } from "./SessionPlaceholders";
import { ALL, card, numeric, row, timeLabel, type Theme } from "./SessionTheme";

/** Full feed of inter-agent messages grouped by conversation chain, inside Sessions. */
export function AgentMessagesList({
  messages,
  loading,
  error,
  onRetry,
  enabled,
  source,
  query,
  now,
  theme,
  selectedId,
  onSelect,
}: {
  messages: readonly AgentMessageRecord[];
  loading: boolean;
  error: string | null;
  onRetry: () => void;
  enabled: boolean;
  source: string;
  query: string;
  now: number;
  theme: Theme;
  selectedId: string | null;
  onSelect: (sessionId: string) => void;
}) {
  const t = useT();
  const { language } = useFormat();

  if (loading && messages.length === 0) {
    return <RowsPlaceholder count={4} theme={theme} />;
  }

  if (error) {
    return (
      <div
        role="alert"
        style={{
          ...row,
          color: theme.secondary,
          fontSize: 12,
          marginBottom: 12,
        }}
      >
        <span>{error}</span>
        <Button variant="ghost" size="sm" onClick={onRetry}>
          {t("Retry")}
        </Button>
      </div>
    );
  }

  const filtered = filterAgentMessages(messages, source, query, ALL);
  const chains = groupMessageChains(filtered);
  const hasFilters = Boolean(query.trim()) || source !== ALL;

  if (chains.length === 0) {
    return (
      <Card style={{ ...card(theme), gap: 6, padding: "18px 16px" }}>
        <div style={{ ...row, gap: 8, color: theme.text, fontWeight: 500 }}>
          <MessageSquare size={14} color={theme.secondary} />
          <span>
            {hasFilters
              ? t("No agent messages match these filters.")
              : t("No agent messages recorded in the last 7 days.")}
          </span>
        </div>
        {!hasFilters && !enabled && (
          <p
            style={{
              margin: 0,
              fontSize: 12,
              lineHeight: 1.45,
              color: theme.secondary,
            }}
          >
            {t(
              'Turn on "Let agents message each other" in Settings → Agents to allow sessions to send messages here.',
            )}
          </p>
        )}
      </Card>
    );
  }

  return (
    <div style={{ display: "grid", gap: 14 }}>
      {chains.map((chain) => {
        const countLabel =
          chain.messages.length === 1
            ? t("1 message")
            : t("{{count}} messages", { count: chain.messages.length });
        const latestAge = age((now - chain.latestAtMs) / 1000, language);
        return (
          <Card
            key={chain.chainId}
            style={{ ...card(theme, "0"), overflow: "hidden", gap: 0 }}
          >
            <div
              style={{
                ...row,
                justifyContent: "space-between",
                padding: "8px 14px",
                borderBottom: `1px solid ${theme.divider}`,
                fontSize: 11,
                color: theme.secondary,
              }}
            >
              <span style={{ ...row, gap: 6 }}>
                <MessageSquare size={12} color={theme.secondary} />
                <span style={numeric}>{countLabel}</span>
              </span>
              <span style={numeric}>{latestAge}</span>
            </div>
            <div>
              {chain.messages.map((message, index) => {
                const when = `${timeLabel(new Date(message.sentAtMs).toISOString(), language)} · ${age((now - message.sentAtMs) / 1000, language)}`;
                return (
                  <div
                    key={message.id}
                    style={{
                      display: "grid",
                      gap: 8,
                      padding: "12px 14px",
                      borderTop:
                        index === 0 ? undefined : `1px solid ${theme.divider}`,
                    }}
                  >
                    <div
                      style={{
                        ...row,
                        flexWrap: "wrap",
                        gap: 6,
                        justifyContent: "space-between",
                      }}
                    >
                      <div style={{ ...row, flexWrap: "wrap", gap: 6 }}>
                        <PartyChip
                          party={message.from}
                          highlighted={Boolean(
                            selectedId &&
                            sameSessionId(message.from.sessionId, selectedId),
                          )}
                          theme={theme}
                          onSelect={onSelect}
                        />
                        <ArrowRight size={12} color={theme.secondary} />
                        <PartyChip
                          party={message.to}
                          highlighted={Boolean(
                            selectedId &&
                            sameSessionId(message.to.sessionId, selectedId),
                          )}
                          theme={theme}
                          onSelect={onSelect}
                        />
                      </div>
                      <div style={{ ...row, flexWrap: "wrap", gap: 6 }}>
                        {chain.messages.length > 1 && (
                          <span
                            style={{
                              ...pillStyle("neutral", theme),
                              ...numeric,
                            }}
                          >
                            #{message.count}
                          </span>
                        )}
                        {message.kind === "start" && (
                          <span style={pillStyle("neutral", theme)}>
                            {t("Started")}
                          </span>
                        )}
                        {message.steered && (
                          <span style={pillStyle("neutral", theme)}>
                            {t("Steered")}
                          </span>
                        )}
                        {message.replyTo && (
                          <span style={pillStyle("neutral", theme)}>
                            {t("Reply")}
                          </span>
                        )}
                        <DeliveryStatePill
                          state={message.state}
                          theme={theme}
                        />
                        <span
                          style={{
                            fontSize: 11,
                            color: theme.secondary,
                            ...numeric,
                          }}
                        >
                          {when}
                        </span>
                      </div>
                    </div>
                    <MessageBodyText
                      text={message.text}
                      reason={message.reason}
                      state={message.state}
                      theme={theme}
                    />
                  </div>
                );
              })}
            </div>
          </Card>
        );
      })}
    </div>
  );
}

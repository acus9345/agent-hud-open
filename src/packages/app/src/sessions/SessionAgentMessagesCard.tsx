import { useState, type CSSProperties } from "react";
import { Card } from "@agent-hud/ui/components/card";
import { HUDArtwork as HUDLogo } from "@agent-hud/hud/HUDArtwork";
import type {
  AgentMessageParty,
  AgentMessageRecord,
} from "@agent-hud/protocol";
import { age } from "@agent-hud/ui/format";
import { useFormat, useT } from "@agent-hud/ui/format-context";
import { sameSessionId } from "../lib/agent-messages";
import {
  card,
  CardTitle,
  clipped,
  numeric,
  plain,
  row,
  timeLabel,
  type Theme,
} from "./SessionTheme";

const BODY_PREVIEW_LIMIT = 360;

export function pillStyle(
  tone: "sage" | "terracotta" | "neutral",
  theme: Theme,
): CSSProperties {
  const tones = {
    sage: {
      color: theme.light ? "#2c5e3b" : "#96b87f",
      background: theme.light
        ? "rgba(31, 157, 85, 0.12)"
        : "rgba(91, 122, 69, 0.22)",
      border: `1px solid ${theme.light ? "rgba(31, 157, 85, 0.28)" : "rgba(91, 122, 69, 0.36)"}`,
    },
    terracotta: {
      color: theme.light ? "#a8332c" : "#e07b74",
      background: "rgba(195, 75, 68, 0.16)",
      border: "1px solid rgba(195, 75, 68, 0.34)",
    },
    neutral: {
      color: theme.secondary,
      background: theme.segment,
      border: `1px solid ${theme.divider}`,
    },
  };
  return {
    display: "inline-flex",
    alignItems: "center",
    padding: "1px 7px",
    borderRadius: 999,
    fontSize: 10.5,
    fontWeight: 500,
    lineHeight: 1.45,
    whiteSpace: "nowrap",
    flexShrink: 0,
    ...tones[tone],
  };
}

export function DeliveryStatePill({
  state,
  theme,
}: {
  state: string;
  theme: Theme;
}) {
  const t = useT();
  const labels: Record<string, string> = {
    queued: t("Queued"),
    taken: t("Delivered"),
    steered: t("Steered"),
    started: t("Started"),
    refused: t("Refused"),
    expired: t("Expired"),
    uncertain: t("Uncertain"),
  };
  const tone =
    state === "queued" ||
    state === "taken" ||
    state === "steered" ||
    state === "started"
      ? "sage"
      : state === "refused" || state === "expired"
        ? "terracotta"
        : "neutral";
  return <span style={pillStyle(tone, theme)}>{labels[state] ?? state}</span>;
}

export function PartyChip({
  party,
  highlighted = false,
  theme,
  onSelect,
}: {
  party: AgentMessageParty;
  highlighted?: boolean;
  theme: Theme;
  onSelect?: (sessionId: string) => void;
}) {
  const interactive = Boolean(party.sessionId && onSelect);
  const label = party.title?.trim() || party.client;
  const chipStyle: CSSProperties = {
    ...row,
    gap: 5,
    maxWidth: 190,
    padding: "2px 7px",
    borderRadius: 6,
    fontSize: 11.5,
    fontWeight: 500,
    color: theme.text,
    background: highlighted
      ? theme.light
        ? "rgba(31, 157, 85, 0.12)"
        : "rgba(91, 122, 69, 0.24)"
      : theme.segment,
    border: `1px solid ${
      highlighted
        ? theme.light
          ? "rgba(31, 157, 85, 0.3)"
          : "rgba(91, 122, 69, 0.42)"
        : theme.divider
    }`,
  };
  const inner = (
    <>
      <HUDLogo source={party.client} size={13} light={theme.light} />
      <span style={clipped}>{label}</span>
    </>
  );
  if (!interactive) return <span style={chipStyle}>{inner}</span>;
  return (
    <button
      type="button"
      title={party.sessionId}
      onClick={() => onSelect?.(party.sessionId)}
      style={{ ...plain, ...chipStyle, cursor: "pointer" }}
    >
      {inner}
    </button>
  );
}

export function MessageBodyText({
  text,
  reason,
  state,
  theme,
}: {
  text: string | null;
  reason: string | null;
  state: string;
  theme: Theme;
}) {
  const t = useT();
  const [expanded, setExpanded] = useState(false);
  const long = Boolean(text && text.length > BODY_PREVIEW_LIMIT);
  const shown =
    text && long && !expanded
      ? `${text.slice(0, BODY_PREVIEW_LIMIT).trimEnd()}…`
      : text;

  return (
    <div style={{ display: "grid", gap: 5 }}>
      {shown !== null ? (
        <div
          style={{
            fontSize: 12.5,
            lineHeight: 1.48,
            color: theme.text,
            whiteSpace: "pre-wrap",
            overflowWrap: "anywhere",
            userSelect: "text",
          }}
        >
          {shown}
          {long && (
            <button
              type="button"
              onClick={() => setExpanded((value) => !value)}
              style={{
                ...plain,
                display: "inline",
                marginLeft: 6,
                fontSize: 11,
                fontWeight: 500,
                color: theme.action,
              }}
            >
              {expanded ? t("Show less") : t("Show more")}
            </button>
          )}
        </div>
      ) : (
        <div
          style={{
            fontSize: 11.5,
            fontStyle: "italic",
            color: theme.secondary,
          }}
        >
          {t("Message text was not saved for this earlier send.")}
        </div>
      )}
      {reason && (
        <div
          style={{
            fontSize: 11.5,
            lineHeight: 1.4,
            overflowWrap: "anywhere",
            color:
              state === "refused" || state === "expired"
                ? theme.light
                  ? "#a8332c"
                  : "#e07b74"
                : theme.secondary,
          }}
        >
          {reason}
        </div>
      )}
    </div>
  );
}

/** Per-session Agent Messages card rendered inside Session Detail. */
export function SessionAgentMessagesCard({
  sessionId,
  messages,
  now,
  theme,
  onSelectSession,
}: {
  sessionId: string;
  messages: readonly AgentMessageRecord[];
  now: number;
  theme: Theme;
  onSelectSession: (id: string) => void;
}) {
  const t = useT();
  const { language } = useFormat();
  if (messages.length === 0) return null;

  return (
    <Card style={card(theme)}>
      <CardTitle
        title={t("Agent messages")}
        right={<span style={numeric}>{messages.length}</span>}
        theme={theme}
      />
      <div style={{ display: "grid", gap: 0 }}>
        {messages.map((message, index) => {
          const outgoing = sameSessionId(message.from.sessionId, sessionId);
          const peer = outgoing ? message.to : message.from;
          const time = `${timeLabel(new Date(message.sentAtMs).toISOString(), language)} · ${age((now - message.sentAtMs) / 1000, language)}`;
          return (
            <div
              key={message.id}
              style={{
                display: "grid",
                gap: 7,
                paddingTop: index === 0 ? 4 : 10,
                paddingBottom: index === messages.length - 1 ? 2 : 10,
                borderTop:
                  index === 0 ? undefined : `1px solid ${theme.divider}`,
              }}
            >
              <div style={{ ...row, flexWrap: "wrap", gap: 6 }}>
                <span style={{ fontSize: 11, color: theme.secondary }}>
                  {outgoing ? t("Sent to") : t("From")}
                </span>
                <PartyChip
                  party={peer}
                  theme={theme}
                  onSelect={onSelectSession}
                />
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
                  <span style={pillStyle("neutral", theme)}>{t("Reply")}</span>
                )}
                <DeliveryStatePill state={message.state} theme={theme} />
                <span
                  style={{
                    marginLeft: "auto",
                    fontSize: 11,
                    color: theme.secondary,
                    ...numeric,
                  }}
                >
                  {time}
                </span>
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
}

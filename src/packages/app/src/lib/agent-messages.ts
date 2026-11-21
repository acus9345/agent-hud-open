import type { AgentMessageRecord } from "@agent-hud/protocol";

export interface AgentMessageChain {
  chainId: string;
  latestAtMs: number;
  messages: AgentMessageRecord[];
}

const CLIENT_PREFIXES = [
  "antigravity:",
  "deepseek:",
  "cursor:",
  "trae:",
  "trae-cn:",
  "workbuddy:",
  "codebuddy:",
  "zcode:",
  "qoder:",
  "qoder-cn:",
  "qoder-work:",
  "kimi:",
];

/** Normalizes snapshot-prefixed session ids (e.g. `antigravity:id`, `id:main`) to bare form. */
export function bareSessionId(id: string): string {
  let stripped = id;
  for (const prefix of CLIENT_PREFIXES) {
    if (stripped.startsWith(prefix)) {
      stripped = stripped.slice(prefix.length);
      break;
    }
  }
  return stripped.endsWith(":main") ? stripped.slice(0, -5) : stripped;
}

export function sameSessionId(a: string, b: string): boolean {
  if (!a || !b) return false;
  return bareSessionId(a) === bareSessionId(b);
}

/** Distinct client sources appearing as sender or receiver across `messages`. */
export function messageClientSources(
  messages: readonly AgentMessageRecord[],
): string[] {
  const seen = new Set<string>();
  for (const message of messages) {
    if (message.from.client) seen.add(message.from.client);
    if (message.to.client) seen.add(message.to.client);
  }
  return [...seen].sort((a, b) => a.localeCompare(b));
}

/** Filters messages by source client and search query across body, party titles, clients, ids, and reason. */
export function filterAgentMessages(
  messages: readonly AgentMessageRecord[],
  source: string,
  query: string,
  allSentinel: string,
): AgentMessageRecord[] {
  const trimmed = query.trim().toLowerCase();
  return messages.filter((message) => {
    if (
      source !== allSentinel &&
      message.from.client !== source &&
      message.to.client !== source
    ) {
      return false;
    }
    if (!trimmed) return true;
    const fields = [
      message.text,
      message.from.title,
      message.to.title,
      message.from.client,
      message.to.client,
      message.from.sessionId,
      message.to.sessionId,
      message.reason,
    ];
    return fields.some((field) => field?.toLowerCase().includes(trimmed));
  });
}

/** Groups messages by conversation chain (newest chain first, messages chronological inside each chain). */
export function groupMessageChains(
  messages: readonly AgentMessageRecord[],
): AgentMessageChain[] {
  const buckets = new Map<string, AgentMessageRecord[]>();
  for (const message of messages) {
    const key = message.chain || message.id;
    const bucket = buckets.get(key);
    if (bucket) bucket.push(message);
    else buckets.set(key, [message]);
  }
  const chains: AgentMessageChain[] = [];
  for (const [chainId, items] of buckets) {
    items.sort(
      (a, b) =>
        a.sentAtMs - b.sentAtMs ||
        a.count - b.count ||
        a.id.localeCompare(b.id),
    );
    const latestAtMs = items.reduce(
      (max, item) => Math.max(max, item.sentAtMs),
      0,
    );
    chains.push({ chainId, latestAtMs, messages: items });
  }
  chains.sort((a, b) => b.latestAtMs - a.latestAtMs);
  return chains;
}

/** Messages sent by or delivered to `sessionId`, newest first. */
export function messagesForSession(
  messages: readonly AgentMessageRecord[],
  sessionId: string | null,
): AgentMessageRecord[] {
  if (!sessionId) return [];
  return messages
    .filter(
      (message) =>
        sameSessionId(message.from.sessionId, sessionId) ||
        sameSessionId(message.to.sessionId, sessionId),
    )
    .sort((a, b) => b.sentAtMs - a.sentAtMs || b.count - a.count);
}

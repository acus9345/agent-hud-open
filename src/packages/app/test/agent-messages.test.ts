import { test } from "node:test";
import assert from "node:assert/strict";
import type { AgentMessageRecord } from "@agent-hud/protocol";
import {
  bareSessionId,
  filterAgentMessages,
  groupMessageChains,
  messageClientSources,
  messagesForSession,
  sameSessionId,
} from "../src/lib/agent-messages.ts";

function sampleMessage(
  overrides: Partial<AgentMessageRecord> & { id: string },
): AgentMessageRecord {
  return {
    kind: "send",
    chain: overrides.id,
    count: 1,
    from: { client: "claude", sessionId: "c-1", title: "Planner" },
    to: { client: "codex", sessionId: "x-1", title: "Worker" },
    replyTo: null,
    repliedBy: null,
    sentAtMs: 1_700_000_000_000,
    state: "queued",
    reason: null,
    answered: false,
    steered: false,
    text: "Check the parser outputs.",
    ...overrides,
  };
}

test("bareSessionId and sameSessionId normalize provider prefixes and :main suffix", () => {
  assert.equal(bareSessionId("antigravity:abc-123"), "abc-123");
  assert.equal(bareSessionId("deepseek:sess-1:main"), "sess-1");
  assert.equal(sameSessionId("antigravity:abc-123", "abc-123"), true);
  assert.equal(sameSessionId("deepseek:sess-1:main", "sess-1"), true);
  assert.equal(sameSessionId("", "abc-123"), false);
  assert.equal(sameSessionId("a", "b"), false);
});

test("messageClientSources lists unique sorted clients from senders and recipients", () => {
  const sources = messageClientSources([
    sampleMessage({
      id: "m1",
      from: { client: "codex", sessionId: "x", title: null },
      to: { client: "claude", sessionId: "c", title: null },
    }),
    sampleMessage({
      id: "m2",
      from: { client: "antigravity", sessionId: "a", title: null },
      to: { client: "codex", sessionId: "x", title: null },
    }),
  ]);
  assert.deepEqual(sources, ["antigravity", "claude", "codex"]);
});

test("filterAgentMessages filters by source and searches text, titles, clients, ids, and reason", () => {
  const messages = [
    sampleMessage({
      id: "m1",
      from: { client: "claude", sessionId: "c1", title: "Design Lead" },
      to: { client: "codex", sessionId: "x1", title: "Build Runner" },
      text: "Run cargo test after editing.",
    }),
    sampleMessage({
      id: "m2",
      from: { client: "deepseek", sessionId: "d1", title: "Reviewer" },
      to: { client: "antigravity", sessionId: "a1", title: "UI Agent" },
      state: "refused",
      reason: "Session is offline",
      text: "Verify dark theme borders.",
    }),
  ];

  assert.equal(
    filterAgentMessages(messages, "__all__", "", "__all__").length,
    2,
  );
  assert.deepEqual(
    filterAgentMessages(messages, "deepseek", "", "__all__").map((m) => m.id),
    ["m2"],
  );
  assert.deepEqual(
    filterAgentMessages(messages, "__all__", "cargo test", "__all__").map(
      (m) => m.id,
    ),
    ["m1"],
  );
  assert.deepEqual(
    filterAgentMessages(messages, "__all__", "offline", "__all__").map(
      (m) => m.id,
    ),
    ["m2"],
  );
  assert.deepEqual(
    filterAgentMessages(messages, "__all__", "ui agent", "__all__").map(
      (m) => m.id,
    ),
    ["m2"],
  );
});

test("groupMessageChains orders chains newest-first and turns chronological within each chain", () => {
  const c1First = sampleMessage({
    id: "m1",
    chain: "m1",
    count: 1,
    sentAtMs: 1_000,
  });
  const c2Only = sampleMessage({
    id: "m2",
    chain: "m2",
    count: 1,
    sentAtMs: 2_000,
  });
  const c1Reply = sampleMessage({
    id: "m3",
    chain: "m1",
    count: 2,
    sentAtMs: 3_000,
  });

  const chains = groupMessageChains([c1Reply, c2Only, c1First]);
  assert.equal(chains.length, 2);
  assert.equal(chains[0].chainId, "m1");
  assert.equal(chains[0].latestAtMs, 3_000);
  assert.deepEqual(
    chains[0].messages.map((m) => m.id),
    ["m1", "m3"],
  );
  assert.equal(chains[1].chainId, "m2");
});

test("messagesForSession matches normalized sender and recipient session ids newest-first", () => {
  const older = sampleMessage({
    id: "m1",
    sentAtMs: 1_000,
    from: { client: "claude", sessionId: "target-sess", title: null },
    to: { client: "codex", sessionId: "other", title: null },
  });
  const newer = sampleMessage({
    id: "m2",
    sentAtMs: 2_000,
    from: { client: "codex", sessionId: "other", title: null },
    to: { client: "antigravity", sessionId: "target-sess", title: null },
  });
  const unrelated = sampleMessage({
    id: "m3",
    sentAtMs: 3_000,
    from: { client: "claude", sessionId: "x", title: null },
    to: { client: "codex", sessionId: "y", title: null },
  });

  assert.deepEqual(
    messagesForSession(
      [older, newer, unrelated],
      "antigravity:target-sess",
    ).map((m) => m.id),
    ["m2", "m1"],
  );
  assert.deepEqual(messagesForSession([older], null), []);
});

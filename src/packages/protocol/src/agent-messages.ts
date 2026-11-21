export interface AgentMessageParty {
  client: string;
  sessionId: string;
  title: string | null;
}

export interface AgentMessageRecord {
  id: string;
  kind: "send" | "start";
  chain: string;
  count: number;
  from: AgentMessageParty;
  to: AgentMessageParty;
  replyTo: string | null;
  repliedBy: string | null;
  sentAtMs: number;
  state: string;
  reason: string | null;
  answered: boolean;
  steered: boolean;
  text: string | null;
}

export interface AgentMessagesResponse {
  messages: AgentMessageRecord[];
}

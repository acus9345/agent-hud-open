import { useCallback, useEffect, useRef, useState } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import type {
  AgentMessageRecord,
  AgentMessagesResponse,
  NativeMethod,
} from "@agent-hud/protocol";

type Action = (
  method: NativeMethod,
  params?: Record<string, unknown>,
) => Promise<unknown>;

const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : String(error);

/** Loads inter-agent messages recorded by the host messaging journal and refreshes on updates. */
export function useAgentMessages(active: boolean, action: Action) {
  const actionRef = useRef(action);
  actionRef.current = action;
  const sequence = useRef(0);
  const mounted = useRef(false);
  const [messages, setMessages] = useState<AgentMessageRecord[]>([]);
  const [loadedOnce, setLoadedOnce] = useState(false);
  const [fetching, setFetching] = useState(active);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const retry = useCallback(() => setTick((value) => value + 1), []);

  useEffect(() => {
    const id = ++sequence.current;
    if (!active) {
      setFetching(false);
      return;
    }
    setFetching(true);
    setError(null);
    void actionRef
      .current("agent_messages", {})
      .then((value) => {
        if (!mounted.current || sequence.current !== id) return;
        const response = value as AgentMessagesResponse | null | undefined;
        setMessages(Array.isArray(response?.messages) ? response.messages : []);
        setLoadedOnce(true);
        setError(null);
      })
      .catch((failure: unknown) => {
        if (!mounted.current || sequence.current !== id) return;
        setError(errorMessage(failure));
      })
      .finally(() => {
        if (mounted.current && sequence.current === id) setFetching(false);
      });
  }, [active, tick]);

  useEffect(() => {
    if (!active || !isTauri()) return;
    const subscription = listen("agent-messages:changed", () => {
      setTick((value) => value + 1);
    });
    return () => {
      void subscription.then((unlisten) => unlisten());
    };
  }, [active]);

  return {
    messages,
    loading: !loadedOnce && fetching,
    error,
    retry,
  };
}

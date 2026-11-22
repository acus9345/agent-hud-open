import { useEffect, useState, type CSSProperties } from "react";
import { useT } from "@agent-hud/ui/format-context";
import { Notice, Pill, Row, Section } from "./SettingsPrimitives";
import { SmallButton } from "./SettingsControls";

export type RemoteMode = "lan" | "tailscale" | "relay";

export interface RemoteAccessStatus {
  mode: RemoteMode;
  relayUrl: string;
  sshTarget: string;
  remotePort: number;
  state: string;
  error?: string | null;
}

const inputStyle: CSSProperties = {
  width: "100%",
  padding: "6px 10px",
  borderRadius: 8,
  border: "1px solid var(--hud-border, rgba(255,255,255,0.14))",
  background: "var(--hud-surface-2, rgba(255,255,255,0.04))",
  color: "inherit",
  fontSize: 12.5,
  fontFamily: "var(--font-mono, monospace)",
  outline: "none",
};

const modes: { id: RemoteMode; label: string }[] = [
  { id: "lan", label: "LAN only" },
  { id: "tailscale", label: "Tailscale" },
  { id: "relay", label: "TLS-passthrough relay" },
];

export function RemoteAccessSection({
  remote,
  pending,
  onSave,
}: {
  remote: RemoteAccessStatus;
  pending: boolean;
  onSave: (next: {
    mode: RemoteMode;
    relayUrl: string;
    sshTarget: string;
    remotePort: number;
  }) => void;
}) {
  const t = useT();
  const [mode, setMode] = useState<RemoteMode>(remote.mode);
  const [relayUrl, setRelayUrl] = useState(remote.relayUrl);
  const [sshTarget, setSshTarget] = useState(remote.sshTarget);
  const [remotePort, setRemotePort] = useState(
    String(remote.remotePort || 4499),
  );

  useEffect(() => {
    setMode(remote.mode);
    setRelayUrl(remote.relayUrl);
    setSshTarget(remote.sshTarget);
    setRemotePort(String(remote.remotePort || 4499));
  }, [remote.mode, remote.relayUrl, remote.sshTarget, remote.remotePort]);

  const parsedPort = Math.max(1, Math.min(65535, Number(remotePort) || 4499));
  const dirty =
    mode !== remote.mode ||
    relayUrl.trim() !== remote.relayUrl ||
    sshTarget.trim() !== remote.sshTarget ||
    parsedPort !== remote.remotePort;

  const statusPill =
    remote.mode !== "relay" ? null : remote.state === "connected" ? (
      <Pill tone="green" dot>
        {t("Tunnel live")}
      </Pill>
    ) : remote.state === "manual" ? (
      <Pill>{t("Manual tunnel")}</Pill>
    ) : remote.state === "reconnecting" ? (
      <Pill>{t("Reconnecting…")}</Pill>
    ) : remote.state === "error" ? (
      <Pill tone="red">{t("Tunnel error")}</Pill>
    ) : (
      <Pill>{t("Idle")}</Pill>
    );

  return (
    <Section
      title={t("Remote access")}
      subtitle={t(
        "Connect your phone away from Wi-Fi without exposing plaintext.",
      )}
      action={statusPill}
    >
      <Row
        title={t("Off-LAN transport")}
        subtitle={
          mode === "tailscale"
            ? t(
                "Automatically advertises your Tailscale 100.x address in pairing codes.",
              )
            : mode === "relay"
              ? t(
                  "Forwards raw end-to-end encrypted TLS through an SNI passthrough relay.",
                )
              : t("Only reachable on the same local Wi-Fi network.")
        }
        below={
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            {modes.map((option) => {
              const selected = option.id === mode;
              return (
                <button
                  key={option.id}
                  type="button"
                  disabled={pending}
                  onClick={() => {
                    setMode(option.id);
                    if (option.id !== "relay") {
                      onSave({
                        mode: option.id,
                        relayUrl: relayUrl.trim(),
                        sshTarget: sshTarget.trim(),
                        remotePort: parsedPort,
                      });
                    }
                  }}
                  style={{
                    padding: "5px 11px",
                    borderRadius: 999,
                    border: selected
                      ? "1px solid #5b7a45"
                      : "1px solid var(--hud-border, rgba(255,255,255,0.14))",
                    background: selected
                      ? "rgba(91, 122, 69, 0.22)"
                      : "transparent",
                    color: "inherit",
                    fontSize: 12,
                    fontWeight: selected ? 600 : 400,
                    cursor: pending ? "default" : "pointer",
                  }}
                >
                  {t(option.label)}
                </button>
              );
            })}
          </div>
        }
      />

      {mode === "relay" && (
        <Row
          title={t("TLS-passthrough relay settings")}
          subtitle={t(
            "Relay URL is embedded in new QR invitations; SSH host starts an automatic reverse tunnel.",
          )}
          below={
            <div style={{ display: "grid", gap: 8 }}>
              <label style={{ display: "grid", gap: 4, fontSize: 11.5 }}>
                <span>{t("Public relay URL")}</span>
                <input
                  type="text"
                  value={relayUrl}
                  placeholder="https://hud.qlinter.org"
                  onChange={(e) => setRelayUrl(e.target.value)}
                  style={inputStyle}
                />
              </label>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 110px",
                  gap: 8,
                }}
              >
                <label style={{ display: "grid", gap: 4, fontSize: 11.5 }}>
                  <span>{t("SSH reverse tunnel host (optional)")}</span>
                  <input
                    type="text"
                    value={sshTarget}
                    placeholder="qonzer"
                    onChange={(e) => setSshTarget(e.target.value)}
                    style={inputStyle}
                  />
                </label>
                <label style={{ display: "grid", gap: 4, fontSize: 11.5 }}>
                  <span>{t("Remote port")}</span>
                  <input
                    type="number"
                    value={remotePort}
                    min={1}
                    max={65535}
                    onChange={(e) => setRemotePort(e.target.value)}
                    style={inputStyle}
                  />
                </label>
              </div>
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <SmallButton
                  disabled={pending || !dirty}
                  onClick={() =>
                    onSave({
                      mode: "relay",
                      relayUrl: relayUrl.trim(),
                      sshTarget: sshTarget.trim(),
                      remotePort: parsedPort,
                    })
                  }
                >
                  {t("Save relay settings")}
                </SmallButton>
              </div>
              {remote.error && <Notice tone="error">{remote.error}</Notice>}
            </div>
          }
        />
      )}
    </Section>
  );
}

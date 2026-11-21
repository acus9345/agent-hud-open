import { useId } from "react";
import { SpringPill } from "@agent-hud/ui/MotionPrimitives";
import { Tabs, TabsList, TabsTrigger } from "@agent-hud/ui/components/tabs";
import type { StatsTheme } from "./StatsTheme";

/** The Statistics window's segmented control; `small` on the Sessions page. */
export function Segments({
  value,
  onChange,
  choices,
  theme,
  label,
  small = false,
}: {
  value: string;
  onChange: (value: string) => void;
  choices: { value: string; label: string }[];
  theme: Pick<StatsTheme, "light" | "text" | "segment" | "selected">;
  label: string;
  small?: boolean;
}) {
  const id = useId();
  return (
    <Tabs
      value={value}
      onValueChange={onChange}
      style={{ gap: 0 }}
      aria-label={label}
    >
      <TabsList
        style={{
          background: theme.segment,
          borderRadius: small ? 6 : 8,
          height: small ? 26 : 28,
          padding: 2,
          gap: 1,
        }}
      >
        {choices.map((choice) => {
          const active = value === choice.value;
          return (
            <TabsTrigger
              key={choice.value}
              value={choice.value}
              style={{
                position: "relative",
                height: small ? 22 : 24,
                padding: small ? "2px 12px" : "3px 11px",
                border: 0,
                borderRadius: small ? 4 : 6,
                fontSize: small ? 11 : 12,
                fontWeight: active ? 500 : 400,
                lineHeight: small ? "14px" : "16px",
                color: theme.text,
                boxShadow: "none",
                background: "transparent",
                transition: "color 160ms ease",
              }}
            >
              {active && (
                <SpringPill
                  layoutId={`segments-${id}`}
                  style={{
                    borderRadius: small ? 4 : 6,
                    background: theme.selected,
                    boxShadow: theme.light
                      ? "0 1px 2px rgba(0,0,0,.08)"
                      : "inset 0 1px 0 rgba(255,255,255,.12)",
                  }}
                />
              )}
              <span style={{ position: "relative", zIndex: 1 }}>
                {choice.label}
              </span>
            </TabsTrigger>
          );
        })}
      </TabsList>
    </Tabs>
  );
}

import type { CSSProperties, MouseEventHandler, ReactNode } from "react";
import { AnimatePresence, motion, useReducedMotionConfig } from "motion/react";
import { SCROLL_EASE, SNAP_SPRING, staggerDelay } from "./lib/motion";

export type SurfaceTier = "primary" | "secondary" | "plain";

/** Scroll-unfurl dashboard block with optional tactile spring hover lift. */
export function DashboardSection({
  as = "section",
  index = 0,
  hoverLift = false,
  className,
  style,
  title,
  children,
}: {
  as?: "section" | "div";
  index?: number;
  hoverLift?: boolean;
  className?: string;
  style?: CSSProperties;
  title?: string;
  children: ReactNode;
}) {
  const reduced = Boolean(useReducedMotionConfig());
  const Comp = as === "div" ? motion.div : motion.section;
  if (reduced) {
    const Tag = as;
    return (
      <Tag className={className} style={style} title={title}>
        {children}
      </Tag>
    );
  }
  return (
    <Comp
      className={className}
      title={title}
      initial={{ opacity: 0, y: 14, scale: 0.988 }}
      animate={{ opacity: 1, y: 0, scale: 1 }}
      transition={{
        duration: 0.36,
        delay: staggerDelay(index, 0.02, 0.042, 0.28),
        ease: SCROLL_EASE,
      }}
      whileHover={hoverLift ? { y: -2, transition: SNAP_SPRING } : undefined}
      style={style}
    >
      {children}
    </Comp>
  );
}

/** Spring-animated pill button for agent filters and quick reset chips. */
export function MotionChip({
  role,
  "aria-checked": ariaChecked,
  "aria-label": ariaLabel,
  title,
  onClick,
  className,
  style,
  children,
}: {
  role?: "radio" | "checkbox";
  "aria-checked"?: boolean;
  "aria-label"?: string;
  title?: string;
  onClick?: MouseEventHandler<HTMLButtonElement>;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const reduced = Boolean(useReducedMotionConfig());
  return (
    <motion.button
      type="button"
      role={role}
      aria-checked={ariaChecked}
      aria-label={ariaLabel}
      title={title}
      onClick={onClick}
      className={className}
      whileHover={reduced ? undefined : { scale: 1.03 }}
      whileTap={reduced ? undefined : { scale: 0.96 }}
      transition={SNAP_SPRING}
      style={style}
    >
      {children}
    </motion.button>
  );
}

/** Tactile interactive wrapper for clickable KPI and agent tiles. */
export function MotionCardButton({
  "aria-pressed": ariaPressed,
  title,
  onClick,
  className,
  style,
  children,
}: {
  "aria-pressed"?: boolean;
  title?: string;
  onClick?: () => void;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const reduced = Boolean(useReducedMotionConfig());
  return (
    <motion.button
      type="button"
      aria-pressed={ariaPressed}
      title={title}
      onClick={onClick}
      className={className}
      whileHover={reduced ? undefined : { y: -2 }}
      whileTap={reduced ? undefined : { scale: 0.985 }}
      transition={SNAP_SPRING}
      style={style}
    >
      {children}
    </motion.button>
  );
}

/** Smooth height/opacity unfurl for collapsible card drawers. */
export function CollapsibleReveal({
  open,
  children,
}: {
  open: boolean;
  children: ReactNode;
}) {
  const reduced = Boolean(useReducedMotionConfig());
  if (reduced) return open ? <>{children}</> : null;
  return (
    <AnimatePresence initial={false}>
      {open && (
        <motion.div
          initial={{ height: 0, opacity: 0 }}
          animate={{ height: "auto", opacity: 1 }}
          exit={{ height: 0, opacity: 0 }}
          transition={{ duration: 0.24, ease: SCROLL_EASE }}
          style={{ overflow: "hidden" }}
        >
          {children}
        </motion.div>
      )}
    </AnimatePresence>
  );
}

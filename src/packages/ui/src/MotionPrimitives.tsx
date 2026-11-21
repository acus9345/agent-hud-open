import { useEffect, type CSSProperties, type ReactNode } from "react";
import {
  AnimatePresence,
  motion,
  useAnimate,
  useReducedMotionConfig,
} from "motion/react";
import {
  EXIT_EASE,
  SCROLL_EASE,
  SNAP_SPRING,
  scrollEdgeGeometry,
  staggerDelay,
  type ScrollEdge,
} from "./lib/motion";

/** Unrolls content from its anchored edge like a scroll with clip-path + directional slide. */
export function ScrollUnfurl({
  edge = "top",
  duration = 0.36,
  delay = 0,
  reduced: forceReduced = false,
  className,
  style,
  children,
}: {
  edge?: ScrollEdge;
  duration?: number;
  delay?: number;
  reduced?: boolean;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const hookReduced = useReducedMotionConfig();
  if (forceReduced || hookReduced) {
    return (
      <div className={className} style={style}>
        {children}
      </div>
    );
  }
  const geo = scrollEdgeGeometry(edge);
  const offsetPos = geo.axis === "x" ? { x: geo.offset } : { y: geo.offset };
  const exitPos =
    geo.axis === "x" ? { x: geo.offset * 0.5 } : { y: geo.offset * 0.5 };
  return (
    <motion.div
      className={className}
      initial={{
        opacity: 0,
        ...offsetPos,
        scale: 0.985,
      }}
      animate={{
        opacity: 1,
        x: 0,
        y: 0,
        scale: 1,
      }}
      exit={{
        opacity: 0,
        ...exitPos,
        scale: 0.985,
        transition: { duration: 0.2, ease: EXIT_EASE },
      }}
      transition={{ duration, delay, ease: SCROLL_EASE }}
      style={{ transformOrigin: geo.origin, ...style }}
    >
      {children}
    </motion.div>
  );
}

/** Staggered item reveal synchronized with ScrollUnfurl. */
export function ScrollItem({
  index,
  edge = "top",
  baseDelay = 0.015,
  step = 0.034,
  reduced: forceReduced = false,
  className,
  style,
  sectionId,
  children,
}: {
  index: number;
  edge?: ScrollEdge;
  baseDelay?: number;
  step?: number;
  reduced?: boolean;
  className?: string;
  style?: CSSProperties;
  sectionId?: string;
  children: ReactNode;
}) {
  const hookReduced = useReducedMotionConfig();
  if (forceReduced || hookReduced) {
    return (
      <div data-hud-section={sectionId} className={className} style={style}>
        {children}
      </div>
    );
  }
  const geo = scrollEdgeGeometry(edge);
  const offset = geo.offset < 0 ? -10 : 10;
  const offsetPos = geo.axis === "x" ? { x: offset } : { y: offset };
  return (
    <motion.div
      data-hud-section={sectionId}
      className={className}
      initial={{ opacity: 0, ...offsetPos, scale: 0.988 }}
      animate={{ opacity: 1, x: 0, y: 0, scale: 1 }}
      transition={{
        duration: 0.32,
        delay: staggerDelay(index, baseDelay, step),
        ease: SCROLL_EASE,
      }}
      style={style}
    >
      {children}
    </motion.div>
  );
}

/** Smooth page transition for Settings and hub views. */
export function ScrollPage({
  pageKey,
  className,
  style,
  children,
}: {
  pageKey: string;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const reduced = useReducedMotionConfig();
  if (reduced) {
    return (
      <div className={className} style={style}>
        {children}
      </div>
    );
  }
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={pageKey}
        className={className}
        style={style}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{
          opacity: 0,
          y: -6,
          transition: { duration: 0.14, ease: EXIT_EASE },
        }}
        transition={{ duration: 0.28, ease: SCROLL_EASE }}
      >
        {children}
      </motion.div>
    </AnimatePresence>
  );
}

/** Keeps a tab pane mounted while playing a scroll-unfurl entrance each time it activates. */
export function ScrollTab({
  active,
  className,
  style,
  children,
}: {
  active: boolean;
  className?: string;
  style?: CSSProperties;
  children: ReactNode;
}) {
  const reduced = useReducedMotionConfig();
  const [scope, animate] = useAnimate();
  useEffect(() => {
    if (!active || reduced || !scope.current) return;
    const controls = animate(
      scope.current,
      { opacity: [0, 1], y: [10, 0] },
      { duration: 0.28, ease: SCROLL_EASE },
    );
    return () => controls.stop();
  }, [active, reduced, animate, scope]);
  return (
    <div
      ref={scope}
      className={className}
      style={{ display: active ? "block" : "none", ...style }}
    >
      {children}
    </div>
  );
}

/** Shared-element sliding active pill for toolbars, pickers, and sidebars. */
export function SpringPill({
  layoutId,
  className,
  style,
}: {
  layoutId: string;
  className?: string;
  style?: CSSProperties;
}) {
  const reduced = useReducedMotionConfig();
  return (
    <motion.span
      aria-hidden="true"
      layoutId={layoutId}
      className={className}
      transition={reduced ? { duration: 0 } : SNAP_SPRING}
      style={{
        position: "absolute",
        inset: 0,
        pointerEvents: "none",
        ...style,
      }}
    />
  );
}

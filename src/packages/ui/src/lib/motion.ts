/** Shared scroll-unfurl motion curves, springs, and edge geometry helpers. */

export type ScrollEdge = "top" | "right" | "bottom" | "left";

/** Exponential deceleration curve for smooth scroll-like unfurling. */
export const SCROLL_EASE = [0.22, 1, 0.36, 1] as const;

/** Fluid settle curve for longer geometry and chart sweeps. */
export const SETTLE_EASE = [0.16, 1, 0.3, 1] as const;

/** Crisp roll-up exit curve. */
export const EXIT_EASE = [0.4, 0, 1, 1] as const;

/** Critically damped, interruptible spring for HUD surfaces and cards. */
export const SCROLL_SPRING = {
  type: "spring",
  stiffness: 350,
  damping: 34,
  mass: 0.75,
} as const;

/** Tactile spring for active pills, icons, and interactive controls. */
export const SNAP_SPRING = {
  type: "spring",
  stiffness: 460,
  damping: 34,
  mass: 0.6,
} as const;

/** Fluid sweep spring for quota progress meters and projections. */
export const BAR_SPRING = {
  type: "spring",
  stiffness: 260,
  damping: 28,
  mass: 0.8,
} as const;

export interface ScrollEdgeGeometry {
  axis: "x" | "y";
  offset: number;
  clipClosed: string;
  clipOpen: string;
  origin: string;
}

const EDGE_GEOMETRY: Record<ScrollEdge, ScrollEdgeGeometry> = {
  top: {
    axis: "y",
    offset: -12,
    clipClosed: "inset(0% 0% 100% 0% round 22px)",
    clipOpen: "inset(0% 0% 0% 0% round 0px)",
    origin: "top center",
  },
  bottom: {
    axis: "y",
    offset: 12,
    clipClosed: "inset(100% 0% 0% 0% round 22px)",
    clipOpen: "inset(0% 0% 0% 0% round 0px)",
    origin: "bottom center",
  },
  left: {
    axis: "x",
    offset: -12,
    clipClosed: "inset(0% 100% 0% 0% round 22px)",
    clipOpen: "inset(0% 0% 0% 0% round 0px)",
    origin: "left center",
  },
  right: {
    axis: "x",
    offset: 12,
    clipClosed: "inset(0% 0% 0% 100% round 22px)",
    clipOpen: "inset(0% 0% 0% 0% round 0px)",
    origin: "right center",
  },
};

/** Geometry for unrolling a panel or card outward from its anchored edge. */
export function scrollEdgeGeometry(edge: ScrollEdge): ScrollEdgeGeometry {
  return EDGE_GEOMETRY[edge];
}

/** Monotonic, capped stagger delay for scroll-cascade items. */
export function staggerDelay(
  index: number,
  base = 0.015,
  step = 0.034,
  cap = 0.24,
): number {
  const safeBase = Math.max(0, base);
  const safeStep = Math.max(0, step);
  const safeCap = Math.max(safeBase, cap);
  return Math.min(safeCap, safeBase + Math.max(0, index) * safeStep);
}

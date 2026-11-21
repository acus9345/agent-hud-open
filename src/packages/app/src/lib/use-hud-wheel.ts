import { useEffect } from "react";
import { isTauri } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";

type NativeWheelEvent = {
  screenId: string;
  x: number;
  y: number;
  deltaX: number;
  deltaY: number;
};

export interface ScrollCandidate {
  overflowX: string;
  overflowY: string;
  scrollWidth: number;
  clientWidth: number;
  scrollHeight: number;
  clientHeight: number;
  scrollLeft: number;
  scrollTop: number;
}

const SCROLLABLE_OVERFLOW = new Set(["auto", "scroll", "overlay"]);

export function selectScrollTarget<T extends ScrollCandidate>(
  chain: readonly T[],
  axis: "x" | "y",
  delta: number,
): T | null {
  if (!Number.isFinite(delta) || delta === 0) return null;
  for (const candidate of chain) {
    if (axis === "y") {
      if (!SCROLLABLE_OVERFLOW.has(candidate.overflowY)) continue;
      if (candidate.scrollHeight <= candidate.clientHeight + 1) continue;
      const room =
        delta > 0
          ? candidate.scrollTop + candidate.clientHeight <
            candidate.scrollHeight - 0.5
          : candidate.scrollTop > 0.5;
      if (room) return candidate;
    } else {
      if (!SCROLLABLE_OVERFLOW.has(candidate.overflowX)) continue;
      if (candidate.scrollWidth <= candidate.clientWidth + 1) continue;
      const room =
        delta > 0
          ? candidate.scrollLeft + candidate.clientWidth <
            candidate.scrollWidth - 0.5
          : candidate.scrollLeft > 0.5;
      if (room) return candidate;
    }
  }
  return null;
}

function collectScrollCandidates(x: number, y: number) {
  const scene = document.querySelector(".hud-scene");
  const bounds = scene?.getBoundingClientRect();
  const clientX = x + (bounds?.left ?? 0);
  const clientY = y + (bounds?.top ?? 0);
  const hit = document.elementFromPoint(clientX, clientY);
  const elements: Element[] = [];
  const seen = new Set<Element>();
  let node: Element | null = hit;
  while (node) {
    seen.add(node);
    elements.push(node);
    if (node === scene) break;
    node = node.parentElement;
  }
  if (hit?.closest(".hud-expanded-surface")) {
    for (const fallback of document.querySelectorAll(
      ".hud-expanded-surface [data-hud-natural-scroll], .hud-expanded-surface .hud-bare-scroll",
    )) {
      if (!seen.has(fallback)) {
        seen.add(fallback);
        elements.push(fallback);
      }
    }
  }
  return elements.map((el) => {
    const style = getComputedStyle(el);
    return {
      el,
      overflowX: style.overflowX,
      overflowY: style.overflowY,
      scrollWidth: el.scrollWidth,
      clientWidth: el.clientWidth,
      scrollHeight: el.scrollHeight,
      clientHeight: el.clientHeight,
      scrollLeft: el.scrollLeft,
      scrollTop: el.scrollTop,
    };
  });
}

/** Applies native wheel events forwarded while Windows routes wheels by focus
 * so an unfocused HUD panel scrolls smoothly on hover without taking focus. */
export function useHUDWheel(screenId: string) {
  useEffect(() => {
    if (!isTauri()) return;
    let disposed = false;
    const subscription = listen<NativeWheelEvent>("hud:wheel", (event) => {
      if (disposed || event.payload.screenId !== screenId) return;
      const { x, y, deltaX, deltaY } = event.payload;
      const candidates = collectScrollCandidates(x, y);
      if (deltaY !== 0) {
        const target = selectScrollTarget(candidates, "y", deltaY);
        if (target) target.el.scrollTop += deltaY;
      }
      if (deltaX !== 0) {
        const target = selectScrollTarget(candidates, "x", deltaX);
        if (target) target.el.scrollLeft += deltaX;
      }
    });
    return () => {
      disposed = true;
      void subscription.then((unlisten) => unlisten());
    };
  }, [screenId]);
}

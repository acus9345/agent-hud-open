import { test } from "node:test";
import assert from "node:assert/strict";
import {
  selectScrollTarget,
  type ScrollCandidate,
} from "../src/lib/use-hud-wheel.ts";

function candidate(overrides: Partial<ScrollCandidate> = {}): ScrollCandidate {
  return {
    overflowX: "visible",
    overflowY: "visible",
    scrollWidth: 300,
    clientWidth: 300,
    scrollHeight: 300,
    clientHeight: 300,
    scrollLeft: 0,
    scrollTop: 0,
    ...overrides,
  };
}

test("selectScrollTarget picks the innermost scrollable container with room", () => {
  const inner = candidate({
    overflowY: "auto",
    scrollHeight: 400,
    clientHeight: 200,
    scrollTop: 40,
  });
  const outer = candidate({
    overflowY: "auto",
    scrollHeight: 900,
    clientHeight: 400,
    scrollTop: 0,
  });

  assert.equal(selectScrollTarget([inner, outer], "y", 50), inner);
  assert.equal(selectScrollTarget([inner, outer], "y", -50), inner);
});

test("selectScrollTarget chains to outer container at top or bottom boundary", () => {
  const innerAtBottom = candidate({
    overflowY: "auto",
    scrollHeight: 400,
    clientHeight: 200,
    scrollTop: 200,
  });
  const outerMiddle = candidate({
    overflowY: "auto",
    scrollHeight: 900,
    clientHeight: 400,
    scrollTop: 120,
  });

  assert.equal(
    selectScrollTarget([innerAtBottom, outerMiddle], "y", 50),
    outerMiddle,
  );
  assert.equal(
    selectScrollTarget([innerAtBottom, outerMiddle], "y", -50),
    innerAtBottom,
  );

  const innerAtTop = candidate({
    overflowY: "scroll",
    scrollHeight: 400,
    clientHeight: 200,
    scrollTop: 0,
  });
  assert.equal(
    selectScrollTarget([innerAtTop, outerMiddle], "y", -50),
    outerMiddle,
  );
});

test("selectScrollTarget skips non-scrollable or hidden-overflow elements", () => {
  const clipped = candidate({
    overflowY: "hidden",
    scrollHeight: 600,
    clientHeight: 200,
  });
  const fits = candidate({
    overflowY: "auto",
    scrollHeight: 200,
    clientHeight: 200,
  });
  const horizontal = candidate({
    overflowX: "auto",
    scrollWidth: 600,
    clientWidth: 300,
    scrollLeft: 50,
  });

  assert.equal(selectScrollTarget([clipped, fits], "y", 40), null);
  assert.equal(
    selectScrollTarget([clipped, fits, horizontal], "x", 40),
    horizontal,
  );
  assert.equal(selectScrollTarget([horizontal], "x", 0), null);
});

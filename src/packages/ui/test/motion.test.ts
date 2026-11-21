import { test } from "node:test";
import assert from "node:assert/strict";
import { scrollEdgeGeometry, staggerDelay } from "../src/lib/motion.ts";

test("scrollEdgeGeometry maps each edge to an inward-anchored unfurl direction", () => {
  const top = scrollEdgeGeometry("top");
  assert.equal(top.axis, "y");
  assert.ok(top.offset < 0);
  assert.equal(top.origin, "top center");
  assert.match(top.clipClosed, /^inset\(0% 0% 100% 0%/);

  const bottom = scrollEdgeGeometry("bottom");
  assert.equal(bottom.axis, "y");
  assert.ok(bottom.offset > 0);
  assert.equal(bottom.origin, "bottom center");
  assert.match(bottom.clipClosed, /^inset\(100% 0% 0% 0%/);

  const left = scrollEdgeGeometry("left");
  assert.equal(left.axis, "x");
  assert.ok(left.offset < 0);
  assert.equal(left.origin, "left center");
  assert.match(left.clipClosed, /^inset\(0% 100% 0% 0%/);

  const right = scrollEdgeGeometry("right");
  assert.equal(right.axis, "x");
  assert.ok(right.offset > 0);
  assert.equal(right.origin, "right center");
  assert.match(right.clipClosed, /^inset\(0% 0% 0% 100%/);
});

test("staggerDelay increases monotonically from non-negative indices and respects cap", () => {
  assert.equal(staggerDelay(-2, 0.02, 0.04, 0.2), 0.02);
  assert.equal(staggerDelay(0, 0.02, 0.04, 0.2), 0.02);
  assert.ok(
    staggerDelay(1, 0.02, 0.04, 0.2) > staggerDelay(0, 0.02, 0.04, 0.2),
  );
  assert.equal(staggerDelay(20, 0.02, 0.04, 0.2), 0.2);
});

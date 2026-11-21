import { test } from "node:test";
import assert from "node:assert/strict";
import { normalizeAnimation, totalMs, wire, CELLS } from "./animation.ts";

test("accepts Plaude rows and normalizes to 169 chars, off outside the circle", () => {
  const rows = Array.from({ length: 13 }, () => "#############");
  const a = normalizeAnimation({ frames: [rows, "+".repeat(169)], fps: 10, repeat: 3 });
  assert.equal(a.frames[0].length, CELLS);
  assert.equal(a.frames[0][0], "."); // esquina: no existe
  assert.equal(a.frames[0][6 * 13 + 6], "9");
  assert.equal(a.frames[1][6 * 13 + 6], "5");
  assert.equal(a.frameMs, 100);
  assert.equal(totalMs(a), 600);
});

test("rejects bad input and clamps numbers", () => {
  assert.throws(() => normalizeAnimation({ frames: [] }), /cuadros/);
  assert.throws(() => normalizeAnimation({ frames: ["###"] }), /169/);
  const a = normalizeAnimation({ frames: [".".repeat(169)], frameMs: 1, repeat: 999, brightness: 150 });
  assert.deepEqual([a.frameMs, a.repeat, a.brightness], [40, 50, 100]);
});

test("a 15-frame animation fits in an FCM data message", () => {
  const a = normalizeAnimation({ frames: Array.from({ length: 15 }, () => "9".repeat(169)) });
  assert.ok(wire(a).length < 3500);
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { isQuiet } from "./quiet.ts";

const tz = "America/Argentina/Buenos_Aires"; // UTC-3
const at = (utc: string) => new Date(`2026-11-21T${utc}:00Z`);

test("overnight range crosses midnight in local time", () => {
  assert.ok(isQuiet(at("03:30"), "23:00", "08:00", tz)); // 00:30 local
  assert.ok(isQuiet(at("02:00"), "23:00", "08:00", tz)); // 23:00 local
  assert.ok(!isQuiet(at("11:00"), "23:00", "08:00", tz)); // 08:00 local
  assert.ok(!isQuiet(at("18:00"), "23:00", "08:00", tz));
});

test("same-day range and empty range", () => {
  assert.ok(isQuiet(at("17:00"), "13:00", "15:00", tz)); // 14:00 local
  assert.ok(!isQuiet(at("17:00"), "10:00", "10:00", tz));
});

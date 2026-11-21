import { test } from "node:test";
import assert from "node:assert/strict";
import { matchCondition, pickRule, type EventView, type Rule } from "./rules.ts";

const push: EventView = {
  source_id: "gh",
  source: "GitHub",
  method: "POST",
  headers: { "x-github-event": "push" },
  query: {},
  body: { ref: "refs/heads/main", commits: [{ id: "a" }, { id: "b" }], repository: { full_name: "n/claude-mods" } },
};
const pr: EventView = {
  ...push,
  headers: { "x-github-event": "pull_request" },
  body: { action: "opened", pull_request: { draft: false, labels: ["bug", "ui"] } },
};
const linear: EventView = { ...push, source_id: "lin", source: "Linear", headers: {}, body: { type: "Issue", action: "create" } };

const rule = (id: string, priority: number, extra: Partial<Rule> = {}): Rule => ({
  id,
  name: id,
  enabled: true,
  priority,
  source_id: null,
  conditions: [],
  ...extra,
});

test("conditions read headers, nested body fields and arrays", () => {
  assert.ok(matchCondition(push, { field: "headers.X-GitHub-Event", op: "equals", value: "push" }));
  assert.ok(matchCondition(push, { field: "body.repository.full_name", op: "contains", value: "CLAUDE" }));
  assert.ok(matchCondition(push, { field: "body.commits.1.id", op: "equals", value: "b" }));
  assert.ok(matchCondition(push, { field: "body.ref", op: "regex", value: "/main$" }));
  assert.ok(matchCondition(pr, { field: "body.pull_request.labels", op: "contains", value: "bug" }));
  assert.ok(matchCondition(pr, { field: "body.pull_request.draft", op: "equals", value: "false" }));
  assert.ok(matchCondition(pr, { field: "body.action", op: "in", value: "opened, reopened" }));
  assert.ok(matchCondition(pr, { field: "body.merged", op: "not_exists" }));
  assert.ok(!matchCondition(pr, { field: "body.nope", op: "equals", value: "" }));
  assert.ok(!matchCondition(pr, { field: "body.action", op: "regex", value: "(" }));
});

test("the highest priority rule wins across the whole batch", () => {
  const rules = [
    rule("push", 1, { conditions: [{ field: "headers.x-github-event", op: "equals", value: "push" }] }),
    rule("pr", 5, { conditions: [{ field: "body.action", op: "equals", value: "opened" }] }),
    rule("linear", 3, { source_id: "lin" }),
  ];
  assert.equal(pickRule(rules, [push, push, pr, push])?.rule.id, "pr");
  assert.equal(pickRule(rules, [push, linear])?.rule.id, "linear");
  assert.equal(pickRule(rules, [push])?.matched, 1);
  assert.equal(pickRule(rules.slice(0, 1), [linear]), null);
});

test("disabled rules and other sources never match", () => {
  assert.equal(pickRule([rule("off", 9, { enabled: false }), rule("gh", 0, { source_id: "gh" })], [linear]), null);
});

// Reglas: deciden qué animación corresponde a un evento crudo. Sin dependencias, para poder testearlo.

export type Op =
  | "equals"
  | "not_equals"
  | "contains"
  | "starts_with"
  | "in"
  | "exists"
  | "not_exists"
  | "regex";

export const OPS: { op: Op; label: string; needsValue: boolean }[] = [
  { op: "equals", label: "es igual a", needsValue: true },
  { op: "not_equals", label: "no es igual a", needsValue: true },
  { op: "contains", label: "contiene", needsValue: true },
  { op: "starts_with", label: "empieza con", needsValue: true },
  { op: "in", label: "es uno de (separados por coma)", needsValue: true },
  { op: "exists", label: "existe", needsValue: false },
  { op: "not_exists", label: "no existe", needsValue: false },
  { op: "regex", label: "cumple la regex", needsValue: true },
];

export interface Condition {
  field: string;
  op: Op;
  value?: string;
}

export interface Rule {
  id: string;
  name: string;
  enabled: boolean;
  priority: number;
  source_id: string | null;
  conditions: Condition[];
}

/** Lo que ven las reglas de un evento: campos como "body.action" o "headers.x-github-event". */
export interface EventView {
  source_id: string | null;
  source: string | null;
  method: string | null;
  headers: Record<string, unknown>;
  query: Record<string, unknown>;
  body: unknown;
}

export function getPath(obj: unknown, path: string): unknown {
  let cur: unknown = obj;
  for (const part of path.split(".").filter(Boolean)) {
    if (cur === null || cur === undefined) return undefined;
    if (Array.isArray(cur)) {
      const i = Number(part);
      cur = Number.isInteger(i) ? cur[i] : undefined;
    } else if (typeof cur === "object") {
      cur = (cur as Record<string, unknown>)[part];
    } else {
      return undefined;
    }
  }
  return cur;
}

function asText(v: unknown): string {
  if (v === null || v === undefined) return "";
  if (typeof v === "object") return JSON.stringify(v);
  return String(v);
}

export function matchCondition(ev: EventView, c: Condition): boolean {
  const field = c.field.trim();
  // Los headers llegan en minúsculas.
  const path = field.startsWith("headers.") ? field.toLowerCase() : field;
  const v = getPath(ev, path);
  const want = (c.value ?? "").trim();
  switch (c.op) {
    case "exists":
      return v !== undefined && v !== null;
    case "not_exists":
      return v === undefined || v === null;
    case "equals":
      return v !== undefined && asText(v) === want;
    case "not_equals":
      return asText(v) !== want;
    case "contains":
      if (Array.isArray(v)) return v.some((x) => asText(x) === want);
      return asText(v).toLowerCase().includes(want.toLowerCase());
    case "starts_with":
      return v !== undefined && asText(v).startsWith(want);
    case "in":
      return v !== undefined && want.split(",").map((s) => s.trim()).includes(asText(v));
    case "regex":
      try {
        return v !== undefined && new RegExp(want).test(asText(v));
      } catch {
        return false;
      }
    default:
      return false;
  }
}

export function ruleMatches(rule: Rule, ev: EventView): boolean {
  if (!rule.enabled) return false;
  if (rule.source_id && rule.source_id !== ev.source_id) return false;
  return (rule.conditions ?? []).every((c) => matchCondition(ev, c));
}

/**
 * Resuelve un lote: de todas las reglas que matchean algún evento, gana la de mayor prioridad.
 * A igual prioridad gana la que matcheó el evento más reciente (los eventos vienen en orden).
 */
export function pickRule<R extends Rule>(rules: R[], events: EventView[]): { rule: R; matched: number } | null {
  let best: R | null = null;
  let bestAt = -1;
  let matched = 0;
  events.forEach((ev, i) => {
    let any = false;
    for (const r of rules) {
      if (!ruleMatches(r, ev)) continue;
      any = true;
      if (!best || r.priority > best.priority || (r.priority === best.priority && i >= bestAt)) {
        best = r;
        bestAt = i;
      }
    }
    if (any) matched++;
  });
  return best ? { rule: best, matched } : null;
}

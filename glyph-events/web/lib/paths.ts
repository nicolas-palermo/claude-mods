/** Todos los caminos "a.b.c" con valor simple de un objeto, para sugerir campos en las reglas. */
export function leafPaths(obj: unknown, prefix = "", out: Map<string, string> = new Map(), depth = 0): Map<string, string> {
  if (out.size > 300 || depth > 6) return out;
  if (obj !== null && typeof obj === "object") {
    const entries = Array.isArray(obj) ? obj.slice(0, 3).map((v, i) => [String(i), v] as const) : Object.entries(obj);
    for (const [k, v] of entries) leafPaths(v, prefix ? `${prefix}.${k}` : k, out, depth + 1);
  } else if (prefix && !out.has(prefix)) {
    out.set(prefix, obj === null || obj === undefined ? "" : String(obj).slice(0, 60));
  }
  return out;
}

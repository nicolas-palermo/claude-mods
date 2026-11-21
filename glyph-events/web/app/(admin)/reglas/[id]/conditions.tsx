"use client";

import { useState } from "react";
import { OPS, type Condition } from "@/lib/rules";

export default function Conditions({ initial, fields }: { initial: Condition[]; fields: [string, string][] }) {
  const [list, setList] = useState<Condition[]>(initial);
  const update = (i: number, patch: Partial<Condition>) => setList((l) => l.map((c, j) => (j === i ? { ...c, ...patch } : c)));
  return (
    <div>
      <input type="hidden" name="conditions" value={JSON.stringify(list)} />
      <datalist id="fields">
        {fields.map(([path, sample]) => (
          <option key={path} value={path}>{sample}</option>
        ))}
      </datalist>
      {list.map((c, i) => {
        const needs = OPS.find((o) => o.op === c.op)?.needsValue ?? true;
        return (
          <div key={i} className="row" style={{ marginBottom: 6 }}>
            <input list="fields" placeholder="campo, ej. body.action" value={c.field} onChange={(e) => update(i, { field: e.target.value })} className="grow mono" style={{ minWidth: 160 }} />
            <select value={c.op} onChange={(e) => update(i, { op: e.target.value as Condition["op"] })}>
              {OPS.map((o) => (
                <option key={o.op} value={o.op}>{o.label}</option>
              ))}
            </select>
            {needs && <input placeholder="valor" value={c.value ?? ""} onChange={(e) => update(i, { value: e.target.value })} className="mono" style={{ width: 140 }} />}
            <button type="button" className="danger" onClick={() => setList((l) => l.filter((_, j) => j !== i))}>×</button>
          </div>
        );
      })}
      <button type="button" onClick={() => setList((l) => [...l, { field: "", op: "equals", value: "" }])}>+ Condición</button>
      <p className="muted small">
        Campos: <code>headers.x-github-event</code>, <code>body.action</code>, <code>body.repository.full_name</code>,{" "}
        <code>query.algo</code>, <code>source</code>. Los números de un array van como <code>body.commits.0.id</code>.
      </p>
    </div>
  );
}

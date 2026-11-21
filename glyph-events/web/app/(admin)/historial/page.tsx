import { db, getSettings } from "@/lib/db";
import { flushDue } from "@/lib/engine";
import { STATUS, when } from "@/lib/fmt";

interface Ev {
  id: string;
  received_at: string;
  source_name: string | null;
  headers: Record<string, unknown> | null;
  body: unknown;
  batch_id: string | null;
}

export default async function History() {
  await flushDue();
  const s = await getSettings();
  const [batches, plays] = await Promise.all([
    db().from("ge_batches").select("*").order("opened_at", { ascending: false }).limit(40),
    db().from("ge_plays").select("*").order("created_at", { ascending: false }).limit(60),
  ]);
  const ids = (batches.data ?? []).map((b) => b.id);
  const events = ids.length
    ? (((await db().from("ge_events").select("id, received_at, source_name, headers, body, batch_id").in("batch_id", ids).order("received_at")).data ?? []) as Ev[])
    : [];
  const byBatch = new Map<string, Ev[]>();
  for (const e of events) byBatch.set(e.batch_id!, [...(byBatch.get(e.batch_id!) ?? []), e]);
  const playById = new Map((plays.data ?? []).map((p) => [p.id, p]));
  const tests = (plays.data ?? []).filter((p) => p.kind === "test").slice(0, 10);

  const pill = (status: string) => {
    const [label, cls] = STATUS[status] ?? [status, ""];
    return <span className={`pill ${cls}`}>{label}</span>;
  };
  const kind = (e: Ev) => {
    const h = e.headers ?? {};
    const b = (e.body ?? {}) as Record<string, unknown>;
    return [h["x-github-event"], b.type, b.action].filter(Boolean).join(" · ");
  };

  return (
    <>
      <h1>Historial</h1>
      <h2>Lotes de eventos</h2>
      {(batches.data ?? []).length === 0 && <p className="muted">Todavía no llegó ningún evento.</p>}
      {(batches.data ?? []).map((b) => {
        const evs = byBatch.get(b.id) ?? [];
        const p = b.play_id ? playById.get(b.play_id) : null;
        return (
          <details key={b.id} className="card">
            <summary>
              <span className="small muted">{when(b.opened_at, s.timezone)}</span> · {evs.length} evento(s){" "}
              {b.status !== "done" ? <span className="pill warn">agrupando</span> : p ? pill(p.status) : <span className="pill">sin regla</span>}
              {p && <span className="small"> {p.rule_name} → {p.animation_name}</span>}
            </summary>
            <p className="small muted">{b.result ?? "Esperando que cierre la ventana de agrupamiento."}</p>
            {p?.detail && <p className="small muted">Detalle: {p.detail}</p>}
            {evs.map((e) => (
              <details key={e.id} style={{ marginTop: 6 }}>
                <summary className="small">{when(e.received_at, s.timezone)} · {e.source_name} {kind(e) && <span className="muted">· {kind(e)}</span>}</summary>
                <pre>{JSON.stringify({ headers: e.headers, body: e.body }, null, 2).slice(0, 20000)}</pre>
              </details>
            ))}
          </details>
        );
      })}

      <h2>Pruebas</h2>
      {tests.length === 0 && <p className="muted">Ninguna.</p>}
      <table>
        <tbody>
          {tests.map((p) => (
            <tr key={p.id}>
              <td className="small muted">{when(p.created_at, s.timezone)}</td>
              <td>{p.animation_name}</td>
              <td>{pill(p.status)} {p.detail && <span className="small muted">{p.detail}</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}

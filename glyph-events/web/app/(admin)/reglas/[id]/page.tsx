import { notFound } from "next/navigation";
import { db, getSettings } from "@/lib/db";
import { toView } from "@/lib/engine";
import { ruleMatches, type Rule } from "@/lib/rules";
import { leafPaths } from "@/lib/paths";
import { when } from "@/lib/fmt";
import { deleteRule, saveRule } from "../../../actions";
import Conditions from "./conditions";

export default async function RulePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const rule = (await db().from("ge_rules").select("*").eq("id", id).maybeSingle()).data as
    | (Rule & { animation_id: string; sound_id: string | null })
    | null;
  if (!rule) notFound();
  const [s, anims, sounds, sources, events] = await Promise.all([
    getSettings(),
    db().from("ge_animations").select("id, name").order("name"),
    db().from("ge_sounds").select("id, name").order("name"),
    db().from("ge_sources").select("id, name").order("name"),
    db().from("ge_events").select("*").order("received_at", { ascending: false }).limit(15),
  ]);
  const evs = (events.data ?? []) as (Parameters<typeof toView>[0] & { id: string; received_at: string })[];
  const fields = new Map<string, string>();
  for (const e of evs.filter((e) => !rule.source_id || e.source_id === rule.source_id).slice(0, 5)) {
    const v = toView(e);
    leafPaths({ headers: v.headers, query: v.query, body: v.body, source: v.source, method: v.method }, "", fields);
  }

  return (
    <>
      <h1>{rule.name}</h1>
      <form action={saveRule} className="card">
        <input type="hidden" name="id" value={rule.id} />
        <label>Nombre</label>
        <input name="name" defaultValue={rule.name} style={{ width: "100%" }} />
        <div className="row">
          <div>
            <label>Prioridad (más alta gana)</label>
            <input name="priority" type="number" defaultValue={rule.priority} style={{ width: 110 }} />
          </div>
          <div>
            <label>Activa</label>
            <input name="enabled" type="checkbox" defaultChecked={rule.enabled} />
          </div>
        </div>
        <label>Fuente</label>
        <select name="source_id" defaultValue={rule.source_id ?? ""}>
          <option value="">Cualquier fuente</option>
          {(sources.data ?? []).map((x) => (
            <option key={x.id} value={x.id}>{x.name}</option>
          ))}
        </select>
        <label>Condiciones (todas tienen que cumplirse; sin condiciones, matchea cualquier evento de la fuente)</label>
        <Conditions initial={rule.conditions ?? []} fields={[...fields.entries()]} />
        <div className="row">
          <div>
            <label>Animación</label>
            <select name="animation_id" defaultValue={rule.animation_id}>
              {(anims.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>{a.name}</option>
              ))}
            </select>
          </div>
          <div>
            <label>Sonido</label>
            <select name="sound_id" defaultValue={rule.sound_id ?? ""}>
              <option value="">Sin sonido</option>
              {(sounds.data ?? []).map((x) => (
                <option key={x.id} value={x.id}>{x.name}</option>
              ))}
            </select>
          </div>
        </div>
        <div className="row" style={{ marginTop: 14 }}>
          <button className="primary">Guardar</button>
        </div>
      </form>

      <h2>Últimos eventos contra esta regla</h2>
      <p className="muted small">Según la versión guardada. Abrí uno para ver sus campos.</p>
      {evs.length === 0 && <p className="muted">Todavía no llegó ningún evento.</p>}
      {evs.map((e) => {
        const v = toView(e);
        const ok = ruleMatches({ ...rule, enabled: true }, v);
        return (
          <details key={e.id} className="card">
            <summary>
              <span className={`pill ${ok ? "ok" : ""}`}>{ok ? "matchea" : "no"}</span>{" "}
              <span className="small">{e.source_name}</span>{" "}
              <span className="small muted">{when(e.received_at, s.timezone)} · {String(v.headers["x-github-event"] ?? (v.body as Record<string, unknown> | null)?.type ?? "")}</span>
            </summary>
            <pre>{JSON.stringify({ headers: v.headers, query: v.query, body: v.body }, null, 2).slice(0, 20000)}</pre>
          </details>
        );
      })}

      <form action={deleteRule} style={{ marginTop: 24 }}>
        <input type="hidden" name="id" value={rule.id} />
        <button className="danger">Borrar regla</button>
      </form>
    </>
  );
}

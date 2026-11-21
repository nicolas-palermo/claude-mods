import Link from "next/link";
import { db } from "@/lib/db";
import { createRule, toggleRule } from "../../actions";

export default async function Rules() {
  const [rules, anims, sources] = await Promise.all([
    db().from("ge_rules").select("*").order("priority", { ascending: false }).order("name"),
    db().from("ge_animations").select("id, name").order("name"),
    db().from("ge_sources").select("id, name"),
  ]);
  const animName = new Map((anims.data ?? []).map((a) => [a.id, a.name]));
  const srcName = new Map((sources.data ?? []).map((s) => [s.id, s.name]));
  return (
    <>
      <h1>Reglas</h1>
      <p className="muted">
        Cada regla mira el evento crudo y elige una animación y un sonido. Si en un lote matchean varias, gana la de mayor
        prioridad. Se editan acá y aplican en el momento.
      </p>
      {(rules.data ?? []).map((r) => (
        <div key={r.id} className="card row">
          <span className="pill" title="prioridad">{r.priority}</span>
          <Link href={`/reglas/${r.id}`} className="grow" style={{ textDecoration: "none" }}>
            <strong style={{ opacity: r.enabled ? 1 : 0.5 }}>{r.name}</strong>
            <div className="muted small">
              {r.source_id ? srcName.get(r.source_id) ?? "?" : "Cualquier fuente"} · {(r.conditions ?? []).length} condición(es) →{" "}
              {animName.get(r.animation_id) ?? "?"}
            </div>
          </Link>
          <form action={toggleRule}>
            <input type="hidden" name="id" value={r.id} />
            <input type="hidden" name="enabled" value={String(!r.enabled)} />
            <button className="small">{r.enabled ? "Pausar" : "Activar"}</button>
          </form>
        </div>
      ))}
      {(anims.data ?? []).length === 0 ? (
        <p className="muted">Para crear una regla primero necesitás una <Link href="/animaciones">animación</Link>.</p>
      ) : (
        <form action={createRule} className="card row">
          <input name="name" placeholder="Nombre (por ejemplo, PR abierto)" className="grow" />
          <input type="hidden" name="animation_id" value={anims.data![0].id} />
          <button className="primary">Nueva regla</button>
        </form>
      )}
    </>
  );
}

import Link from "next/link";
import { headers } from "next/headers";
import { db, getSettings } from "@/lib/db";
import { flushDue } from "@/lib/engine";
import { isQuiet } from "@/lib/quiet";
import { STATUS, when } from "@/lib/fmt";
import { rotateDeviceKey } from "../actions";
import TestButton from "./test-button";

export default async function Home() {
  await flushDue();
  const s = await getSettings();
  const h = await headers();
  const origin = `${h.get("x-forwarded-proto") ?? "https"}://${h.get("x-forwarded-host") ?? h.get("host")}`;
  const pairQuery = `u=${encodeURIComponent(origin)}&k=${s.device_key}`;
  const intent = `intent://pair?${pairQuery}#Intent;scheme=plaude;package=com.plaude.glyph;end`;
  const [plays, anims, sounds, counts] = await Promise.all([
    db().from("ge_plays").select("*").order("created_at", { ascending: false }).limit(5),
    db().from("ge_animations").select("id, name").order("name"),
    db().from("ge_sounds").select("id, name").order("name"),
    Promise.all(["ge_rules", "ge_sources", "ge_animations"].map((t) => db().from(t).select("id", { count: "exact", head: true }))),
  ]);
  const quietNow = s.quiet_enabled && isQuiet(new Date(), s.quiet_start, s.quiet_end, s.timezone);
  const [nRules, nSources, nAnims] = counts.map((c) => c.count ?? 0);

  return (
    <>
      <h1>Glyph Events</h1>

      <div className="card">
        <div className="row">
          <strong className="grow">Teléfono</strong>
          {s.fcm_token ? <span className="pill ok">vinculado</span> : <span className="pill bad">sin vincular</span>}
          {quietNow && <span className="pill warn">en silencio</span>}
        </div>
        {s.fcm_token ? (
          <p className="muted small">
            {s.device_model ?? "Android"} · Plaude Glyph {s.app_version ?? "?"} · desde {when(s.device_registered_at, s.timezone)}
          </p>
        ) : (
          <p className="muted small">
            Abrí esta página en el teléfono, con Plaude Glyph instalado, y tocá el botón.
          </p>
        )}
        <div className="row">
          <a className="btn primary" href={intent}>Vincular este teléfono</a>
          <details>
            <summary className="small muted">Otras opciones</summary>
            <p className="small muted">Link directo (si el botón no abre la app):</p>
            <p className="mono copy small">plaude://pair?{pairQuery}</p>
            <form action={rotateDeviceKey}>
              <button className="danger small">Cambiar la clave del teléfono (desvincula)</button>
            </form>
          </details>
        </div>
      </div>

      <div className="card">
        <strong>Probar</strong>
        <p className="muted small">Manda una animación al teléfono ya, aunque sea horario de silencio.</p>
        <TestButton animations={(anims.data ?? []) as { id: string; name: string }[]} sounds={(sounds.data ?? []) as { id: string; name: string }[]} />
      </div>

      {(nSources === 0 || nAnims === 0 || nRules === 0) && (
        <div className="card">
          <strong>Para empezar</strong>
          <ol className="small">
            <li className={nSources ? "muted" : ""}>Creá una <Link href="/fuentes">fuente</Link> y pegá su URL en el servicio (GitHub, Linear…).</li>
            <li className={nAnims ? "muted" : ""}>Dibujá una <Link href="/animaciones">animación</Link>.</li>
            <li className={nRules ? "muted" : ""}>Armá una <Link href="/reglas">regla</Link> que una el evento con la animación.</li>
          </ol>
        </div>
      )}

      <h2>Últimas reproducciones</h2>
      {(plays.data ?? []).length === 0 && <p className="muted">Todavía nada.</p>}
      <table>
        <tbody>
          {(plays.data ?? []).map((p) => {
            const [label, cls] = STATUS[p.status] ?? [p.status, ""];
            return (
              <tr key={p.id}>
                <td className="small muted">{when(p.created_at, s.timezone)}</td>
                <td>{p.rule_name ?? (p.kind === "test" ? "Prueba" : "—")} <span className="muted small">· {p.animation_name ?? "?"}</span></td>
                <td><span className={`pill ${cls}`}>{label}</span></td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p><Link href="/historial" className="small">Ver todo el historial</Link></p>
    </>
  );
}

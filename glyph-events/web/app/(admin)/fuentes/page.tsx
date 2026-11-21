import { headers } from "next/headers";
import { db, getSettings } from "@/lib/db";
import { when } from "@/lib/fmt";
import { createSource, deleteSource, renameSource, rotateSource } from "../../actions";

export default async function Sources() {
  const s = await getSettings();
  const h = await headers();
  const origin = `${h.get("x-forwarded-proto") ?? "https"}://${h.get("x-forwarded-host") ?? h.get("host")}`;
  const sources = ((await db().from("ge_sources").select("*").order("created_at")).data ?? []) as {
    id: string;
    name: string;
    token: string;
  }[];
  const last = new Map<string, string>();
  for (const src of sources) {
    const e = (await db().from("ge_events").select("received_at").eq("source_id", src.id).order("received_at", { ascending: false }).limit(1)).data?.[0];
    if (e) last.set(src.id, e.received_at);
  }

  return (
    <>
      <h1>Fuentes</h1>
      <p className="muted">
        Cada servicio que manda webhooks tiene su URL. El token de la URL es el secreto: no se validan firmas, así funciona con
        cualquier servicio. Mandá el evento crudo (JSON, formulario o texto) por POST.
      </p>
      {sources.map((src) => {
        const url = `${origin}/api/hook/${src.token}`;
        return (
          <div key={src.id} className="card">
            <form action={renameSource} className="row">
              <input type="hidden" name="id" value={src.id} />
              <input name="name" defaultValue={src.name} className="grow" />
              <button>Guardar</button>
            </form>
            <p className="mono copy small" style={{ marginBottom: 4 }}>{url}</p>
            <p className="muted small" style={{ marginTop: 0 }}>Último evento: {when(last.get(src.id), s.timezone)}</p>
            <div className="row">
              <form action={rotateSource}>
                <input type="hidden" name="id" value={src.id} />
                <button className="small">Cambiar token</button>
              </form>
              <form action={deleteSource}>
                <input type="hidden" name="id" value={src.id} />
                <button className="small danger">Borrar</button>
              </form>
            </div>
          </div>
        );
      })}
      <form action={createSource} className="card row">
        <input name="name" placeholder="Nombre (GitHub, Linear, mi backend…)" className="grow" />
        <button className="primary">Crear fuente</button>
      </form>
      <details className="card">
        <summary>Cómo conectar servicios</summary>
        <ul className="small">
          <li><b>GitHub:</b> repo o organización › Settings › Webhooks › Add webhook. Payload URL = la URL de arriba, Content type <code>application/json</code>, sin secret. El tipo de evento llega en <code>headers.x-github-event</code>.</li>
          <li><b>Linear:</b> Settings › API › Webhooks › New webhook. El tipo llega en <code>body.type</code> y la acción en <code>body.action</code>.</li>
          <li><b>Tu backend o un script:</b> <code>curl -X POST -H &apos;content-type: application/json&apos; -d &apos;&#123;&quot;evento&quot;:&quot;deploy&quot;&#125;&apos; URL</code></li>
        </ul>
      </details>
    </>
  );
}

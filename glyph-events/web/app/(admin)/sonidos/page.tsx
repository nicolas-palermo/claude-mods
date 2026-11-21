import { db, SOUND_BUCKET } from "@/lib/db";
import { deleteSound } from "../../actions";
import Upload from "./upload";

export default async function Sounds() {
  const sounds = ((await db().from("ge_sounds").select("*").order("name")).data ?? []) as {
    id: string;
    name: string;
    path: string;
    size: number | null;
  }[];
  const urls = new Map<string, string>();
  if (sounds.length) {
    const signed = await db().storage.from(SOUND_BUCKET).createSignedUrls(sounds.map((s) => s.path), 3600);
    for (const x of signed.data ?? []) if (x.path && x.signedUrl) urls.set(x.path, x.signedUrl);
  }
  return (
    <>
      <h1>Sonidos</h1>
      <p className="muted">
        Se guardan en Supabase Storage. El teléfono baja cada sonido una sola vez y lo deja en caché. Suena con el volumen de
        notificaciones, así que respeta el modo silencio del teléfono.
      </p>
      {sounds.map((s) => (
        <div key={s.id} className="card">
          <div className="row">
            <strong className="grow">{s.name}</strong>
            <span className="muted small">{s.size ? `${Math.round(s.size / 1024)} KB` : ""}</span>
            <form action={deleteSound}>
              <input type="hidden" name="id" value={s.id} />
              <button className="small danger">Borrar</button>
            </form>
          </div>
          {urls.get(s.path) && <audio controls preload="none" src={urls.get(s.path)} style={{ width: "100%", marginTop: 8 }} />}
        </div>
      ))}
      <Upload />
    </>
  );
}

import Link from "next/link";
import { db } from "@/lib/db";
import { Mini } from "@/components/mini";
import { createAnimation } from "../../actions";

export default async function Animations() {
  const anims = ((await db().from("ge_animations").select("id, name, data").order("name")).data ?? []) as {
    id: string;
    name: string;
    data: { frames: string[]; frameMs: number; repeat: number };
  }[];
  return (
    <>
      <h1>Animaciones</h1>
      <p className="muted">
        Se dibujan en la matriz redonda de 13×13 del Phone (4a) Pro. El teléfono las recibe completas en el push, así que
        cambiarlas no requiere actualizar la app.
      </p>
      {anims.map((a) => (
        <Link key={a.id} href={`/animaciones/${a.id}`} className="card row" style={{ textDecoration: "none" }}>
          <Mini frame={a.data.frames?.[0] ?? ""} />
          <div className="grow">
            <strong>{a.name}</strong>
            <div className="muted small">
              {a.data.frames?.length ?? 0} cuadro(s) · {a.data.frameMs} ms · ×{a.data.repeat}
            </div>
          </div>
        </Link>
      ))}
      <form action={createAnimation} className="card row">
        <input name="name" placeholder="Nombre" className="grow" />
        <button className="primary">Nueva animación</button>
      </form>
    </>
  );
}

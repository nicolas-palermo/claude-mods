"use client";

import { useActionState } from "react";
import { uploadSound } from "../../actions";

export default function Upload() {
  const [error, action, pending] = useActionState(uploadSound, null);
  return (
    <form action={action} className="card">
      <strong>Subir sonido</strong>
      <label>Archivo (mp3, ogg, wav, m4a; hasta 3,5 MB)</label>
      <input type="file" name="file" accept="audio/*" required />
      <label>Nombre</label>
      <input name="name" placeholder="Opcional" style={{ width: "100%" }} />
      <div className="row" style={{ marginTop: 12 }}>
        <button className="primary" disabled={pending}>{pending ? "Subiendo…" : "Subir"}</button>
        {error && <span className="pill bad">{error}</span>}
      </div>
    </form>
  );
}

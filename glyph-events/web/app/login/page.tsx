"use client";

import { useActionState } from "react";
import { login } from "../actions";

export default function Login() {
  const [error, action, pending] = useActionState(login, null);
  return (
    <main style={{ maxWidth: 360, paddingTop: 80 }}>
      <h1>Glyph Events</h1>
      <form action={action} className="card">
        <label htmlFor="password">Contraseña</label>
        <input id="password" name="password" type="password" autoFocus style={{ width: "100%" }} />
        <div className="row" style={{ marginTop: 12 }}>
          <button className="primary" disabled={pending}>Entrar</button>
          {error && <span className="pill bad">{error}</span>}
        </div>
      </form>
    </main>
  );
}

"use client";

import { useState, useTransition } from "react";
import { testAnimation } from "../actions";

export default function TestButton({
  animations,
  sounds,
  fixedAnimation,
}: {
  animations?: { id: string; name: string }[];
  sounds: { id: string; name: string }[];
  fixedAnimation?: string;
}) {
  const [anim, setAnim] = useState(fixedAnimation ?? animations?.[0]?.id ?? "");
  const [sound, setSound] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [pending, start] = useTransition();
  if (!fixedAnimation && !animations?.length) return <p className="muted small">Primero creá una animación.</p>;
  return (
    <div className="row">
      {!fixedAnimation && (
        <select value={anim} onChange={(e) => setAnim(e.target.value)}>
          {animations!.map((a) => (
            <option key={a.id} value={a.id}>{a.name}</option>
          ))}
        </select>
      )}
      <select value={sound} onChange={(e) => setSound(e.target.value)}>
        <option value="">Sin sonido</option>
        {sounds.map((s) => (
          <option key={s.id} value={s.id}>{s.name}</option>
        ))}
      </select>
      <button
        className="primary"
        disabled={pending || !anim}
        onClick={() => start(async () => setMsg(await testAnimation(anim, sound || null)))}
      >
        {pending ? "Enviando…" : "Probar en el teléfono"}
      </button>
      {msg && <span className="small muted">{msg}</span>}
    </div>
  );
}

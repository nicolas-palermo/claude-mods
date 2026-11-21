"use client";

import { useEffect, useRef, useState, useTransition } from "react";
import { CELLS, exists, normalizeAnimation, totalMs, type Animation } from "@/lib/animation";
import { ledColor, Mini } from "@/components/mini";
import { deleteAnimation, saveAnimation, testAnimation } from "../../../actions";

const LEVELS = [
  { ch: ".", label: "Apagar" },
  { ch: "3", label: "Bajo" },
  { ch: "6", label: "Medio" },
  { ch: "9", label: "Lleno" },
];

const setAt = (f: string, i: number, ch: string) => f.slice(0, i) + ch + f.slice(i + 1);

export default function Editor({
  id,
  initialName,
  initial,
  sounds,
}: {
  id: string;
  initialName: string;
  initial: Animation;
  sounds: { id: string; name: string }[];
}) {
  const [name, setName] = useState(initialName);
  const [frames, setFrames] = useState<string[]>(initial.frames);
  const [frameMs, setFrameMs] = useState(initial.frameMs);
  const [repeat, setRepeat] = useState(initial.repeat);
  const [brightness, setBrightness] = useState(initial.brightness);
  const [cur, setCur] = useState(0);
  const [level, setLevel] = useState("9");
  const [playing, setPlaying] = useState(false);
  const [playFrame, setPlayFrame] = useState(0);
  const [msg, setMsg] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);
  const [sound, setSound] = useState("");
  const [importText, setImportText] = useState("");
  const [pending, start] = useTransition();
  const painting = useRef<string | null>(null);

  useEffect(() => {
    if (!playing) return;
    const t = setInterval(() => setPlayFrame((f) => (f + 1) % frames.length), frameMs);
    return () => clearInterval(t);
  }, [playing, frameMs, frames.length]);

  const shown = playing ? frames[playFrame % frames.length] : frames[cur];
  const data = (): Animation => ({ frames, frameMs, repeat, brightness });

  const edit = (fn: (fs: string[]) => string[]) => {
    setFrames((fs) => fn([...fs]));
    setDirty(true);
  };
  const paint = (i: number, ch: string) => {
    if (!exists(i % 13, Math.floor(i / 13))) return;
    edit((fs) => {
      fs[cur] = setAt(fs[cur], i, ch);
      return fs;
    });
  };
  const cellFromPoint = (x: number, y: number) => {
    const el = document.elementFromPoint(x, y) as HTMLElement | null;
    const i = el?.dataset.i;
    return i === undefined ? null : Number(i);
  };

  const save = async () => {
    const err = await saveAnimation(id, name, data());
    if (err) setMsg(`Error: ${err}`);
    else {
      setDirty(false);
      setMsg("Guardada.");
    }
    return !err;
  };

  const doImport = () => {
    try {
      let o = JSON.parse(importText);
      if (Array.isArray(o)) o = { frames: o.length === 13 && typeof o[0] === "string" ? [o] : o };
      if (o.type === "bitmap" && o.rows) o = { frames: [o.rows] };
      const a = normalizeAnimation({ frameMs, repeat, brightness, ...o });
      setFrames(a.frames);
      setFrameMs(a.frameMs);
      setRepeat(a.repeat);
      setBrightness(a.brightness);
      setCur(0);
      setDirty(true);
      setImportText("");
      setMsg(`Importados ${a.frames.length} cuadro(s). Falta guardar.`);
    } catch (e) {
      setMsg(`No pude importar: ${(e as Error).message}`);
    }
  };

  return (
    <>
      <div className="row">
        <input value={name} onChange={(e) => (setName(e.target.value), setDirty(true))} className="grow" style={{ fontSize: 18 }} />
      </div>

      <div className="row" style={{ marginTop: 12, alignItems: "flex-start" }}>
        <div
          className="matrix"
          style={{ opacity: 0.35 + (0.65 * brightness) / 100 }}
          onPointerDown={(e) => {
            if (playing) return;
            const i = cellFromPoint(e.clientX, e.clientY);
            if (i === null) return;
            // Tocar un LED que ya tiene el nivel elegido lo apaga: así se borra sin cambiar de herramienta.
            const ch = frames[cur][i] === level && level !== "." ? "." : level;
            painting.current = ch;
            (e.target as HTMLElement).releasePointerCapture?.(e.pointerId);
            paint(i, ch);
          }}
          onPointerMove={(e) => {
            if (painting.current === null) return;
            const i = cellFromPoint(e.clientX, e.clientY);
            if (i !== null) paint(i, painting.current);
          }}
          onPointerUp={() => (painting.current = null)}
          onPointerLeave={() => (painting.current = null)}
        >
          {Array.from({ length: CELLS }, (_, i) => {
            const on = exists(i % 13, Math.floor(i / 13));
            return <div key={i} data-i={i} className={`c ${on ? "" : "x"}`} style={{ background: ledColor(shown[i]) }} />;
          })}
        </div>
      </div>

      <div className="row" style={{ marginTop: 8 }}>
        {LEVELS.map((l) => (
          <button key={l.ch} className={level === l.ch ? "primary" : ""} onClick={() => setLevel(l.ch)}>
            {l.label}
          </button>
        ))}
      </div>
      <div className="row" style={{ marginTop: 8 }}>
        <button onClick={() => edit((fs) => ((fs[cur] = ".".repeat(CELLS)), fs))}>Limpiar</button>
        <button onClick={() => edit((fs) => ((fs[cur] = Array.from({ length: CELLS }, (_, i) => (exists(i % 13, Math.floor(i / 13)) ? level : ".")).join("")), fs))}>
          Llenar
        </button>
        <button onClick={() => edit((fs) => ((fs[cur] = Array.from(fs[cur], (c, i) => (!exists(i % 13, Math.floor(i / 13)) ? "." : c === "." ? "9" : ".")).join("")), fs))}>
          Invertir
        </button>
        <button onClick={() => setPlaying((p) => !p)}>{playing ? "Parar" : "▶ Vista previa"}</button>
      </div>

      <h2>Cuadros</h2>
      <div className="thumbs">
        {frames.map((f, i) => (
          <button key={i} className={`thumb ${i === cur ? "on" : ""}`} onClick={() => (setPlaying(false), setCur(i))} title={`Cuadro ${i + 1}`}>
            <Mini frame={f} />
          </button>
        ))}
      </div>
      <div className="row">
        <span className="muted small">Cuadro {cur + 1} de {frames.length}</span>
        <button disabled={frames.length >= 120} onClick={() => (edit((fs) => (fs.splice(cur + 1, 0, ".".repeat(CELLS)), fs)), setCur(cur + 1))}>+ Vacío</button>
        <button disabled={frames.length >= 120} onClick={() => (edit((fs) => (fs.splice(cur + 1, 0, fs[cur]), fs)), setCur(cur + 1))}>Duplicar</button>
        <button disabled={cur === 0} onClick={() => (edit((fs) => ([fs[cur - 1], fs[cur]] = [fs[cur], fs[cur - 1]], fs)), setCur(cur - 1))}>←</button>
        <button disabled={cur === frames.length - 1} onClick={() => (edit((fs) => ([fs[cur + 1], fs[cur]] = [fs[cur], fs[cur + 1]], fs)), setCur(cur + 1))}>→</button>
        <button className="danger" disabled={frames.length === 1} onClick={() => (edit((fs) => (fs.splice(cur, 1), fs)), setCur(Math.max(0, cur - 1)))}>Borrar cuadro</button>
      </div>

      <div className="row" style={{ marginTop: 8 }}>
        <div>
          <label>ms por cuadro</label>
          <input type="number" min={40} max={10000} value={frameMs} onChange={(e) => (setFrameMs(Number(e.target.value)), setDirty(true))} style={{ width: 100 }} />
        </div>
        <div>
          <label>Repeticiones</label>
          <input type="number" min={1} max={50} value={repeat} onChange={(e) => (setRepeat(Number(e.target.value)), setDirty(true))} style={{ width: 90 }} />
        </div>
        <div>
          <label>Brillo %</label>
          <input type="number" min={0} max={100} value={brightness} onChange={(e) => (setBrightness(Number(e.target.value)), setDirty(true))} style={{ width: 90 }} />
        </div>
      </div>
      <p className="muted small">Dura {(totalMs(data()) / 1000).toFixed(1)} s en el teléfono (máximo 30 s).</p>

      <div className="row" style={{ marginTop: 12 }}>
        <button className="primary" disabled={pending} onClick={() => start(async () => void (await save()))}>
          {dirty ? "Guardar" : "Guardado"}
        </button>
        <select value={sound} onChange={(e) => setSound(e.target.value)}>
          <option value="">Sin sonido</option>
          {sounds.map((s) => (
            <option key={s.id} value={s.id}>{s.name}</option>
          ))}
        </select>
        <button
          disabled={pending}
          onClick={() =>
            start(async () => {
              if (dirty && !(await save())) return;
              setMsg(await testAnimation(id, sound || null));
            })
          }
        >
          Probar en el teléfono
        </button>
        {msg && <span className="small muted">{msg}</span>}
      </div>

      <details className="card" style={{ marginTop: 24 }}>
        <summary>Importar o exportar JSON</summary>
        <p className="small muted">Acepta lo que exporta el editor de Plaude (13 filas con # + .) o el formato de acá.</p>
        <textarea rows={5} value={importText} onChange={(e) => setImportText(e.target.value)} className="mono" placeholder='{"frames": [["13 filas", "..."]], "frameMs": 100}' />
        <div className="row">
          <button onClick={doImport} disabled={!importText.trim()}>Importar</button>
          <button onClick={() => setImportText(JSON.stringify(data()))}>Exportar acá</button>
        </div>
      </details>

      <form action={deleteAnimation} style={{ marginTop: 24 }}>
        <input type="hidden" name="id" value={id} />
        <button className="danger">Borrar animación (y sus reglas)</button>
      </form>
    </>
  );
}

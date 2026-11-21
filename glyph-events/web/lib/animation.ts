// Formato de las animaciones (v1) para la matriz redonda de 13x13 del Phone (4a) Pro.
// Cada cuadro es un string de 169 caracteres, fila por fila: '.' apagado, '1'..'9' brillo (9 = lleno).
// También se aceptan '#' (lleno), '+' (medio) y cuadros de 13 filas, como en glyph.json de Plaude.

export const SIZE = 13;
export const CELLS = SIZE * SIZE;
export const MAX_FRAMES = 120;

export interface Animation {
  frames: string[];
  frameMs: number;
  repeat: number;
  brightness: number;
}

export function exists(x: number, y: number): boolean {
  const dx = x - 6;
  const dy = y - 6;
  return dx * dx + dy * dy <= 42.25;
}

function normChar(c: string): string {
  if (c === "#") return "9";
  if (c === "+") return "5";
  if (c >= "1" && c <= "9") return c;
  return ".";
}

/** Normaliza un cuadro (169 caracteres o 13 filas) a 169 caracteres con '.' y '1'..'9'. */
export function normalizeFrame(f: unknown): string {
  let s: string;
  if (Array.isArray(f)) {
    if (f.length !== SIZE) throw new Error(`un cuadro tiene que tener ${SIZE} filas`);
    s = f.map((r) => String(r).padEnd(SIZE, ".").slice(0, SIZE)).join("");
  } else if (typeof f === "string") {
    s = f;
  } else {
    throw new Error("cuadro inválido");
  }
  if (s.length !== CELLS) throw new Error(`un cuadro tiene que tener ${CELLS} caracteres, tiene ${s.length}`);
  let out = "";
  for (let i = 0; i < CELLS; i++) {
    out += exists(i % SIZE, Math.floor(i / SIZE)) ? normChar(s[i]) : ".";
  }
  return out;
}

const clamp = (v: unknown, lo: number, hi: number, def: number) => {
  const n = Number(v);
  return Number.isFinite(n) ? Math.max(lo, Math.min(hi, Math.round(n))) : def;
};

export function normalizeAnimation(input: unknown): Animation {
  if (!input || typeof input !== "object") throw new Error("la animación tiene que ser un objeto");
  const o = input as Record<string, unknown>;
  const raw = Array.isArray(o.frames) ? o.frames : Array.isArray(o.rows) ? [o.rows] : null;
  if (!raw || raw.length === 0) throw new Error("la animación no tiene cuadros");
  if (raw.length > MAX_FRAMES) throw new Error(`máximo ${MAX_FRAMES} cuadros`);
  const fps = Number(o.fps);
  return {
    frames: raw.map(normalizeFrame),
    frameMs: clamp(o.frameMs ?? (fps > 0 ? 1000 / fps : undefined), 40, 10000, 100),
    repeat: clamp(o.repeat, 1, 50, 1),
    brightness: clamp(o.brightness, 0, 100, 100),
  };
}

export function totalMs(a: Animation): number {
  return Math.min(30000, a.frames.length * a.frameMs * a.repeat);
}

/** Lo que viaja en el push: compacto, para entrar en los 4 KB de FCM cuando se puede. */
export function wire(a: Animation): string {
  return JSON.stringify({ v: 1, frames: a.frames, frameMs: a.frameMs, repeat: a.repeat, brightness: a.brightness });
}

export function blankFrame(): string {
  return ".".repeat(CELLS);
}

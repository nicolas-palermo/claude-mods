import { exists } from "@/lib/animation";

/** Color de un LED según su nivel ('.' o '1'..'9'). */
export function ledColor(ch: string): string {
  const lvl = ch >= "1" && ch <= "9" ? Number(ch) : 0;
  const c = Math.round(34 + (lvl / 9) * (245 - 34));
  return `rgb(${c},${c},${Math.min(255, c + 2)})`;
}

/** Miniatura de un cuadro de 169 caracteres. */
export function Mini({ frame }: { frame: string }) {
  return (
    <div className="mini">
      {Array.from({ length: 169 }, (_, i) => (
        <span
          key={i}
          style={{
            background: exists(i % 13, Math.floor(i / 13)) ? ledColor(frame[i] ?? ".") : "transparent",
          }}
        />
      ))}
    </div>
  );
}

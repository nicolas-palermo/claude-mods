export function when(iso: string | null | undefined, tz: string): string {
  if (!iso) return "—";
  return new Intl.DateTimeFormat("es-AR", {
    timeZone: tz,
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).format(new Date(iso));
}

export const STATUS: Record<string, [string, string]> = {
  sending: ["enviando", "warn"],
  sent: ["enviado", "ok"],
  played: ["reproducido", "ok"],
  silenced: ["silenciado", "warn"],
  no_device: ["sin teléfono", "bad"],
  error: ["error", "bad"],
  play_error: ["error en el teléfono", "bad"],
};

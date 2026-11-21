import { db } from "@/lib/db";
import { deviceAuthorized, unauthorized } from "@/lib/device";

export const dynamic = "force-dynamic";

/** El teléfono avisa qué hizo con un push: queda en el historial. */
export async function POST(req: Request) {
  if (!(await deviceAuthorized(req))) return unauthorized();
  const body = (await req.json().catch(() => ({}))) as { playId?: string; status?: string; detail?: string };
  if (!body.playId) return Response.json({ error: "falta playId" }, { status: 400 });
  const status = body.status === "played" ? "played" : "play_error";
  await db()
    .from("ge_plays")
    .update({ status, detail: body.detail?.slice(0, 300) ?? null, acked_at: new Date().toISOString() })
    .eq("id", body.playId);
  return Response.json({ ok: true });
}

import { deviceAuthorized, unauthorized } from "@/lib/device";
import { playPayload } from "@/lib/engine";

export const dynamic = "force-dynamic";

/** La animación completa de un push que no entraba en los 4 KB de FCM. */
export async function GET(req: Request, ctx: { params: Promise<{ id: string }> }) {
  if (!(await deviceAuthorized(req))) return unauthorized();
  const { id } = await ctx.params;
  if (!/^[0-9a-f-]{36}$/.test(id)) return Response.json({ error: "id inválido" }, { status: 400 });
  const p = await playPayload(id);
  if (!p) return Response.json({ error: "no existe" }, { status: 404 });
  return Response.json(p);
}

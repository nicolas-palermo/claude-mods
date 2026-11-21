import { db } from "@/lib/db";
import { deviceAuthorized, unauthorized } from "@/lib/device";

export const dynamic = "force-dynamic";

/** Guarda el token de FCM del teléfono (uno solo: es una app personal). */
export async function POST(req: Request) {
  if (!(await deviceAuthorized(req))) return unauthorized();
  const body = (await req.json().catch(() => ({}))) as { token?: string; model?: string; appVersion?: string };
  if (!body.token) return Response.json({ error: "falta token" }, { status: 400 });
  const { error } = await db()
    .from("ge_settings")
    .update({
      fcm_token: body.token,
      device_model: body.model?.slice(0, 80) ?? null,
      app_version: body.appVersion?.slice(0, 40) ?? null,
      device_registered_at: new Date().toISOString(),
    })
    .eq("id", 1);
  if (error) return Response.json({ error: error.message }, { status: 500 });
  return Response.json({ ok: true });
}

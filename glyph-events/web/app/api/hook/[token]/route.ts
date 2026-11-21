import { after } from "next/server";
import { db } from "@/lib/db";
import { flushDue, ingest, type Source } from "@/lib/engine";

// Endpoint genérico de webhooks: https://<tu-app>/api/hook/<token>.
// No valida firmas propias de cada servicio: el secreto es el token de la URL.

export const dynamic = "force-dynamic";
export const maxDuration = 60;

const SKIP_HEADERS = new Set(["cookie", "authorization", "x-vercel-proxy-signature", "x-vercel-oidc-token"]);

async function parseBody(req: Request): Promise<unknown> {
  const text = await req.text();
  if (!text) return null;
  const type = req.headers.get("content-type") ?? "";
  if (type.includes("application/x-www-form-urlencoded")) {
    const form = Object.fromEntries(new URLSearchParams(text));
    // GitHub en modo formulario manda el JSON en "payload".
    if (typeof form.payload === "string") {
      try {
        return JSON.parse(form.payload);
      } catch {
        // queda como formulario
      }
    }
    return form;
  }
  try {
    return JSON.parse(text);
  } catch {
    return { text: text.slice(0, 20000) };
  }
}

async function handle(req: Request, ctx: { params: Promise<{ token: string }> }) {
  const { token } = await ctx.params;
  const source = (await db().from("ge_sources").select("id, name, token").eq("token", token).maybeSingle())
    .data as Source | null;
  if (!source) return Response.json({ error: "fuente desconocida" }, { status: 404 });

  const headers: Record<string, string> = {};
  req.headers.forEach((v, k) => {
    if (!SKIP_HEADERS.has(k)) headers[k] = v;
  });
  const url = new URL(req.url);
  const batch = await ingest(source, {
    method: req.method,
    headers,
    query: Object.fromEntries(url.searchParams),
    body: req.method === "GET" || req.method === "HEAD" ? null : await parseBody(req),
  });

  // Esperamos a que cierre la ventana de agrupamiento y resolvemos el lote. Si llegan más
  // eventos, cada uno agenda lo mismo; solo el primero que llegue a tiempo lo resuelve.
  after(async () => {
    const wait = new Date(batch.deadline).getTime() - Date.now() + 250;
    if (wait > 0) await new Promise((r) => setTimeout(r, Math.min(wait, 55_000)));
    await flushDue();
  });
  return Response.json({ ok: true, batch: batch.id }, { status: 202 });
}

export { handle as POST, handle as PUT, handle as PATCH, handle as GET };

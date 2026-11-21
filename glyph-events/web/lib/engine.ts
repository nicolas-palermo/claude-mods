import "server-only";
import { db, getSettings, must, SOUND_BUCKET } from "./db";
import { sendData } from "./fcm";
import { isQuiet } from "./quiet";
import { pickRule, type EventView, type Rule } from "./rules";
import { normalizeAnimation, wire } from "./animation";

// El corazón del backend: guarda eventos, los agrupa en lotes, resuelve cada lote con las reglas
// y manda la animación ganadora al teléfono por FCM.

/** FCM acepta hasta 4 KB de datos; si la animación no entra, el teléfono la baja por su id. */
const MAX_INLINE = 3500;

export interface Source {
  id: string;
  name: string;
  token: string;
}

export interface RuleRow extends Rule {
  animation_id: string;
  sound_id: string | null;
}

interface Batch {
  id: string;
  deadline: string;
}

export async function ingest(
  source: Source,
  req: { method: string; headers: Record<string, string>; query: Record<string, string>; body: unknown },
): Promise<Batch> {
  const ev = must(
    await db()
      .from("ge_events")
      .insert({
        source_id: source.id,
        source_name: source.name,
        method: req.method,
        headers: req.headers,
        query: req.query,
        body: req.body,
      })
      .select("id")
      .single(),
  ) as { id: string };
  const settings = await getSettings();
  return must(
    await db().rpc("ge_attach_event", { p_event: ev.id, p_window: settings.batch_window_seconds }).single(),
  ) as Batch;
}

/** Resuelve los lotes que ya cerraron. Se puede llamar muchas veces: cada lote se toma una sola vez. */
export async function flushDue(): Promise<number> {
  const batches = must(await db().rpc("ge_claim_due_batches")) as Batch[];
  for (const b of batches) {
    try {
      await resolveBatch(b.id);
    } catch (e) {
      await db()
        .from("ge_batches")
        .update({ status: "done", resolved_at: new Date().toISOString(), result: `error: ${(e as Error).message}` })
        .eq("id", b.id);
    }
  }
  return batches.length;
}

export function toView(e: {
  source_id: string | null;
  source_name: string | null;
  method: string | null;
  headers: unknown;
  query: unknown;
  body: unknown;
}): EventView {
  return {
    source_id: e.source_id,
    source: e.source_name,
    method: e.method,
    headers: (e.headers as Record<string, unknown>) ?? {},
    query: (e.query as Record<string, unknown>) ?? {},
    body: e.body,
  };
}

async function resolveBatch(batchId: string) {
  const events = must(
    await db().from("ge_events").select("*").eq("batch_id", batchId).order("received_at", { ascending: true }),
  ) as Parameters<typeof toView>[0][];
  const rules = must(await db().from("ge_rules").select("*").eq("enabled", true)) as RuleRow[];
  const pick = pickRule(rules, events.map(toView));
  const done = (result: string, playId: string | null = null) =>
    db()
      .from("ge_batches")
      .update({ status: "done", resolved_at: new Date().toISOString(), result, play_id: playId })
      .eq("id", batchId);
  const n = events.length;
  if (!pick) {
    await done(`${n} evento(s), ninguna regla`);
    return;
  }
  const sent = await play({
    kind: "rule",
    animationId: pick.rule.animation_id,
    soundId: pick.rule.sound_id,
    ruleId: pick.rule.id,
    ruleName: pick.rule.name,
    batchId,
  });
  await done(`${n} evento(s), ${pick.matched} con regla; ganó "${pick.rule.name}" (prioridad ${pick.rule.priority})`, sent.id);
}

async function soundUrl(soundId: string): Promise<{ url: string; key: string } | null> {
  const s = (await db().from("ge_sounds").select("id, path").eq("id", soundId).maybeSingle()).data as
    | { id: string; path: string }
    | null;
  if (!s) return null;
  const signed = must(await db().storage.from(SOUND_BUCKET).createSignedUrl(s.path, 3600)) as { signedUrl: string };
  // Los sonidos no se editan (se sube uno nuevo), así que el id sirve de clave de caché.
  return { url: signed.signedUrl, key: s.id };
}

export interface PlayRequest {
  kind: "rule" | "test";
  animationId: string;
  soundId: string | null;
  ruleId?: string;
  ruleName?: string;
  batchId?: string;
}

/** Arma el push, respeta el silencio (salvo las pruebas) y lo manda. Siempre queda registrado. */
export async function play(p: PlayRequest): Promise<{ id: string; status: string; detail: string | null }> {
  const settings = await getSettings();
  const anim = (await db().from("ge_animations").select("id, name, data").eq("id", p.animationId).maybeSingle())
    .data as { id: string; name: string; data: unknown } | null;
  const base = {
    kind: p.kind,
    batch_id: p.batchId ?? null,
    rule_id: p.ruleId ?? null,
    rule_name: p.ruleName ?? null,
    animation_id: anim?.id ?? null,
    animation_name: anim?.name ?? null,
    sound_id: p.soundId,
  };
  const record = async (status: string, detail: string | null, payload: object | null = null) => {
    const row = must(
      await db().from("ge_plays").insert({ ...base, status, detail, payload }).select("id").single(),
    ) as { id: string };
    return { id: row.id, status, detail };
  };

  if (!anim) return record("error", "la animación ya no existe");
  if (p.kind !== "test" && settings.quiet_enabled && isQuiet(new Date(), settings.quiet_start, settings.quiet_end, settings.timezone)) {
    return record("silenced", `horario de silencio ${settings.quiet_start.slice(0, 5)}–${settings.quiet_end.slice(0, 5)}`);
  }
  if (!settings.fcm_token) return record("no_device", "no hay teléfono vinculado");

  let animWire: string;
  try {
    animWire = wire(normalizeAnimation(anim.data));
  } catch (e) {
    return record("error", `animación inválida: ${(e as Error).message}`);
  }
  const sound = p.soundId ? await soundUrl(p.soundId) : null;
  const played = await record("sending", null, { anim: animWire, soundId: p.soundId });

  const data: Record<string, string> = {
    type: "play",
    playId: played.id,
    title: (p.ruleName ?? anim.name).slice(0, 60),
  };
  if (sound) {
    data.sound = sound.url;
    data.soundKey = sound.key;
  }
  if (JSON.stringify(data).length + animWire.length < MAX_INLINE) data.anim = animWire;

  let status = "sent";
  let detail: string | null = data.anim ? null : "animación grande: el teléfono la baja aparte";
  try {
    await sendData(settings.fcm_token, data);
  } catch (e) {
    status = "error";
    detail = (e as Error).message;
  }
  await db().from("ge_plays").update({ status, detail }).eq("id", played.id);
  return { id: played.id, status, detail };
}

/** Para el teléfono, cuando la animación no entraba en el push. */
export async function playPayload(playId: string) {
  const row = (await db().from("ge_plays").select("payload, sound_id").eq("id", playId).maybeSingle()).data as
    | { payload: { anim?: string } | null; sound_id: string | null }
    | null;
  if (!row?.payload?.anim) return null;
  const sound = row.sound_id ? await soundUrl(row.sound_id) : null;
  return { anim: row.payload.anim, sound: sound?.url ?? null, soundKey: sound?.key ?? null };
}

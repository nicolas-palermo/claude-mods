"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { checkPassword, endSession, requireAdmin, startSession } from "@/lib/auth";
import { db, must, SOUND_BUCKET } from "@/lib/db";
import { play } from "@/lib/engine";
import { normalizeAnimation, blankFrame } from "@/lib/animation";
import { OPS, type Condition, type Op } from "@/lib/rules";

const str = (f: FormData, k: string) => String(f.get(k) ?? "").trim();

// ---- Sesión ----

export async function login(_: string | null, f: FormData): Promise<string | null> {
  if (!checkPassword(str(f, "password"))) return "Contraseña incorrecta";
  await startSession();
  redirect("/");
}

export async function logout() {
  await endSession();
  redirect("/login");
}

// ---- Fuentes ----

export async function createSource(f: FormData) {
  await requireAdmin();
  const name = str(f, "name") || "Fuente";
  must(await db().from("ge_sources").insert({ name }).select("id").single());
  revalidatePath("/fuentes");
}

export async function renameSource(f: FormData) {
  await requireAdmin();
  await db().from("ge_sources").update({ name: str(f, "name") }).eq("id", str(f, "id"));
  revalidatePath("/fuentes");
}

export async function rotateSource(f: FormData) {
  await requireAdmin();
  const token = Buffer.from(crypto.getRandomValues(new Uint8Array(18))).toString("hex");
  await db().from("ge_sources").update({ token }).eq("id", str(f, "id"));
  revalidatePath("/fuentes");
}

export async function deleteSource(f: FormData) {
  await requireAdmin();
  await db().from("ge_sources").delete().eq("id", str(f, "id"));
  revalidatePath("/fuentes");
}

// ---- Animaciones ----

export async function createAnimation(f: FormData) {
  await requireAdmin();
  const row = must(
    await db()
      .from("ge_animations")
      .insert({
        name: str(f, "name") || "Animación nueva",
        data: { frames: [blankFrame()], frameMs: 120, repeat: 2, brightness: 100 },
      })
      .select("id")
      .single(),
  ) as { id: string };
  redirect(`/animaciones/${row.id}`);
}

export async function saveAnimation(id: string, name: string, data: unknown): Promise<string | null> {
  await requireAdmin();
  try {
    const a = normalizeAnimation(data);
    const { error } = await db()
      .from("ge_animations")
      .update({ name: name.trim() || "Sin nombre", data: a, updated_at: new Date().toISOString() })
      .eq("id", id);
    if (error) return error.message;
    revalidatePath("/animaciones");
    return null;
  } catch (e) {
    return (e as Error).message;
  }
}

export async function deleteAnimation(f: FormData) {
  await requireAdmin();
  await db().from("ge_animations").delete().eq("id", str(f, "id"));
  revalidatePath("/animaciones");
  redirect("/animaciones");
}

/** Botón "Probar": manda la animación al teléfono ya, ignorando el horario de silencio. */
export async function testAnimation(animationId: string, soundId: string | null): Promise<string> {
  await requireAdmin();
  const r = await play({ kind: "test", animationId, soundId: soundId || null });
  revalidatePath("/historial");
  if (r.status === "sent") return r.detail ? `Enviada (${r.detail}).` : "Enviada al teléfono.";
  if (r.status === "no_device") return "No hay teléfono vinculado todavía: andá a Inicio y vinculalo.";
  return `No se pudo enviar: ${r.detail ?? r.status}`;
}

// ---- Reglas ----

function parseConditions(raw: string): Condition[] {
  const list = JSON.parse(raw || "[]") as Condition[];
  const ops = new Set(OPS.map((o) => o.op));
  return list
    .filter((c) => c.field?.trim())
    .map((c) => {
      if (!ops.has(c.op)) throw new Error(`operador desconocido: ${c.op}`);
      return { field: c.field.trim(), op: c.op as Op, value: (c.value ?? "").trim() };
    });
}

export async function createRule(f: FormData) {
  await requireAdmin();
  const animationId = str(f, "animation_id");
  if (!animationId) throw new Error("Primero creá una animación");
  const row = must(
    await db()
      .from("ge_rules")
      .insert({ name: str(f, "name") || "Regla nueva", animation_id: animationId, priority: 0 })
      .select("id")
      .single(),
  ) as { id: string };
  redirect(`/reglas/${row.id}`);
}

export async function saveRule(f: FormData) {
  await requireAdmin();
  const id = str(f, "id");
  await db()
    .from("ge_rules")
    .update({
      name: str(f, "name") || "Sin nombre",
      enabled: f.get("enabled") === "on",
      priority: Number(str(f, "priority")) || 0,
      source_id: str(f, "source_id") || null,
      animation_id: str(f, "animation_id"),
      sound_id: str(f, "sound_id") || null,
      conditions: parseConditions(str(f, "conditions")),
    })
    .eq("id", id);
  revalidatePath("/reglas");
  revalidatePath(`/reglas/${id}`);
}

export async function toggleRule(f: FormData) {
  await requireAdmin();
  await db().from("ge_rules").update({ enabled: f.get("enabled") === "true" }).eq("id", str(f, "id"));
  revalidatePath("/reglas");
}

export async function deleteRule(f: FormData) {
  await requireAdmin();
  await db().from("ge_rules").delete().eq("id", str(f, "id"));
  revalidatePath("/reglas");
  redirect("/reglas");
}

// ---- Sonidos ----

const AUDIO = /\.(mp3|ogg|oga|wav|m4a|aac|flac|opus)$/i;

export async function uploadSound(_: string | null, f: FormData): Promise<string | null> {
  await requireAdmin();
  const file = f.get("file");
  if (!(file instanceof File) || file.size === 0) return "Elegí un archivo";
  if (file.size > 3.5 * 1024 * 1024) return "Máximo 3,5 MB";
  if (!file.type.startsWith("audio/") && !AUDIO.test(file.name)) return "Tiene que ser un archivo de audio";
  const ext = (file.name.match(AUDIO)?.[1] ?? "bin").toLowerCase();
  const path = `${crypto.randomUUID()}.${ext}`;
  const up = await db()
    .storage.from(SOUND_BUCKET)
    .upload(path, Buffer.from(await file.arrayBuffer()), { contentType: file.type || "application/octet-stream" });
  if (up.error) return `No se pudo subir: ${up.error.message}`;
  const name = str(f, "name") || file.name.replace(AUDIO, "");
  const { error } = await db().from("ge_sounds").insert({ name, path, mime: file.type, size: file.size });
  if (error) return error.message;
  revalidatePath("/sonidos");
  return null;
}

export async function deleteSound(f: FormData) {
  await requireAdmin();
  const s = (await db().from("ge_sounds").select("path").eq("id", str(f, "id")).maybeSingle()).data as
    | { path: string }
    | null;
  if (s) await db().storage.from(SOUND_BUCKET).remove([s.path]);
  await db().from("ge_sounds").delete().eq("id", str(f, "id"));
  revalidatePath("/sonidos");
}

// ---- Ajustes ----

export async function saveSettings(f: FormData) {
  await requireAdmin();
  const tz = str(f, "timezone") || "America/Argentina/Buenos_Aires";
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
  } catch {
    throw new Error(`Zona horaria inválida: ${tz}`);
  }
  await db()
    .from("ge_settings")
    .update({
      quiet_enabled: f.get("quiet_enabled") === "on",
      quiet_start: str(f, "quiet_start") || "23:00",
      quiet_end: str(f, "quiet_end") || "08:00",
      timezone: tz,
      batch_window_seconds: Math.max(0, Math.min(50, Number(str(f, "batch_window_seconds")) || 0)),
    })
    .eq("id", 1);
  revalidatePath("/ajustes");
  revalidatePath("/");
}

export async function rotateDeviceKey() {
  await requireAdmin();
  const key = Buffer.from(crypto.getRandomValues(new Uint8Array(18))).toString("hex");
  await db().from("ge_settings").update({ device_key: key, fcm_token: null }).eq("id", 1);
  revalidatePath("/");
}

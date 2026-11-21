import "server-only";
import { createClient, type SupabaseClient } from "@supabase/supabase-js";

let client: SupabaseClient | null = null;

/** Cliente con la service role: solo del lado del servidor (las tablas tienen RLS sin políticas). */
export function db(): SupabaseClient {
  if (client) return client;
  const url = process.env.SUPABASE_URL;
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) throw new Error("Faltan SUPABASE_URL y SUPABASE_SERVICE_ROLE_KEY");
  client = createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
  return client;
}

export const SOUND_BUCKET = "ge-sounds";

export interface Settings {
  device_key: string;
  fcm_token: string | null;
  device_model: string | null;
  app_version: string | null;
  device_registered_at: string | null;
  batch_window_seconds: number;
  quiet_enabled: boolean;
  quiet_start: string;
  quiet_end: string;
  timezone: string;
}

export async function getSettings(): Promise<Settings> {
  const { data, error } = await db().from("ge_settings").select("*").eq("id", 1).single();
  if (error) throw new Error(`No pude leer ge_settings (¿corriste schema.sql?): ${error.message}`);
  return data as Settings;
}

/** Tira el error de Supabase como excepción, para no olvidarse de chequearlo. */
export function must<T>(r: { data: T | null; error: { message: string } | null }): T {
  if (r.error) throw new Error(r.error.message);
  return r.data as T;
}

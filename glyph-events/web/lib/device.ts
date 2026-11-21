import "server-only";
import { safeEqual } from "./auth";
import { getSettings } from "./db";

/** El teléfono se identifica con la clave del dispositivo (la de la URL de vinculación). */
export async function deviceAuthorized(req: Request): Promise<boolean> {
  const key = req.headers.get("x-device-key") ?? "";
  if (!key) return false;
  const s = await getSettings();
  return safeEqual(key, s.device_key);
}

export const unauthorized = () => Response.json({ error: "clave de dispositivo inválida" }, { status: 401 });

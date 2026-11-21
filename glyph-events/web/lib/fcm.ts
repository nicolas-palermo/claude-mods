import "server-only";
import { createSign } from "node:crypto";

// Firebase Cloud Messaging, API HTTP v1, sin SDK: un JWT firmado con la cuenta de servicio
// se cambia por un access token de Google.

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

let cached: { token: string; exp: number } | null = null;

function serviceAccount(): ServiceAccount {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error("Falta FIREBASE_SERVICE_ACCOUNT");
  const text = raw.trim().startsWith("{") ? raw : Buffer.from(raw, "base64").toString("utf8");
  return JSON.parse(text) as ServiceAccount;
}

/** Lo que necesita la app para arrancar Firebase sin google-services.json. */
export function firebasePublicConfig() {
  const appId = process.env.FIREBASE_APP_ID;
  const apiKey = process.env.FIREBASE_API_KEY;
  if (!appId || !apiKey) throw new Error("Faltan FIREBASE_APP_ID y FIREBASE_API_KEY");
  // El App ID es "1:<número de proyecto>:android:<hash>": el número es el sender id.
  const senderId = process.env.FIREBASE_SENDER_ID || appId.split(":")[1];
  return { appId, apiKey, projectId: serviceAccount().project_id, senderId };
}

async function accessToken(sa: ServiceAccount): Promise<string> {
  if (cached && cached.exp > Date.now() + 60_000) return cached.token;
  const now = Math.floor(Date.now() / 1000);
  const enc = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const unsigned = `${enc({ alg: "RS256", typ: "JWT" })}.${enc({
    iss: sa.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  })}`;
  const sig = createSign("RSA-SHA256").update(unsigned).sign(sa.private_key, "base64url");
  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${unsigned}.${sig}`,
    }),
  });
  const json = (await res.json()) as { access_token?: string; expires_in?: number; error_description?: string };
  if (!res.ok || !json.access_token) throw new Error(`Google OAuth: ${json.error_description ?? res.status}`);
  cached = { token: json.access_token, exp: Date.now() + (json.expires_in ?? 3600) * 1000 };
  return json.access_token;
}

/** Manda un mensaje solo de datos, con prioridad alta para que llegue con el teléfono dormido. */
export async function sendData(token: string, data: Record<string, string>): Promise<void> {
  const sa = serviceAccount();
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${await accessToken(sa)}`, "content-type": "application/json" },
    body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl: "300s" } } }),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`FCM ${res.status}: ${text.slice(0, 300)}`);
  }
}

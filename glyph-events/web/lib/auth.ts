import "server-only";
import { createHmac, timingSafeEqual } from "node:crypto";
import { cookies } from "next/headers";
import { redirect } from "next/navigation";

const COOKIE = "ge_session";
const DAYS = 60;

function secret(): string {
  const p = process.env.ADMIN_PASSWORD;
  if (!p) throw new Error("Falta ADMIN_PASSWORD");
  return `glyph-events:${p}`;
}

function sign(value: string): string {
  return createHmac("sha256", secret()).update(value).digest("base64url");
}

export function safeEqual(a: string, b: string): boolean {
  const x = Buffer.from(a);
  const y = Buffer.from(b);
  return x.length === y.length && timingSafeEqual(x, y);
}

export function checkPassword(p: string): boolean {
  return !!process.env.ADMIN_PASSWORD && safeEqual(p, process.env.ADMIN_PASSWORD);
}

export async function startSession() {
  const exp = String(Date.now() + DAYS * 86400_000);
  (await cookies()).set(COOKIE, `${exp}.${sign(exp)}`, {
    httpOnly: true,
    secure: true,
    sameSite: "lax",
    path: "/",
    maxAge: DAYS * 86400,
  });
}

export async function endSession() {
  (await cookies()).delete(COOKIE);
}

export async function isAdmin(): Promise<boolean> {
  const v = (await cookies()).get(COOKIE)?.value;
  if (!v) return false;
  const [exp, sig] = v.split(".");
  if (!exp || !sig || Number(exp) < Date.now()) return false;
  return safeEqual(sig, sign(exp));
}

/** Para páginas y server actions: sin sesión, al login. */
export async function requireAdmin() {
  if (!(await isAdmin())) redirect("/login");
}

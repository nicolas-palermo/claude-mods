import { notFound } from "next/navigation";
import { db } from "@/lib/db";
import { normalizeAnimation } from "@/lib/animation";
import Editor from "./editor";

export default async function AnimationPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  const row = (await db().from("ge_animations").select("*").eq("id", id).maybeSingle()).data as
    | { id: string; name: string; data: unknown }
    | null;
  if (!row) notFound();
  const sounds = ((await db().from("ge_sounds").select("id, name").order("name")).data ?? []) as { id: string; name: string }[];
  let anim;
  try {
    anim = normalizeAnimation(row.data);
  } catch {
    anim = { frames: [".".repeat(169)], frameMs: 120, repeat: 1, brightness: 100 };
  }
  return <Editor id={row.id} initialName={row.name} initial={anim} sounds={sounds} />;
}

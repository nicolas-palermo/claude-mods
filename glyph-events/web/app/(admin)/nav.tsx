"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const LINKS = [
  ["/", "Inicio"],
  ["/reglas", "Reglas"],
  ["/animaciones", "Animaciones"],
  ["/sonidos", "Sonidos"],
  ["/fuentes", "Fuentes"],
  ["/historial", "Historial"],
  ["/ajustes", "Ajustes"],
] as const;

export default function Nav({ logout }: { logout: () => Promise<void> }) {
  const path = usePathname();
  return (
    <nav>
      <span className="brand" style={{ padding: "6px 8px" }}>GE</span>
      {LINKS.map(([href, label]) => (
        <Link key={href} href={href} className={(href === "/" ? path === "/" : path.startsWith(href)) ? "on" : ""}>
          {label}
        </Link>
      ))}
      <form action={logout} style={{ marginLeft: "auto" }}>
        <button style={{ border: 0, background: "none", color: "var(--muted)" }}>Salir</button>
      </form>
    </nav>
  );
}

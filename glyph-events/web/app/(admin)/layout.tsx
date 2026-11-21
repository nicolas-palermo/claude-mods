import { requireAdmin } from "@/lib/auth";
import { logout } from "../actions";
import Nav from "./nav";

export const dynamic = "force-dynamic";

export default async function AdminLayout({ children }: { children: React.ReactNode }) {
  await requireAdmin();
  return (
    <>
      <Nav logout={logout} />
      <main>{children}</main>
    </>
  );
}

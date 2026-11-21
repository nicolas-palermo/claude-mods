import { deviceAuthorized, unauthorized } from "@/lib/device";
import { firebasePublicConfig } from "@/lib/fcm";

export const dynamic = "force-dynamic";

/** La app la pide al vincularse: con esto arranca Firebase sin google-services.json. */
export async function GET(req: Request) {
  if (!(await deviceAuthorized(req))) return unauthorized();
  try {
    return Response.json({ firebase: firebasePublicConfig() });
  } catch (e) {
    return Response.json({ error: (e as Error).message }, { status: 500 });
  }
}

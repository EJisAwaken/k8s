import { NextResponse, type NextRequest } from "next/server";
import { SESSION_COOKIE } from "@/lib/auth/constants";
import { decodeSession } from "@/lib/auth/session";

/**
 * BFF — session courante (lecture seule, sans appel backend) : permet aux
 * composants client de connaître l'utilisateur sans exposer les tokens.
 */
export async function GET(req: NextRequest) {
  const session = decodeSession(req.cookies.get(SESSION_COOKIE)?.value);
  if (!session) {
    return NextResponse.json({ authenticated: false }, { status: 401 });
  }
  return NextResponse.json({ authenticated: true, user: session });
}

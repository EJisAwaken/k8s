import { NextResponse, type NextRequest } from "next/server";
import { ACCESS_COOKIE, BACKEND_REFRESH_COOKIE, REFRESH_COOKIE } from "@/lib/auth/constants";
import { backendUrl, forwardedHeaders } from "@/lib/auth/backend";
import { clearAuthCookies } from "@/lib/auth/session";

/** Déconnexion : révoque la session côté backend puis efface les cookies, quoi qu'il arrive. */
export async function POST(req: NextRequest) {
  const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
  const accessToken = req.cookies.get(ACCESS_COOKIE)?.value;

  try {
    await fetch(backendUrl("/api/v1/auth/logout"), {
      method: "POST",
      headers: {
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(refreshToken ? { Cookie: `${BACKEND_REFRESH_COOKIE}=${refreshToken}` } : {}),
        ...forwardedHeaders(req),
      },
      cache: "no-store",
    });
  } catch {
    // Backend injoignable : les cookies sont effacés malgré tout.
  }

  const res = new NextResponse(null, { status: 204 });
  clearAuthCookies(res);
  return res;
}

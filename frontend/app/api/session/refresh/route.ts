import { NextResponse, type NextRequest } from "next/server";
import { BACKEND_REFRESH_COOKIE, REFRESH_COOKIE } from "@/lib/auth/constants";
import { backendUrl, extractRefreshCookie, forwardedHeaders, problem } from "@/lib/auth/backend";
import { applyAuthCookies, clearAuthCookies } from "@/lib/auth/session";
import { toSessionUser, type BackendAuthResponse } from "@/lib/auth/types";
import { messages } from "@/lib/messages";

/** Renouvellement silencieux : appelé par le client API sur un 401. */
export async function POST(req: NextRequest) {
  const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
  if (!refreshToken) {
    const res = NextResponse.json({ message: messages.sessionExpired }, { status: 401 });
    clearAuthCookies(res);
    return res;
  }

  let backendRes: Response;
  try {
    backendRes = await fetch(backendUrl("/api/v1/auth/refresh"), {
      method: "POST",
      headers: { Cookie: `${BACKEND_REFRESH_COOKIE}=${refreshToken}`, ...forwardedHeaders(req) },
      cache: "no-store",
    });
  } catch {
    // Backend injoignable : on NE purge PAS la session (le réseau reviendra).
    return NextResponse.json({ message: messages.network }, { status: 502 });
  }

  if (!backendRes.ok) {
    const { detail } = await problem(backendRes);
    const res = NextResponse.json({ message: detail ?? messages.sessionExpired }, { status: 401 });
    clearAuthCookies(res);
    return res;
  }

  const auth = (await backendRes.json()) as BackendAuthResponse;
  const rotated = extractRefreshCookie(backendRes);
  const session = toSessionUser(auth);
  const res = NextResponse.json(session);
  applyAuthCookies(res, {
    accessToken: auth.accessToken,
    expiresIn: auth.expiresIn,
    refreshToken: rotated?.value,
    refreshMaxAge: rotated?.maxAge,
    session,
  });
  return res;
}

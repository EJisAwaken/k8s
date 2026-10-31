import { NextResponse, type NextRequest } from "next/server";
import { backendUrl, extractRefreshCookie, forwardedHeaders, problem } from "@/lib/auth/backend";
import { applyAuthCookies } from "@/lib/auth/session";
import { toSessionUser, type BackendAuthResponse } from "@/lib/auth/types";
import { messages } from "@/lib/messages";

/**
 * Login BFF : relaie les identifiants au backend, puis pose les cookies
 * httpOnly. Le code 2FA éventuel est relayé tel quel ; un refus
 * « TOTP_REQUIRED » est renvoyé avec son code pour que l'écran affiche le champ.
 */
export async function POST(req: NextRequest) {
  let body: unknown;
  try {
    body = await req.json();
  } catch {
    return NextResponse.json({ message: messages.missingFields }, { status: 400 });
  }

  const { email, password, code } = (body ?? {}) as Record<string, unknown>;
  if (typeof email !== "string" || typeof password !== "string" || email.trim() === "" || password === "") {
    return NextResponse.json({ message: messages.missingFields }, { status: 400 });
  }

  let backendRes: Response;
  try {
    backendRes = await fetch(backendUrl("/api/v1/auth/login"), {
      method: "POST",
      headers: { "Content-Type": "application/json", ...forwardedHeaders(req) },
      body: JSON.stringify({
        email: email.trim(),
        password,
        code: typeof code === "string" && code.trim() !== "" ? code.trim() : undefined,
      }),
      cache: "no-store",
    });
  } catch {
    return NextResponse.json({ message: messages.network }, { status: 502 });
  }

  if (!backendRes.ok) {
    const { detail, code: errorCode } = await problem(backendRes);
    return NextResponse.json({ message: detail, code: errorCode }, { status: backendRes.status });
  }

  const auth = (await backendRes.json()) as BackendAuthResponse;
  const refresh = extractRefreshCookie(backendRes);
  const session = toSessionUser(auth);

  const res = NextResponse.json(session);
  applyAuthCookies(res, {
    accessToken: auth.accessToken,
    expiresIn: auth.expiresIn,
    refreshToken: refresh?.value,
    refreshMaxAge: refresh?.maxAge,
    session,
  });
  return res;
}

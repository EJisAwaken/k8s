import { cookies } from "next/headers";
import type { NextResponse } from "next/server";
import {
  ACCESS_COOKIE,
  DEFAULT_REFRESH_TTL_SECONDS,
  REFRESH_COOKIE,
  REFRESH_COOKIE_PATH,
  SESSION_COOKIE,
} from "@/lib/auth/constants";
import { isRole, type SessionUser } from "@/lib/auth/types";

/**
 * Lecture/écriture des cookies de session du BFF — module SERVEUR
 * uniquement (next/headers) ; le navigateur passe par GET /api/session.
 *
 * Les cookies n'ont pas d'attribut `domain` : ils sont host-only, donc jamais
 * envoyés à un autre sous-domaine. Ne pas en ajouter un sans y avoir réfléchi.
 *
 * Le cookie de session n'est PAS une preuve d'identité : il sert à l'affichage
 * et à la garde optimiste des pages. L'autorisation réelle est faite par le
 * backend, sur le JWT.
 */

export function encodeSession(user: SessionUser): string {
  return Buffer.from(JSON.stringify(user), "utf8").toString("base64url");
}

export function decodeSession(raw: string | undefined): SessionUser | null {
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(Buffer.from(raw, "base64url").toString("utf8"));
    if (typeof parsed !== "object" || parsed === null) return null;
    const c = parsed as Record<string, unknown>;
    if (typeof c.userId !== "number" || typeof c.email !== "string" || !isRole(c.role)) {
      return null;
    }
    return {
      userId: c.userId,
      email: c.email,
      fullName: typeof c.fullName === "string" ? c.fullName : c.email,
      role: c.role,
      passwordExpired: c.passwordExpired === true,
      twoFactorSetupRequired: c.twoFactorSetupRequired === true,
    };
  } catch {
    return null;
  }
}

export async function readSession(): Promise<SessionUser | null> {
  const store = await cookies();
  return decodeSession(store.get(SESSION_COOKIE)?.value);
}

interface AuthCookiesInput {
  accessToken: string;
  expiresIn: number;
  refreshToken?: string;
  refreshMaxAge?: number;
  session: SessionUser;
}

function secureFlag(): boolean {
  return process.env.NODE_ENV === "production";
}

export function applyAuthCookies(res: NextResponse, input: AuthCookiesInput): void {
  const secure = secureFlag();
  const refreshMaxAge = input.refreshMaxAge ?? DEFAULT_REFRESH_TTL_SECONDS;

  res.cookies.set(ACCESS_COOKIE, input.accessToken, {
    httpOnly: true,
    sameSite: "lax",
    secure,
    path: "/",
    maxAge: input.expiresIn,
  });

  if (input.refreshToken) {
    res.cookies.set(REFRESH_COOKIE, input.refreshToken, {
      httpOnly: true,
      sameSite: "lax",
      secure,
      path: REFRESH_COOKIE_PATH,
      maxAge: refreshMaxAge,
    });
  }

  res.cookies.set(SESSION_COOKIE, encodeSession(input.session), {
    httpOnly: true,
    sameSite: "lax",
    secure,
    path: "/",
    maxAge: refreshMaxAge,
  });
}

export function clearAuthCookies(res: NextResponse): void {
  const base = { httpOnly: true, sameSite: "lax" as const, secure: secureFlag(), maxAge: 0 };
  res.cookies.set(ACCESS_COOKIE, "", { ...base, path: "/" });
  res.cookies.set(REFRESH_COOKIE, "", { ...base, path: REFRESH_COOKIE_PATH });
  res.cookies.set(SESSION_COOKIE, "", { ...base, path: "/" });
}

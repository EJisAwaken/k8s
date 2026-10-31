import type { NextRequest } from "next/server";
import { BACKEND_REFRESH_COOKIE, DEFAULT_REFRESH_TTL_SECONDS } from "@/lib/auth/constants";

/** Adresse du backend Spring vue par le serveur Next (jamais par le navigateur). */
export function backendUrl(path: string): string {
  const base = process.env.BACKEND_INTERNAL_URL ?? "http://localhost:8095";
  return `${base}${path}`;
}

/** En-têtes propagés au backend pour le journal de connexion (navigateur, IP d'origine). */
export function forwardedHeaders(req: NextRequest): Record<string, string> {
  const headers: Record<string, string> = {};
  const userAgent = req.headers.get("user-agent");
  if (userAgent) headers["User-Agent"] = userAgent;
  const forwardedFor = req.headers.get("x-forwarded-for");
  if (forwardedFor) headers["X-Forwarded-For"] = forwardedFor;
  return headers;
}

/** Corps d'erreur RFC 7807 du backend : `detail` (affichable tel quel) et `code` machine éventuel. */
export async function problem(res: Response): Promise<{ detail: string | null; code: string | null }> {
  try {
    const body: unknown = await res.json();
    if (typeof body === "object" && body !== null) {
      const b = body as Record<string, unknown>;
      return {
        detail: typeof b.detail === "string" && b.detail.trim() !== "" ? b.detail : null,
        code: typeof b.code === "string" ? b.code : null,
      };
    }
  } catch {
    // corps absent ou non-JSON
  }
  return { detail: null, code: null };
}

export interface RefreshCookie {
  value: string;
  maxAge: number;
}

/**
 * Extrait le refresh token du Set-Cookie du backend pour le re-poser sur le
 * BFF (/api/session) : le navigateur ne parle jamais directement aux
 * endpoints d'authentification du backend.
 */
export function extractRefreshCookie(res: Response): RefreshCookie | null {
  for (const cookie of res.headers.getSetCookie()) {
    if (!cookie.startsWith(`${BACKEND_REFRESH_COOKIE}=`)) continue;
    const value = cookie.slice(BACKEND_REFRESH_COOKIE.length + 1).split(";")[0];
    if (!value) return null; // cookie de purge (logout)
    const maxAgeMatch = cookie.match(/max-age=(\d+)/i);
    return {
      value,
      maxAge: maxAgeMatch ? Number.parseInt(maxAgeMatch[1], 10) : DEFAULT_REFRESH_TTL_SECONDS,
    };
  }
  return null;
}

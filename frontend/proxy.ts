import { NextResponse, type NextRequest } from "next/server";
import { ACCESS_COOKIE, SESSION_COOKIE } from "@/lib/auth/constants";

/**
 * Proxy Next 16 — deux rôles :
 *
 * 1. `/api/v1/*` : injecte `Authorization: Bearer <access>` depuis le cookie
 *    httpOnly, avant le rewrite vers le backend (next.config.ts). Le jeton
 *    n'est jamais lisible par le JavaScript du navigateur.
 * 2. Pages : garde OPTIMISTE (présence du cookie de session, sans appel
 *    réseau) → /login si absent. La vraie autorisation reste au backend.
 */

const PUBLIC_PAGES = ["/login"];

export function proxy(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (pathname.startsWith("/api/v1/")) {
    const accessToken = request.cookies.get(ACCESS_COOKIE)?.value;
    if (accessToken && !request.headers.get("authorization")) {
      const headers = new Headers(request.headers);
      headers.set("Authorization", `Bearer ${accessToken}`);
      return NextResponse.next({ request: { headers } });
    }
    return NextResponse.next();
  }

  // BFF de session : jamais gardé (login/refresh/logout doivent passer).
  if (pathname.startsWith("/api/session")) {
    return NextResponse.next();
  }

  const hasSession = Boolean(request.cookies.get(SESSION_COOKIE)?.value);
  const isPublicPage = PUBLIC_PAGES.some((p) => pathname === p || pathname.startsWith(`${p}/`));

  if (!hasSession && !isPublicPage) {
    const loginUrl = new URL("/login", request.nextUrl);
    if (pathname !== "/") loginUrl.searchParams.set("next", pathname);
    return NextResponse.redirect(loginUrl);
  }

  if (hasSession && isPublicPage) {
    return NextResponse.redirect(new URL("/", request.nextUrl));
  }

  return NextResponse.next();
}

export const config = {
  // Tout sauf les assets — et TOUT /api/* : un chemin d'API qui finit par une
  // extension (…/export.xlsx) doit quand même recevoir son Authorization.
  matcher: [
    "/((?!_next/static|_next/image|favicon\\.ico|.*\\.(?:svg|png|jpg|jpeg|gif|webp|ico|txt|xml)$).*)",
    "/api/:path*",
  ],
};

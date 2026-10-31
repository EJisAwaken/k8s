/**
 * Cookies de session du BFF (Backend For Frontend). Tous httpOnly : le
 * JavaScript du navigateur ne voit jamais un jeton.
 */

/** Access token JWT (15 min) — injecté en `Authorization` par `proxy.ts`. */
export const ACCESS_COOKIE = "stock_access";

/** Refresh token opaque, limité aux routes `/api/session`. */
export const REFRESH_COOKIE = "stock_refresh_bff";

/** Profil de session (id, e-mail, rôle) — garde optimiste des pages. */
export const SESSION_COOKIE = "stock_session";

export const REFRESH_COOKIE_PATH = "/api/session";

/** Nom du cookie de refresh posé par le backend (`app.jwt.refresh.cookie.name`). */
export const BACKEND_REFRESH_COOKIE =
  process.env.BACKEND_REFRESH_COOKIE_NAME ?? "stock_refresh";

export const DEFAULT_REFRESH_TTL_SECONDS = 2_592_000;

import { messages } from "@/lib/messages";

/**
 * Client HTTP du navigateur (API reprise d'EcoleManagement : `api.get/post/…`).
 * Différence de sécurité : aucun jeton n'est lu ni stocké côté JavaScript —
 * le proxy Next injecte le Bearer depuis un cookie httpOnly. Sur un 401, un
 * refresh silencieux est tenté (une seule fois, mutualisé), puis la requête
 * est rejouée.
 */

const BASE_URL = "/api/v1";

/**
 * Opérations de compte relayées par le BFF (il transmet le refresh token,
 * qui identifie la session courante) — voir app/api/session/compte.
 */
const VIA_BFF = ["/auth/change-password", "/auth/sessions"];

type Params = Record<string, string | number | boolean | undefined | null>;

export class ApiError extends Error {
  constructor(
    public status: number,
    public detail: string,
    public code: string | null = null,
  ) {
    super(detail);
    this.name = "ApiError";
  }
}

export function apiErrorMessage(e: unknown): string {
  return e instanceof ApiError ? e.detail : messages.technical;
}

type IssueRefresh = "ok" | "refuse" | "injoignable";
let refreshEnVol: Promise<IssueRefresh> | null = null;

function rafraichir(): Promise<IssueRefresh> {
  refreshEnVol ??= fetch("/api/session/refresh", { method: "POST" })
    .then((r): IssueRefresh => (r.ok ? "ok" : r.status >= 500 ? "injoignable" : "refuse"))
    .catch((): IssueRefresh => "injoignable")
    .finally(() => {
      refreshEnVol = null;
    });
  return refreshEnVol;
}

function url(path: string, params?: Params): string {
  const qs = params
    ? Object.entries(params)
        .filter(([, v]) => v !== undefined && v !== null && v !== "")
        .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
        .join("&")
    : "";
  const base = VIA_BFF.some((p) => path === p || path.startsWith(`${p}/`))
    ? `/api/session/compte${path.slice("/auth".length)}`
    : `${BASE_URL}${path}`;
  return `${base}${qs ? `?${qs}` : ""}`;
}

async function envoyer(path: string, init: RequestInit, params?: Params, rejeu = true): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(url(path, params), { ...init, cache: "no-store" });
  } catch {
    throw new ApiError(0, messages.network);
  }
  if (res.status === 401 && rejeu) {
    const issue = await rafraichir();
    if (issue === "ok") return envoyer(path, init, params, false);
    if (issue === "refuse") {
      // Rechargement complet voulu : il purge tout l'état client de la session.
      // eslint-disable-next-line @next/next/no-location-assign-relative-destination
      window.location.assign(`/login?next=${encodeURIComponent(window.location.pathname)}`);
    }
    throw new ApiError(401, issue === "refuse" ? messages.sessionExpired : messages.network);
  }
  if (!res.ok) {
    let detail: string | null = null;
    let code: string | null = null;
    try {
      const body = (await res.json()) as { detail?: unknown; code?: unknown };
      if (typeof body.detail === "string" && body.detail.trim()) detail = body.detail;
      if (typeof body.code === "string") code = body.code;
    } catch {
      // corps non-JSON
    }
    throw new ApiError(
      res.status,
      detail ?? (res.status === 403 ? messages.forbidden : res.status >= 500 ? messages.network : messages.technical),
      code,
    );
  }
  return res;
}

async function json<T>(method: string, path: string, body?: unknown, params?: Params): Promise<T> {
  const isForm = body instanceof FormData;
  const res = await envoyer(
    path,
    {
      method,
      headers: isForm ? { Accept: "application/json" } : { Accept: "application/json", "Content-Type": "application/json" },
      body: isForm ? body : body === undefined ? undefined : JSON.stringify(body),
    },
    params,
  );
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Télécharge une réponse binaire (Excel, PDF) et la propose à l'enregistrement ou l'ouvre. */
async function telecharger(path: string, nomFichier: string, opts?: { params?: Params; body?: unknown; ouvrir?: boolean }) {
  const res = await envoyer(
    path,
    opts?.body !== undefined
      ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(opts.body) }
      : { method: "GET" },
    opts?.params,
  );
  const blob = await res.blob();
  const lien = URL.createObjectURL(blob);
  // Ouverture dans un onglet (aperçu PDF) ; si le navigateur bloque la
  // fenêtre — appel après un `await`, donc hors du geste de l'utilisateur —,
  // on retombe sur le téléchargement.
  const fenetre = opts?.ouvrir ? window.open(lien, "_blank") : null;
  if (fenetre) {
    fenetre.opener = null;
  } else {
    const a = document.createElement("a");
    a.href = lien;
    a.download = nomFichier;
    a.click();
  }
  setTimeout(() => URL.revokeObjectURL(lien), 60_000);
}

export const api = {
  get: <T>(path: string, params?: Params) => json<T>("GET", path, undefined, params),
  post: <T>(path: string, body?: unknown, params?: Params) => json<T>("POST", path, body, params),
  put: <T>(path: string, body?: unknown, params?: Params) => json<T>("PUT", path, body, params),
  delete: <T>(path: string) => json<T>("DELETE", path),
  telecharger,
};

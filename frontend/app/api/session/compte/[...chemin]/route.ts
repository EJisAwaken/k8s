import { NextResponse, type NextRequest } from "next/server";
import { ACCESS_COOKIE, BACKEND_REFRESH_COOKIE, REFRESH_COOKIE } from "@/lib/auth/constants";
import { backendUrl, forwardedHeaders } from "@/lib/auth/backend";
import { messages } from "@/lib/messages";

/**
 * Relais BFF des opérations de compte qui doivent connaître la SESSION
 * COURANTE : changer son mot de passe (les autres appareils sont déconnectés,
 * pas celui-ci) et lister ses appareils (« cet appareil »). Le refresh token
 * n'est envoyé par le navigateur qu'aux routes /api/session : c'est donc ici,
 * et non via /api/v1, qu'on peut le transmettre au backend.
 */
const AUTORISES = new Set(["change-password", "sessions"]);

async function relayer(req: NextRequest, { params }: { params: Promise<{ chemin: string[] }> }) {
  const { chemin } = await params;
  if (!AUTORISES.has(chemin[0])) {
    return NextResponse.json({ message: "Introuvable." }, { status: 404 });
  }
  const accessToken = req.cookies.get(ACCESS_COOKIE)?.value;
  const refreshToken = req.cookies.get(REFRESH_COOKIE)?.value;
  const corps = req.method === "GET" || req.method === "DELETE" ? undefined : await req.text();
  try {
    const res = await fetch(backendUrl(`/api/v1/auth/${chemin.map(encodeURIComponent).join("/")}`), {
      method: req.method,
      headers: {
        ...(corps ? { "Content-Type": "application/json" } : {}),
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...(refreshToken ? { Cookie: `${BACKEND_REFRESH_COOKIE}=${refreshToken}` } : {}),
        ...forwardedHeaders(req),
      },
      body: corps,
      cache: "no-store",
    });
    return new NextResponse(res.status === 204 ? null : await res.text(), {
      status: res.status,
      headers: { "Content-Type": res.headers.get("Content-Type") ?? "application/json" },
    });
  } catch {
    return NextResponse.json({ message: messages.network }, { status: 502 });
  }
}

export { relayer as GET, relayer as POST, relayer as DELETE };

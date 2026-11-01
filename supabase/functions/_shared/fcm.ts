// Envoi FCM (API HTTP v1) pour les appareils Android (#76).
// Secret : FCM_SERVICE_ACCOUNT, le JSON du compte de service Firebase (client_email, private_key, project_id),
// voir docs/SECRETS.md. Sans ce secret, rien n'est envoyé aux appareils Android.
//
// Les messages sont « data-only » : l'app Android construit elle-même la notification (canal, intention,
// regroupement), ce qui garde le texte hors de tout traitement automatique de Firebase.
import { importPKCS8, SignJWT } from "npm:jose@5";
import type { PushMessage } from "./push.ts";

export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
export const OAUTH_TOKEN_URL = "https://oauth2.googleapis.com/token";
const TIMEOUT_MS = 5000;
const JWT_LIFETIME_S = 3600;
const EXPIRY_MARGIN_S = 60;

export type ServiceAccount = { client_email: string; private_key: string; project_id: string };
export type FcmDeps = { fetch: typeof fetch; now?: () => number };
export type FcmErrorKind = "stale-token" | "other";

export function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
  if (!raw) return null;
  try {
    const sa = JSON.parse(raw);
    if (typeof sa?.client_email === "string" && typeof sa?.private_key === "string" && typeof sa?.project_id === "string") {
      return { client_email: sa.client_email, private_key: sa.private_key, project_id: sa.project_id };
    }
  } catch { /* JSON invalide : traité comme non configuré */ }
  return null;
}

export function buildServiceAccountClaims(sa: Pick<ServiceAccount, "client_email">, nowSeconds: number) {
  return { iss: sa.client_email, scope: FCM_SCOPE, aud: OAUTH_TOKEN_URL, iat: nowSeconds, exp: nowSeconds + JWT_LIFETIME_S };
}

// Message FCM v1, uniquement des données : toutes les valeurs sont des chaînes, pas de clé `notification`.
export function buildFcmMessage(token: string, message: PushMessage): {
  message: {
    token: string;
    data: Record<string, string>;
    android: { priority: "HIGH" | "NORMAL"; collapse_key: string };
  };
} {
  return {
    message: {
      token,
      data: { ...message.data, title: message.title, body: message.body },
      android: { priority: message.priority === "normal" ? "NORMAL" : "HIGH", collapse_key: message.threadId },
    },
  };
}

// Seuls un jeton inconnu (404 / UNREGISTERED) ou un jeton mal formé (400 INVALID_ARGUMENT visant le jeton)
// justifient de le supprimer. Une erreur d'authentification, de quota ou de serveur ne dit rien du jeton.
export function classifyFcmError(status: number, body: unknown): FcmErrorKind {
  const error = (body as { error?: { status?: string; message?: string; details?: unknown } } | null)?.error;
  const text = JSON.stringify(error?.details ?? []);
  if (status === 404 || ((status === 400) && text.includes("UNREGISTERED"))) return "stale-token";
  if (status === 400 && error?.status === "INVALID_ARGUMENT") {
    return text.includes("message.token") || /registration token/i.test(error.message ?? "") ? "stale-token" : "other";
  }
  return "other";
}

// Jeton d'accès OAuth conservé en mémoire (une instance de fonction Edge sert plusieurs envois).
let cached: { key: string; token: string; expiresAt: number } | null = null;
export function resetFcmTokenCache() {
  cached = null;
}

export async function getAccessToken(sa: ServiceAccount, deps: FcmDeps): Promise<string | null> {
  const nowMs = (deps.now ?? Date.now)();
  const key = `${sa.client_email}|${sa.project_id}`;
  if (cached && cached.key === key && nowMs < cached.expiresAt) return cached.token;

  const assertion = await new SignJWT(buildServiceAccountClaims(sa, Math.floor(nowMs / 1000)))
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .sign(await importPKCS8(sa.private_key, "RS256"));
  const res = await deps.fetch(OAUTH_TOKEN_URL, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  }).catch(() => null);
  if (!res?.ok) {
    await res?.body?.cancel();
    return null;
  }
  const body = await res.json().catch(() => null) as { access_token?: string; expires_in?: number } | null;
  if (!body?.access_token) return null;
  cached = {
    key,
    token: body.access_token,
    expiresAt: nowMs + Math.max(0, (body.expires_in ?? 3600) - EXPIRY_MARGIN_S) * 1000,
  };
  return body.access_token;
}

// Envoie un message à un jeton. `stale` vaut true quand le jeton doit être supprimé.
export async function sendFcm(
  sa: ServiceAccount,
  accessToken: string,
  token: string,
  message: PushMessage,
  deps: FcmDeps,
): Promise<{ ok: boolean; stale: boolean }> {
  const res = await deps.fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
    method: "POST",
    headers: { authorization: `Bearer ${accessToken}`, "content-type": "application/json" },
    body: JSON.stringify(buildFcmMessage(token, message)),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  }).catch(() => null);
  if (!res) return { ok: false, stale: false };
  if (res.ok) {
    await res.body?.cancel();
    return { ok: true, stale: false };
  }
  const body = await res.json().catch(() => null);
  return { ok: false, stale: classifyFcmError(res.status, body) === "stale-token" };
}

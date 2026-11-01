// Tests de l'envoi FCM : message, JWT du compte de service, classification des erreurs, cache OAuth.
import { assert, assertEquals, assertFalse, assertNotEquals } from "jsr:@std/assert@1";
import { exportPKCS8, generateKeyPair, jwtVerify, decodeProtectedHeader } from "npm:jose@5";
import {
  buildFcmMessage,
  buildServiceAccountClaims,
  classifyFcmError,
  FCM_SCOPE,
  getAccessToken,
  OAUTH_TOKEN_URL,
  parseServiceAccount,
  resetFcmTokenCache,
  sendFcm,
  type ServiceAccount,
} from "./fcm.ts";
import type { PushMessage } from "./push.ts";

const message: PushMessage = {
  userId: "u1",
  title: "Nouvelles envies pour Léa",
  body: "Marie a ajouté « Lego » à sa liste.",
  threadId: "new-items",
  data: { type: "new-items", child_id: "c1" },
};

async function makeAccount(): Promise<{ sa: ServiceAccount; publicKey: Awaited<ReturnType<typeof generateKeyPair>>["publicKey"] }> {
  const { publicKey, privateKey } = await generateKeyPair("RS256", { extractable: true });
  return {
    sa: { client_email: "push@proj.iam.gserviceaccount.com", private_key: await exportPKCS8(privateKey), project_id: "proj-1" },
    publicKey,
  };
}

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

Deno.test("buildFcmMessage : data-only, valeurs en chaînes, collapse_key", () => {
  const { message: m } = buildFcmMessage("tok", message);
  assertEquals(m.token, "tok");
  assertFalse("notification" in m);
  assertEquals(m.data, { type: "new-items", child_id: "c1", title: message.title, body: message.body });
  for (const value of Object.values(m.data)) assertEquals(typeof value, "string");
  assertEquals(m.android, { priority: "HIGH", collapse_key: "new-items" });
});

Deno.test("buildFcmMessage : priorité normale et title/body non écrasés par data", () => {
  const { message: m } = buildFcmMessage("tok", { ...message, priority: "normal", data: { title: "piège" } });
  assertEquals(m.android.priority, "NORMAL");
  assertEquals(m.data.title, message.title);
});

Deno.test("buildServiceAccountClaims", () => {
  const claims = buildServiceAccountClaims({ client_email: "a@b.c" }, 1000);
  assertEquals(claims, { iss: "a@b.c", scope: FCM_SCOPE, aud: OAUTH_TOKEN_URL, iat: 1000, exp: 4600 });
  assertEquals(FCM_SCOPE, "https://www.googleapis.com/auth/firebase.messaging");
  assertEquals(OAUTH_TOKEN_URL, "https://oauth2.googleapis.com/token");
  assert(claims.exp <= claims.iat + 3600);
});

Deno.test("parseServiceAccount", () => {
  assertEquals(parseServiceAccount(undefined), null);
  assertEquals(parseServiceAccount("pas du json"), null);
  assertEquals(parseServiceAccount('{"client_email":"a"}'), null);
  assertEquals(
    parseServiceAccount('{"client_email":"a","private_key":"k","project_id":"p","extra":1}'),
    { client_email: "a", private_key: "k", project_id: "p" },
  );
});

Deno.test("classifyFcmError : jeton périmé", () => {
  assertEquals(classifyFcmError(404, { error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }), "stale-token");
  assertEquals(classifyFcmError(404, null), "stale-token");
  assertEquals(
    classifyFcmError(400, { error: { status: "INVALID_ARGUMENT", message: "The registration token is not a valid FCM registration token" } }),
    "stale-token",
  );
  assertEquals(
    classifyFcmError(400, {
      error: { status: "INVALID_ARGUMENT", details: [{ fieldViolations: [{ field: "message.token" }] }] },
    }),
    "stale-token",
  );
});

Deno.test("classifyFcmError : autres erreurs, rien à supprimer", () => {
  for (const status of [401, 403, 429, 500, 503]) {
    assertEquals(classifyFcmError(status, { error: { status: "UNAVAILABLE" } }), "other");
  }
  assertEquals(classifyFcmError(400, { error: { status: "INVALID_ARGUMENT", message: "Invalid JSON payload" } }), "other");
  assertEquals(classifyFcmError(400, null), "other");
});

Deno.test("getAccessToken : JWT RS256 avec les bonnes claims, signature vérifiée", async () => {
  resetFcmTokenCache();
  const { sa, publicKey } = await makeAccount();
  let assertion = "", grantType = "", url = "";
  const fetchStub = (input: string | URL | Request, init?: RequestInit) => {
    url = String(input);
    const form = new URLSearchParams(String(init?.body));
    assertion = form.get("assertion")!;
    grantType = form.get("grant_type")!;
    return Promise.resolve(jsonResponse({ access_token: "ya29.abc", expires_in: 3600 }));
  };
  const token = await getAccessToken(sa, { fetch: fetchStub as typeof fetch, now: () => 1_700_000_000_000 });
  assertEquals(token, "ya29.abc");
  assertEquals(url, OAUTH_TOKEN_URL);
  assertEquals(grantType, "urn:ietf:params:oauth:grant-type:jwt-bearer");
  assertEquals(decodeProtectedHeader(assertion).alg, "RS256");
  const { payload } = await jwtVerify(assertion, publicKey, { currentDate: new Date(1_700_000_000_000) });
  assertEquals(payload.iss, sa.client_email);
  assertEquals(payload.scope, FCM_SCOPE);
  assertEquals(payload.aud, OAUTH_TOKEN_URL);
  assertEquals(payload.iat, 1_700_000_000);
  assertEquals(payload.exp, 1_700_003_600);
});

Deno.test("getAccessToken : un seul échange tant que le jeton est valide, puis renouvellement", async () => {
  resetFcmTokenCache();
  const { sa } = await makeAccount();
  let calls = 0;
  const fetchStub = () => {
    calls++;
    return Promise.resolve(jsonResponse({ access_token: `tok-${calls}`, expires_in: 3600 }));
  };
  let now = 1_700_000_000_000;
  const deps = { fetch: fetchStub as unknown as typeof fetch, now: () => now };
  assertEquals(await getAccessToken(sa, deps), "tok-1");
  now += 3000 * 1000;
  assertEquals(await getAccessToken(sa, deps), "tok-1");
  assertEquals(calls, 1);
  now += 541 * 1000; // au-delà de expires_in - 60 s
  assertEquals(await getAccessToken(sa, deps), "tok-2");
  assertEquals(calls, 2);
});

Deno.test("getAccessToken : échec de l'échange -> null, rien en cache", async () => {
  resetFcmTokenCache();
  const { sa } = await makeAccount();
  const failing = () => Promise.resolve(jsonResponse({ error: "invalid_grant" }, 400));
  assertEquals(await getAccessToken(sa, { fetch: failing as unknown as typeof fetch }), null);
  const ok = () => Promise.resolve(jsonResponse({ access_token: "t", expires_in: 3600 }));
  assertEquals(await getAccessToken(sa, { fetch: ok as unknown as typeof fetch }), "t");
});

Deno.test("sendFcm : URL du projet, en-tête Bearer, corps data-only", async () => {
  const { sa } = await makeAccount();
  let seen: { url: string; auth: string | null; body: string } | null = null;
  const fetchStub = (input: string | URL | Request, init?: RequestInit) => {
    seen = { url: String(input), auth: new Headers(init?.headers).get("authorization"), body: String(init?.body) };
    return Promise.resolve(jsonResponse({ name: "projects/proj-1/messages/1" }));
  };
  const result = await sendFcm(sa, "ya29.abc", "tok", message, { fetch: fetchStub as typeof fetch });
  assertEquals(result, { ok: true, stale: false });
  assertEquals(seen!.url, "https://fcm.googleapis.com/v1/projects/proj-1/messages:send");
  assertEquals(seen!.auth, "Bearer ya29.abc");
  assertEquals(JSON.parse(seen!.body), buildFcmMessage("tok", message));
});

Deno.test("sendFcm : 404 marque le jeton périmé, 503 et erreur réseau non", async () => {
  const { sa } = await makeAccount();
  const respond = (status: number, body: unknown) => () => Promise.resolve(jsonResponse(body, status));
  const stale = await sendFcm(sa, "t", "tok", message, {
    fetch: respond(404, { error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }) as unknown as typeof fetch,
  });
  assertEquals(stale, { ok: false, stale: true });
  const unavailable = await sendFcm(sa, "t", "tok", message, {
    fetch: respond(503, { error: { status: "UNAVAILABLE" } }) as unknown as typeof fetch,
  });
  assertEquals(unavailable, { ok: false, stale: false });
  const network = await sendFcm(sa, "t", "tok", message, { fetch: (() => Promise.reject(new Error("réseau"))) as typeof fetch });
  assertEquals(network, { ok: false, stale: false });
  assertNotEquals(stale.stale, unavailable.stale);
});

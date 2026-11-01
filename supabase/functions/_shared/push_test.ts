// Tests de sendPush : partition iOS / Android, fournisseurs indépendants, nettoyage des jetons, confidentialité.
import { assert, assertEquals, assertFalse } from "jsr:@std/assert@1";
import { exportPKCS8, generateKeyPair } from "npm:jose@5";
import { resetFcmTokenCache } from "./fcm.ts";
import { buildApnsPayload, type PushMessage, sendPush } from "./push.ts";

type Row = { token: string; environment: string; user_id: string; platform: string };

// Client Supabase factice : `from("device_tokens").select().in()` renvoie les lignes, `.delete().eq()` les retire.
function fakeAdmin(rows: Row[]) {
  const deleted: string[] = [];
  const admin = {
    from(table: string) {
      assertEquals(table, "device_tokens");
      return {
        select: () => ({ in: (_col: string, ids: string[]) => Promise.resolve({ data: rows.filter((r) => ids.includes(r.user_id)) }) }),
        delete: () => ({
          eq: (_col: string, token: string) => {
            deleted.push(token);
            return Promise.resolve({});
          },
        }),
      };
    },
  };
  // deno-lint-ignore no-explicit-any
  return { admin: admin as any, deleted };
}

const ENV_KEYS = ["APNS_KEY_ID", "APNS_TEAM_ID", "APNS_PRIVATE_KEY", "APNS_TOPIC", "FCM_SERVICE_ACCOUNT"];

async function withEnv(config: { apns: boolean; fcm: boolean }, run: () => Promise<void>) {
  const saved = ENV_KEYS.map((k) => [k, Deno.env.get(k)] as const);
  for (const k of ENV_KEYS) Deno.env.delete(k);
  try {
    if (config.apns) {
      const { privateKey } = await generateKeyPair("ES256", { extractable: true });
      Deno.env.set("APNS_KEY_ID", "KEYID12345");
      Deno.env.set("APNS_TEAM_ID", "TEAMID1234");
      Deno.env.set("APNS_TOPIC", "ch.jonathanlemaine.giftmanager");
      Deno.env.set("APNS_PRIVATE_KEY", await exportPKCS8(privateKey));
    }
    if (config.fcm) {
      const { privateKey } = await generateKeyPair("RS256", { extractable: true });
      Deno.env.set(
        "FCM_SERVICE_ACCOUNT",
        JSON.stringify({ client_email: "push@proj.iam.gserviceaccount.com", private_key: await exportPKCS8(privateKey), project_id: "proj-1" }),
      );
    }
    resetFcmTokenCache();
    await run();
  } finally {
    for (const [k, v] of saved) v === undefined ? Deno.env.delete(k) : Deno.env.set(k, v);
  }
}

type Call = { url: string; body: string };
// fetch factice : `statusFor` choisit la réponse selon l'URL / le jeton.
function fakeFetch(statusFor: (url: string, body: string) => number = () => 200) {
  const calls: Call[] = [];
  const impl = (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input), body = String(init?.body ?? "");
    calls.push({ url, body });
    if (url === "https://oauth2.googleapis.com/token") {
      return Promise.resolve(new Response(JSON.stringify({ access_token: "ya29.x", expires_in: 3600 }), { status: 200 }));
    }
    const status = statusFor(url, body);
    return Promise.resolve(new Response(status === 200 ? "{}" : JSON.stringify({ error: { status: "NOT_FOUND" } }), { status }));
  };
  return { fetch: impl as typeof fetch, calls };
}

const message = (userId: string, data?: Record<string, string>): PushMessage => ({
  userId, title: "Titre", body: "Corps", threadId: "new-items", data,
});
const iosRow = (token: string, user_id = "u1", environment = "production"): Row => ({ token, environment, user_id, platform: "ios" });
const androidRow = (token: string, user_id = "u1"): Row => ({ token, environment: "production", user_id, platform: "android" });

Deno.test("buildApnsPayload : payload historique + data à la racine, hors aps", () => {
  assertEquals(buildApnsPayload(message("u1")), {
    aps: { alert: { title: "Titre", body: "Corps" }, sound: "default", "thread-id": "new-items" },
  });
  const withData = buildApnsPayload(message("u1", { type: "price", item_id: "i1", aps: "piège" }));
  assertEquals(withData, {
    aps: { alert: { title: "Titre", body: "Corps" }, sound: "default", "thread-id": "new-items" },
    type: "price",
    item_id: "i1",
  });
});

Deno.test("sendPush : iOS vers APNs, Android vers FCM", async () => {
  await withEnv({ apns: true, fcm: true }, async () => {
    const { admin, deleted } = fakeAdmin([iosRow("ios-prod"), iosRow("ios-sbx", "u1", "sandbox"), androidRow("and-1")]);
    const { fetch, calls } = fakeFetch();
    const sent = await sendPush(admin, [message("u1", { type: "new-items", child_id: "c1" })], { fetch });
    assertEquals(sent, 3);
    assertEquals(deleted, []);
    const urls = calls.map((c) => c.url);
    assert(urls.includes("https://api.push.apple.com/3/device/ios-prod"));
    assert(urls.includes("https://api.sandbox.push.apple.com/3/device/ios-sbx"));
    assert(urls.includes("https://fcm.googleapis.com/v1/projects/proj-1/messages:send"));
    const apns = JSON.parse(calls.find((c) => c.url.endsWith("/ios-prod"))!.body);
    assertEquals(apns.type, "new-items");
    assertEquals(apns.child_id, "c1");
    const fcm = JSON.parse(calls.find((c) => c.url.includes("fcm.googleapis"))!.body);
    assertEquals(fcm.message.token, "and-1");
    assertFalse("notification" in fcm.message);
    assertEquals(fcm.message.data.child_id, "c1");
  });
});

Deno.test("sendPush : seuls les jetons du destinataire reçoivent son message", async () => {
  await withEnv({ apns: true, fcm: true }, async () => {
    const { admin } = fakeAdmin([iosRow("ios-u1", "u1"), androidRow("and-u2", "u2")]);
    const { fetch, calls } = fakeFetch();
    const sent = await sendPush(admin, [message("u1")], { fetch });
    assertEquals(sent, 1);
    assertFalse(calls.some((c) => c.url.includes("fcm.googleapis")));
  });
});

Deno.test("sendPush : APNs absent, FCM configuré", async () => {
  await withEnv({ apns: false, fcm: true }, async () => {
    const { admin } = fakeAdmin([iosRow("ios-1"), androidRow("and-1")]);
    const { fetch, calls } = fakeFetch();
    assertEquals(await sendPush(admin, [message("u1")], { fetch }), 1);
    assertFalse(calls.some((c) => c.url.includes("apple.com")));
  });
});

Deno.test("sendPush : FCM absent, APNs configuré", async () => {
  await withEnv({ apns: true, fcm: false }, async () => {
    const { admin } = fakeAdmin([iosRow("ios-1"), androidRow("and-1")]);
    const { fetch, calls } = fakeFetch();
    assertEquals(await sendPush(admin, [message("u1")], { fetch }), 1);
    assertFalse(calls.some((c) => c.url.includes("googleapis.com")));
  });
});

Deno.test("sendPush : rien de configuré -> 0, aucun appel", async () => {
  await withEnv({ apns: false, fcm: false }, async () => {
    const { admin } = fakeAdmin([iosRow("ios-1"), androidRow("and-1")]);
    const { fetch, calls } = fakeFetch();
    assertEquals(await sendPush(admin, [message("u1")], { fetch }), 0);
    assertEquals(calls.length, 0);
  });
});

Deno.test("sendPush : jetons périmés supprimés (APNs 410, FCM 404), erreurs transitoires conservées", async () => {
  await withEnv({ apns: true, fcm: true }, async () => {
    const { admin, deleted } = fakeAdmin([
      iosRow("ios-gone"), iosRow("ios-ok"), androidRow("and-gone"), androidRow("and-busy"), androidRow("and-ok"),
    ]);
    const { fetch } = fakeFetch((url, body) => {
      if (url.endsWith("/ios-gone")) return 410;
      if (body.includes('"and-gone"')) return 404;
      if (body.includes('"and-busy"')) return 503;
      return 200;
    });
    const sent = await sendPush(admin, [message("u1")], { fetch });
    assertEquals(sent, 2);
    assertEquals(deleted.sort(), ["and-gone", "ios-gone"]);
  });
});

Deno.test("sendPush : un seul échange OAuth pour plusieurs envois FCM", async () => {
  await withEnv({ apns: false, fcm: true }, async () => {
    const { admin } = fakeAdmin([androidRow("a1", "u1"), androidRow("a2", "u2")]);
    const { fetch, calls } = fakeFetch();
    assertEquals(await sendPush(admin, [message("u1"), message("u2")], { fetch }), 2);
    assertEquals(await sendPush(admin, [message("u1")], { fetch }), 1);
    assertEquals(calls.filter((c) => c.url === "https://oauth2.googleapis.com/token").length, 1);
  });
});

Deno.test("sendPush : échec de l'échange OAuth -> rien envoyé à Android, rien supprimé", async () => {
  await withEnv({ apns: false, fcm: true }, async () => {
    const { admin, deleted } = fakeAdmin([androidRow("a1")]);
    const failing = (() => Promise.resolve(new Response("{}", { status: 401 }))) as unknown as typeof fetch;
    assertEquals(await sendPush(admin, [message("u1")], { fetch: failing }), 0);
    assertEquals(deleted, []);
  });
});

// Confidentialité (#58) : la notification de réservation ne désigne pas le cadeau réservé.
Deno.test("notify-new-items : la réservation n'envoie aucun item_id", async () => {
  const source = await Deno.readTextFile(new URL("../notify-new-items/index.ts", import.meta.url));
  const line = source.split("\n").find((l) => l.includes('"reservation", {'));
  assert(line, "appel push de la réservation introuvable");
  assertFalse(line.includes("item_id"));
  assert(line.includes('type: "reservation"'));
});

// Envoi des notifications push (APNs pour iOS, FCM pour Android) et authentification des tâches planifiées,
// partagés par les fonctions Edge.
// APNs : APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY, APNS_TOPIC. FCM : FCM_SERVICE_ACCOUNT (voir fcm.ts).
// Chaque fournisseur est configuré indépendamment : sans clé APNs, les appareils Android sont quand même servis,
// et inversement.
//
// Convention de payload (`data`) : les apps s'en servent pour ouvrir le bon écran. Jamais de nom ni de titre
// de cadeau dans `data` (le texte visible est déjà dans title/body).
//   type        new-items | birthday | purchase | price | thanks | reservation | idea-review
//   child_id    new-items                   enfant dont la liste a changé
//   event_id    birthday, purchase          événement concerné
//   item_id     price, thanks, idea-review  cadeau concerné
// `reservation` ne porte volontairement AUCUN item_id : la notification reste anonyme et ne désigne pas le cadeau.
// Côté APNs, ces clés sont à la racine du payload (hors `aps`) ; côté FCM, elles vont dans `data` avec title et body.
import type { SupabaseClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";
import { getAccessToken, parseServiceAccount, sendFcm } from "./fcm.ts";

// Les appels viennent de pg_cron (pg_net) avec l'en-tête x-cron-secret = CRON_SECRET (secret de la fonction
// et secret Vault `cron_secret`, voir docs/SECRETS.md). Sans CRON_SECRET configuré, tout est refusé.
export function isCronRequest(req: Request): boolean {
  const expected = Deno.env.get("CRON_SECRET");
  const given = req.headers.get("x-cron-secret");
  if (!expected || !given || expected.length !== given.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ given.charCodeAt(i);
  return diff === 0;
}

export type PushMessage = {
  userId: string;
  title: string;
  body: string;
  threadId: string;
  data?: Record<string, string>;
  priority?: "high" | "normal";
};
export type PushDeps = { fetch: typeof fetch };

export function buildApnsPayload(message: PushMessage): Record<string, unknown> {
  const { aps: _ignored, ...data } = message.data ?? {};
  return {
    aps: { alert: { title: message.title, body: message.body }, sound: "default", "thread-id": message.threadId },
    ...data,
  };
}

type DeviceToken = { token: string; environment: string; user_id: string; platform: string };

export async function sendPush(
  admin: SupabaseClient,
  messages: PushMessage[],
  deps: PushDeps = { fetch },
): Promise<number> {
  const keyId = Deno.env.get("APNS_KEY_ID"), teamId = Deno.env.get("APNS_TEAM_ID");
  const pem = Deno.env.get("APNS_PRIVATE_KEY"), topic = Deno.env.get("APNS_TOPIC");
  const apnsReady = !!(keyId && teamId && pem && topic);
  const fcmAccount = parseServiceAccount(Deno.env.get("FCM_SERVICE_ACCOUNT"));
  if ((!apnsReady && !fcmAccount) || messages.length === 0) return 0;

  const userIds = [...new Set(messages.map((m) => m.userId))];
  const { data } = await admin.from("device_tokens").select("token, environment, user_id, platform").in("user_id", userIds);
  const tokens = (data ?? []) as DeviceToken[];
  if (!tokens.length) return 0;
  const ios = tokens.filter((t) => t.platform !== "android");
  const android = tokens.filter((t) => t.platform === "android");

  let sent = 0;
  const drop = async (token: string) => {
    await admin.from("device_tokens").delete().eq("token", token);
  };

  if (apnsReady && ios.length) {
    const jwt = await new SignJWT({ iss: teamId!, iat: Math.floor(Date.now() / 1000) })
      .setProtectedHeader({ alg: "ES256", kid: keyId! })
      .sign(await importPKCS8(pem!, "ES256"));
    for (const message of messages) {
      for (const { token, environment, user_id } of ios) {
        if (user_id !== message.userId) continue;
        const host = environment === "sandbox" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
        const res = await deps.fetch(`https://${host}/3/device/${token}`, {
          method: "POST",
          headers: {
            authorization: `bearer ${jwt}`,
            "apns-topic": topic!,
            "apns-push-type": "alert",
            "apns-priority": "5",
          },
          body: JSON.stringify(buildApnsPayload(message)),
          signal: AbortSignal.timeout(5000),
        }).catch(() => null);
        if (res?.ok) sent++;
        else if (res && (res.status === 410 || res.status === 400)) {
          // Jeton expiré ou invalide : on le retire.
          await drop(token);
        }
        await res?.body?.cancel();
      }
    }
  }

  if (fcmAccount && android.length) {
    const accessToken = await getAccessToken(fcmAccount, deps).catch(() => null);
    if (accessToken) {
      for (const message of messages) {
        for (const { token, user_id } of android) {
          if (user_id !== message.userId) continue;
          const result = await sendFcm(fcmAccount, accessToken, token, message, deps);
          if (result.ok) sent++;
          else if (result.stale) await drop(token);
        }
      }
    }
  }
  return sent;
}

export function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });
}

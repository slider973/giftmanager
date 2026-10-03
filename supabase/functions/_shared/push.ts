// Envoi APNs et authentification des tâches planifiées, partagés par les fonctions Edge cron.
// Mêmes secrets que notify-new-items : APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY, APNS_TOPIC.
// Sans clé APNs configurée, rien n'est envoyé.
import type { SupabaseClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

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

export type PushMessage = { userId: string; title: string; body: string; threadId: string };

export async function sendPush(admin: SupabaseClient, messages: PushMessage[]): Promise<number> {
  const keyId = Deno.env.get("APNS_KEY_ID"), teamId = Deno.env.get("APNS_TEAM_ID");
  const pem = Deno.env.get("APNS_PRIVATE_KEY"), topic = Deno.env.get("APNS_TOPIC");
  if (!keyId || !teamId || !pem || !topic || messages.length === 0) return 0;

  const userIds = [...new Set(messages.map((m) => m.userId))];
  const { data: tokens } = await admin.from("device_tokens").select("token, environment, user_id").in("user_id", userIds);
  if (!tokens?.length) return 0;

  const jwt = await new SignJWT({ iss: teamId, iat: Math.floor(Date.now() / 1000) })
    .setProtectedHeader({ alg: "ES256", kid: keyId })
    .sign(await importPKCS8(pem, "ES256"));

  let sent = 0;
  for (const message of messages) {
    for (const { token, environment, user_id } of tokens) {
      if (user_id !== message.userId) continue;
      const host = environment === "sandbox" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
      const res = await fetch(`https://${host}/3/device/${token}`, {
        method: "POST",
        headers: {
          authorization: `bearer ${jwt}`,
          "apns-topic": topic,
          "apns-push-type": "alert",
          "apns-priority": "5",
        },
        body: JSON.stringify({
          aps: { alert: { title: message.title, body: message.body }, sound: "default", "thread-id": message.threadId },
        }),
        signal: AbortSignal.timeout(5000),
      }).catch(() => null);
      if (res?.ok) sent++;
      else if (res && (res.status === 410 || res.status === 400)) {
        // Jeton expiré ou invalide : on le retire.
        await admin.from("device_tokens").delete().eq("token", token);
      }
    }
  }
  return sent;
}

export function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });
}

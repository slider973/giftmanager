// Notifie la famille quand de nouveaux cadeaux sont ajoutés pour un enfant (#15).
//
// Appelée par l'app après un ajout : { "item_ids": ["…"] } avec le JWT de l'utilisateur.
// Règles :
//  - l'appelant doit pouvoir modifier ces cadeaux (vérifié avec son propre JWT, donc via la RLS) ;
//  - destinataires : membres du groupe, sauf l'auteur ;
//  - pour une idée, les parents de l'enfant ne sont JAMAIS notifiés ;
//  - aucune notification ne parle de réservation.
//
// Secrets (supabase secrets set) : APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY (contenu du .p8), APNS_TOPIC (bundle ID).
// Sans clé APNs configurée, la fonction répond 200 sans rien envoyer.
import { createClient } from "npm:@supabase/supabase-js@2";
import { importPKCS8, SignJWT } from "npm:jose@5";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;

type Item = { id: string; child_id: string; kind: "wish" | "idea"; title: string; created_by: string | null };

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  const auth = req.headers.get("Authorization");
  if (!auth) return json({ error: "unauthorized" }, 401);

  const payload = await req.json().catch(() => ({}));
  if (payload.type === "thanks") return await notifyThanks(auth, payload.item_id);
  const { item_ids } = payload;
  if (!Array.isArray(item_ids) || item_ids.length === 0 || item_ids.length > 50) return json({ error: "item_ids" }, 400);

  // Client « utilisateur » : la RLS garantit qu'il voit ces cadeaux.
  const asUser = createClient(SUPABASE_URL, ANON_KEY, { global: { headers: { Authorization: auth } } });
  const { data: userData } = await asUser.auth.getUser();
  const caller = userData.user?.id;
  if (!caller) return json({ error: "unauthorized" }, 401);

  const admin = createClient(SUPABASE_URL, SERVICE_KEY);
  const { data: items } = await admin.from("wish_items")
    .select("id, child_id, kind, title, created_by").in("id", item_ids);
  const own = (items ?? []).filter((i: Item) => i.created_by === caller) as Item[];
  if (own.length === 0) return json({ sent: 0 });

  let sent = 0;
  for (const [childId, childItems] of groupBy(own, (i) => i.child_id)) {
    const { data: child } = await admin.from("children")
      .select("first_name, household_id, households!inner(group_id)").eq("id", childId).single();
    if (!child) continue;
    const groupId = (child as any).households.group_id as string;

    const { data: members } = await admin.from("group_members").select("user_id").eq("group_id", groupId);
    const { data: parents } = await admin.from("household_members").select("user_id").eq("household_id", child.household_id);
    const parentIds = new Set((parents ?? []).map((p) => p.user_id));
    const { data: author } = await admin.from("profiles").select("display_name").eq("id", caller).single();

    for (const kind of ["wish", "idea"] as const) {
      const batch = childItems.filter((i) => i.kind === kind);
      if (batch.length === 0) continue;
      const recipients = (members ?? []).map((m) => m.user_id)
        .filter((id) => id !== caller && !(kind === "idea" && parentIds.has(id)));
      if (recipients.length === 0) continue;

      const title = kind === "wish" ? `Nouvelles envies pour ${child.first_name}` : `Nouvelle idée pour ${child.first_name}`;
      const body = kind === "wish"
        ? `${author?.display_name || "Un parent"} a ajouté ${batch.length > 1 ? `${batch.length} cadeaux` : `« ${batch[0].title} »`} à sa liste.`
        : `${author?.display_name || "Quelqu'un"} propose ${batch.length > 1 ? `${batch.length} idées` : `« ${batch[0].title} »`} (invisible pour ses parents).`;
      sent += await push(admin, recipients, title, body);
    }
  }
  return json({ sent });
});

// Remerciement (#42) : seuls les donateurs du cadeau sont notifiés ; l'appelant doit être parent de l'enfant.
async function notifyThanks(auth: string, itemId: string): Promise<Response> {
  const asUser = createClient(SUPABASE_URL, ANON_KEY, { global: { headers: { Authorization: auth } } });
  const caller = (await asUser.auth.getUser()).data.user?.id;
  if (!caller || typeof itemId !== "string") return json({ error: "unauthorized" }, 401);
  const admin = createClient(SUPABASE_URL, SERVICE_KEY);
  const { data: item } = await admin.from("wish_items").select("id, title, child_id, children!inner(household_id)").eq("id", itemId).single();
  if (!item) return json({ sent: 0 });
  const { data: parent } = await admin.from("household_members").select("user_id")
    .eq("household_id", (item as any).children.household_id).eq("user_id", caller).maybeSingle();
  if (!parent) return json({ sent: 0 });
  const { data: reservation } = await admin.from("reservations").select("user_id").eq("item_id", itemId);
  const { data: contributions } = await admin.from("contributions").select("user_id").eq("item_id", itemId);
  const donors = [...new Set([...(reservation ?? []), ...(contributions ?? [])].map((d) => d.user_id))];
  const { data: author } = await admin.from("profiles").select("display_name").eq("id", caller).single();
  const sent = await push(admin, donors, "Un grand merci 🎁", `${author?.display_name || "Un parent"} te remercie pour « ${item.title} ».`);
  // Réponse identique qu'il y ait des donateurs ou non : le parent n'apprend rien.
  void sent;
  return json({ ok: true });
}

async function push(admin: ReturnType<typeof createClient>, userIds: string[], title: string, body: string): Promise<number> {
  const keyId = Deno.env.get("APNS_KEY_ID"), teamId = Deno.env.get("APNS_TEAM_ID");
  const pem = Deno.env.get("APNS_PRIVATE_KEY"), topic = Deno.env.get("APNS_TOPIC");
  if (!keyId || !teamId || !pem || !topic) return 0;

  const { data: tokens } = await admin.from("device_tokens").select("token, environment").in("user_id", userIds);
  if (!tokens?.length) return 0;

  const jwt = await new SignJWT({ iss: teamId, iat: Math.floor(Date.now() / 1000) })
    .setProtectedHeader({ alg: "ES256", kid: keyId })
    .sign(await importPKCS8(pem, "ES256"));

  let sent = 0;
  for (const { token, environment } of tokens) {
    const host = environment === "sandbox" ? "api.sandbox.push.apple.com" : "api.push.apple.com";
    const res = await fetch(`https://${host}/3/device/${token}`, {
      method: "POST",
      headers: {
        authorization: `bearer ${jwt}`,
        "apns-topic": topic,
        "apns-push-type": "alert",
        "apns-priority": "5",
      },
      body: JSON.stringify({ aps: { alert: { title, body }, sound: "default", "thread-id": "new-items" } }),
    });
    if (res.ok) sent++;
    else if (res.status === 410 || res.status === 400) {
      // Jeton expiré ou invalide : on le retire.
      await admin.from("device_tokens").delete().eq("token", token);
    }
  }
  return sent;
}

function groupBy<T>(list: T[], key: (t: T) => string): Map<string, T[]> {
  const map = new Map<string, T[]>();
  for (const item of list) map.set(key(item), [...(map.get(key(item)) ?? []), item]);
  return map;
}

function json(payload: unknown, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { "Content-Type": "application/json" } });
}

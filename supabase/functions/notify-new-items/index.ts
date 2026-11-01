// Notifie la famille quand de nouveaux cadeaux sont ajoutés pour un enfant (#15).
//
// Appelée par l'app après un ajout : { "item_ids": ["…"] } avec le JWT de l'utilisateur.
// Règles :
//  - l'appelant doit pouvoir modifier ces cadeaux (vérifié avec son propre JWT, donc via la RLS) ;
//  - destinataires : membres du groupe, sauf l'auteur ;
//  - pour une idée, les parents de l'enfant ne sont JAMAIS notifiés ;
//  - aucune notification ne parle de réservation.
//
// Secrets (supabase secrets set) : APNS_KEY_ID, APNS_TEAM_ID, APNS_PRIVATE_KEY (contenu du .p8), APNS_TOPIC (bundle ID)
// pour iOS ; FCM_SERVICE_ACCOUNT pour Android. Sans aucun fournisseur configuré, la fonction répond 200 sans rien envoyer.
import { createClient } from "npm:@supabase/supabase-js@2";
import { json, sendPush } from "../_shared/push.ts";

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
  if (payload.type === "reservation") return await notifyReservation(auth, payload.item_id);
  if (payload.type === "idea_review") return await notifyIdeaReview(auth, payload.item_id, payload.decision);
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
      sent += await push(admin, recipients, title, body, "new-items", { type: "new-items", child_id: childId });
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
  // Seulement juste après un vrai remerciement de l'appelant pour ce cadeau (anti-spam des donateurs).
  const since = new Date(Date.now() - 10 * 60 * 1000).toISOString();
  const { data: recent } = await admin.from("thanks").select("id")
    .eq("item_id", itemId).eq("sender_id", caller).gte("created_at", since).limit(1);
  if (!recent?.length) return json({ ok: true });
  const { data: reservation } = await admin.from("reservations").select("user_id").eq("item_id", itemId);
  const { data: contributions } = await admin.from("contributions").select("user_id").eq("item_id", itemId);
  const donors = [...new Set([...(reservation ?? []), ...(contributions ?? [])].map((d) => d.user_id))];
  const { data: author } = await admin.from("profiles").select("display_name").eq("id", caller).single();
  const sent = await push(
    admin, donors, "Un grand merci 🎁", `${author?.display_name || "Un parent"} te remercie pour « ${item.title} ».`,
    `thanks-${itemId}`, { type: "thanks", item_id: itemId },
  );
  // Réponse identique qu'il y ait des donateurs ou non : le parent n'apprend rien.
  void sent;
  return json({ ok: true });
}

// Réservation (#58) : alerte anonyme aux membres qui ne sont ni parents ni l'acheteur.
// Le message ne nomme personne et ne cite aucun titre de cadeau : il sert seulement à
// éviter qu'on achète deux fois la même chose. Le serveur décide seul des destinataires
// et de la fenêtre d'agrégation (claim_reservation_notice).
async function notifyReservation(auth: string, itemId: string): Promise<Response> {
  const asUser = createClient(SUPABASE_URL, ANON_KEY, { global: { headers: { Authorization: auth } } });
  const caller = (await asUser.auth.getUser()).data.user?.id;
  if (!caller || typeof itemId !== "string") return json({ error: "unauthorized" }, 401);

  const admin = createClient(SUPABASE_URL, SERVICE_KEY);
  const { data: rows } = await admin.rpc("claim_reservation_notice", { p_item: itemId, p_user: caller });
  if (!rows?.length) return json({ ok: true });

  const first = rows[0] as { child_name: string; item_count: number; available_count: number };
  const recipients = (rows as { user_id: string }[]).map((r) => r.user_id);
  const title = `Liste de ${first.child_name}`;
  const taken = first.item_count > 1
    ? `${first.item_count} cadeaux viennent d'être réservés.`
    : "Un cadeau vient d'être réservé.";
  const left = first.available_count > 0
    ? ` Il en reste ${first.available_count} disponible${first.available_count > 1 ? "s" : ""}.`
    : " Il n'en reste aucun de disponible.";
  // Pas d'item_id dans le payload : la notification ne désigne pas le cadeau réservé.
  await push(admin, recipients, title, taken + left, "reservation", { type: "reservation" });
  // Réponse constante : l'appelant n'apprend ni qui a été notifié, ni combien.
  return json({ ok: true });
}

// Verdict sur une idée (#57) : seul son auteur est informé, jamais le reste de la famille.
async function notifyIdeaReview(auth: string, itemId: string, decision: string): Promise<Response> {
  const asUser = createClient(SUPABASE_URL, ANON_KEY, { global: { headers: { Authorization: auth } } });
  const caller = (await asUser.auth.getUser()).data.user?.id;
  if (!caller || typeof itemId !== "string") return json({ error: "unauthorized" }, 401);

  const admin = createClient(SUPABASE_URL, SERVICE_KEY);
  const { data: item } = await admin.from("wish_items")
    .select("id, title, created_by, child_id, review_status, reviewed_by, children!inner(first_name, household_id)")
    .eq("id", itemId).single();
  if (!item) return json({ ok: true });
  // Seul le parent qui vient de trancher déclenche l'envoi.
  if ((item as any).reviewed_by !== caller) return json({ ok: true });
  const author = (item as any).created_by as string | null;
  if (!author || author === caller) return json({ ok: true });

  const childName = (item as any).children.first_name as string;
  const verdicts: Record<string, string> = {
    accepted: `Ton idée « ${item.title} » a été acceptée : elle rejoint la liste de ${childName}.`,
    rejected: `Ton idée « ${item.title} » n'a pas été retenue pour ${childName}.`,
    owned_already: `${childName} possède déjà « ${item.title} » : ton idée a été écartée.`,
  };
  const body = verdicts[decision] ?? verdicts.rejected;
  await push(admin, [author], "Réponse à ta proposition", body, `idea-review-${itemId}`, { type: "idea-review", item_id: itemId });
  return json({ ok: true });
}

// L'envoi (APNs / FCM, nettoyage des jetons périmés) est dans _shared/push.ts.
function push(
  admin: Parameters<typeof sendPush>[0], userIds: string[], title: string, body: string,
  threadId: string, data: Record<string, string>,
): Promise<number> {
  return sendPush(admin, userIds.map((userId) => ({ userId, title, body, threadId, data })));
}

function groupBy<T>(list: T[], key: (t: T) => string): Map<string, T[]> {
  const map = new Map<string, T[]>();
  for (const item of list) map.set(key(item), [...(map.get(key(item)) ?? []), item]);
  return map;
}

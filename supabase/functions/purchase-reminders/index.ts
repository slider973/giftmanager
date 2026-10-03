// Rappels d'achat J-30 / J-15 / J-7 / J-2 (#59). Appelée toutes les heures par pg_cron.
//
// Les rappels étaient purement locaux : ils ne partaient que si l'app avait été ouverte à temps.
// Ici chaque membre est averti de SES propres réservations encore à acheter — personne d'autre
// n'apprend quoi que ce soit, et les parents ne reçoivent rien sur les listes de leurs enfants.
//
// Le filtrage (18 h dans le fuseau du destinataire, préférence du profil, idempotence) est fait
// en SQL par claim_purchase_reminders : relancer la fonction ne renvoie rien de plus.
import { createClient } from "npm:@supabase/supabase-js@2";
import { isCronRequest, json, sendPush } from "../_shared/push.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!isCronRequest(req)) return json({ error: "unauthorized" }, 401);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await admin.rpc("claim_purchase_reminders");
  if (error) return json({ error: "claim" }, 500);

  const reminders = (data ?? []) as {
    user_id: string;
    event_id: string;
    event_title: string;
    days_before: number;
    item_count: number;
  }[];

  const sent = await sendPush(admin, reminders.map((r) => ({
    userId: r.user_id,
    title: `${r.event_title} dans ${r.days_before} jour${r.days_before > 1 ? "s" : ""}`,
    body: r.item_count > 1
      ? `Il te reste ${r.item_count} cadeaux réservés à acheter.`
      : "Il te reste un cadeau réservé à acheter.",
    threadId: `purchase-${r.event_id}`,
  })));
  return json({ reminders: reminders.length, sent });
});

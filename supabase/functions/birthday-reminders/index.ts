// Rappels d'anniversaires J-30 / J-7 (#43). Appelée chaque jour par pg_cron (voir migration birthday_reminders).
//
// Les destinataires sont calculés en SQL (claim_birthday_reminders) : membres du groupe hors foyer de
// l'enfant (ses parents, ou l'adulte et son conjoint), préférence « notify_birthday_reminders » respectée.
// Chaque rappel n'est réservé qu'une fois : relancer la fonction ne renvoie rien de plus.
import { createClient } from "npm:@supabase/supabase-js@2";
import { isCronRequest, json, sendPush } from "../_shared/push.ts";

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!isCronRequest(req)) return json({ error: "unauthorized" }, 401);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data, error } = await admin.rpc("claim_birthday_reminders");
  if (error) return json({ error: "claim" }, 500);

  const reminders = (data ?? []) as { event_id: string; child_name: string; days_before: number; user_id: string }[];
  const sent = await sendPush(admin, reminders.map((r) => ({
    userId: r.user_id,
    title: `Anniversaire de ${r.child_name}`,
    body: r.days_before === 7
      ? `C'est dans une semaine : il est temps de choisir un cadeau pour ${r.child_name}.`
      : `C'est dans un mois : pense à regarder la liste de ${r.child_name}.`,
    threadId: `birthday-${r.event_id}`,
  })));
  return json({ reminders: reminders.length, sent });
});

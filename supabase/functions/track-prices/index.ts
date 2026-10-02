// Suivi des prix et du stock des cadeaux réservés non achetés (#39). Appelée chaque jour par pg_cron.
//
// Pour chaque lien retourné par links_to_check : relecture de la page (timeout court, échecs tolérés :
// Amazon et d'autres bloquent souvent), puis record_price_check, qui décide en SQL s'il y a une notification
// et pour qui : uniquement l'auteur de la réservation, jamais les parents, jamais deux fois le même changement.
// Le prix saisi par les parents sur le lien n'est jamais modifié.
import { createClient } from "npm:@supabase/supabase-js@2";
import { isCronRequest, json, sendPush } from "../_shared/push.ts";
import { parseProductPage } from "./parse.ts";
import { fetchPageSafely } from "./safe_fetch.ts";

const FETCH_TIMEOUT_MS = 6000;
const MAX_HTML_BYTES = 1_500_000;
const MAX_LINKS = 200;
const CONCURRENCY = 5;
const TIME_BUDGET_MS = 100_000;

type Alert = {
  kind: string; user_id: string; item_id: string; title: string;
  old_price: number | null; new_price: number | null; currency: string | null;
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!isCronRequest(req)) return json({ error: "unauthorized" }, 401);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: links, error } = await admin.rpc("links_to_check", { p_limit: MAX_LINKS });
  if (error) return json({ error: "links" }, 500);

  const queue = [...((links ?? []) as { link_id: string; url: string }[])];
  const deadline = Date.now() + TIME_BUDGET_MS;
  let checked = 0, failed = 0, notifications = 0, sent = 0;

  // Petit pool de workers ; chaque lien est traité (et notifié) indépendamment. Au-delà du budget de temps,
  // les liens restants sont repris au prochain passage (rotation par date de dernière tentative).
  const worker = async () => {
    for (let link = queue.shift(); link && Date.now() < deadline; link = queue.shift()) {
      try {
        const html = await fetchPageSafely(link.url, { timeoutMs: FETCH_TIMEOUT_MS, maxBytes: MAX_HTML_BYTES });
        const offer = html === null ? null : parseProductPage(html);
        if (!offer || (offer.price === null && offer.inStock === null)) {
          failed++;
          await admin.rpc("mark_link_checked", { p_link: link.link_id });
          continue;
        }
        const { data: alerts } = await admin.rpc("record_price_check", {
          p_link: link.link_id, p_price: offer.price, p_currency: offer.currency, p_in_stock: offer.inStock,
        });
        checked++;
        for (const a of (alerts ?? []) as Alert[]) {
          notifications++;
          sent += await sendPush(admin, [a.kind === "price_drop"
            ? {
              userId: a.user_id, threadId: `price-${a.item_id}`, title: "Prix en baisse",
              body: `« ${a.title} » passe de ${a.old_price} à ${a.new_price} ${a.currency ?? ""}`.trim() + ".",
            }
            : {
              userId: a.user_id, threadId: `price-${a.item_id}`, title: "Plus en stock",
              body: `« ${a.title} » n'est plus disponible sur le site que tu avais choisi.`,
            }]);
        }
      } catch {
        failed++;
      }
    }
  };
  await Promise.all(Array.from({ length: CONCURRENCY }, worker));
  return json({ checked, failed, notifications, sent, remaining: queue.length });
});

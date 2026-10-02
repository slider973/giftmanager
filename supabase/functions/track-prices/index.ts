// Suivi des prix et du stock des cadeaux réservés non achetés (#39). Appelée chaque jour par pg_cron.
//
// Pour chaque lien retourné par links_to_check : relecture de la page (timeout court, échecs tolérés :
// Amazon et d'autres bloquent souvent), puis record_price_check, qui décide en SQL s'il y a une notification
// et pour qui : uniquement l'auteur de la réservation, jamais les parents, jamais deux fois le même changement.
// Le prix saisi par les parents sur le lien n'est jamais modifié.
import { createClient } from "npm:@supabase/supabase-js@2";
import { isCronRequest, json, type PushMessage, sendPush } from "../_shared/push.ts";
import { parseProductPage } from "./parse.ts";

const FETCH_TIMEOUT_MS = 6000;
const MAX_HTML_BYTES = 1_500_000;
const MAX_LINKS = 200;

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method" }, 405);
  if (!isCronRequest(req)) return json({ error: "unauthorized" }, 401);

  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
  const { data: links, error } = await admin.rpc("links_to_check", { p_limit: MAX_LINKS });
  if (error) return json({ error: "links" }, 500);

  const messages: PushMessage[] = [];
  let checked = 0, failed = 0;
  for (const link of (links ?? []) as { link_id: string; url: string }[]) {
    const html = await fetchPage(link.url);
    if (html === null) { failed++; continue; }
    const offer = parseProductPage(html);
    if (offer.price === null && offer.inStock === null) { failed++; continue; }
    const { data: alerts } = await admin.rpc("record_price_check", {
      p_link: link.link_id, p_price: offer.price, p_currency: offer.currency, p_in_stock: offer.inStock,
    });
    checked++;
    for (const a of (alerts ?? []) as {
      kind: string; user_id: string; item_id: string; title: string; old_price: number | null; new_price: number | null; currency: string | null;
    }[]) {
      messages.push(a.kind === "price_drop"
        ? {
          userId: a.user_id, threadId: `price-${a.item_id}`, title: "Prix en baisse",
          body: `« ${a.title} » passe de ${a.old_price} à ${a.new_price} ${a.currency ?? ""}`.trim() + ".",
        }
        : {
          userId: a.user_id, threadId: `price-${a.item_id}`, title: "Plus en stock",
          body: `« ${a.title} » n'est plus disponible sur le site que tu avais choisi.`,
        });
    }
  }
  const sent = await sendPush(admin, messages);
  return json({ checked, failed, notifications: messages.length, sent });
});

// Relit une page ; null en cas d'échec (réseau, blocage, timeout, type de contenu inattendu).
async function fetchPage(url: string): Promise<string | null> {
  try {
    const { protocol } = new URL(url);
    if (protocol !== "https:" && protocol !== "http:") return null;
    const res = await fetch(url, {
      redirect: "follow",
      signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
      headers: {
        "user-agent": "Mozilla/5.0 (compatible; GiftManagerBot/1.0)",
        accept: "text/html,application/xhtml+xml",
        "accept-language": "fr,en;q=0.8",
      },
    });
    if (!res.ok || !(res.headers.get("content-type") ?? "").includes("html")) return null;
    return (await res.text()).slice(0, MAX_HTML_BYTES);
  } catch {
    return null;
  }
}

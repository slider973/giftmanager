// Extraction du prix et de la disponibilité d'une page produit : JSON-LD (offers.price / availability),
// puis balises meta produit (og/product:price:amount, itemprop). Tout est optionnel : on renvoie ce qu'on trouve.

export type Offer = { price: number | null; currency: string | null; inStock: boolean | null };

export function parseProductPage(html: string): Offer {
  const fromLd = fromJsonLd(html);
  const fromMeta = fromMetaTags(html);
  return {
    price: fromLd.price ?? fromMeta.price,
    currency: fromLd.currency ?? fromMeta.currency,
    inStock: fromLd.inStock ?? fromMeta.inStock,
  };
}

function fromJsonLd(html: string): Offer {
  const result: Offer = { price: null, currency: null, inStock: null };
  const scripts = html.matchAll(/<script[^>]+type=["']application\/ld\+json["'][^>]*>([\s\S]*?)<\/script>/gi);
  for (const [, raw] of scripts) {
    let data: unknown;
    try {
      data = JSON.parse(raw.trim());
    } catch {
      continue;
    }
    for (const offer of collectOffers(data)) {
      const price = toPrice(offer.price ?? offer.lowPrice);
      if (result.price === null && price !== null) {
        result.price = price;
        result.currency = typeof offer.priceCurrency === "string" ? offer.priceCurrency.toUpperCase() : null;
      }
      const stock = toStock(offer.availability);
      if (result.inStock === null && stock !== null) result.inStock = stock;
    }
    if (result.price !== null && result.inStock !== null) break;
  }
  return result;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function collectOffers(node: any, depth = 0): any[] {
  if (!node || typeof node !== "object" || depth > 6) return [];
  if (Array.isArray(node)) return node.flatMap((n) => collectOffers(n, depth + 1));
  const found: any[] = [];
  if (node.offers) found.push(...[node.offers].flat().filter((o) => o && typeof o === "object"));
  if (node["@graph"]) found.push(...collectOffers(node["@graph"], depth + 1));
  // Offres imbriquées (AggregateOffer.offers).
  for (const offer of [...found]) if (offer.offers) found.push(...collectOffers(offer, depth + 1));
  return found;
}

function fromMetaTags(html: string): Offer {
  const meta = (names: string[]): string | null => {
    for (const tag of html.matchAll(/<meta\s[^>]*>/gi)) {
      const attrs = tag[0];
      const key = /(?:property|name|itemprop)=["']([^"']+)["']/i.exec(attrs)?.[1]?.toLowerCase();
      const value = /content=["']([^"']*)["']/i.exec(attrs)?.[1];
      if (key && value && names.includes(key)) return value;
    }
    return null;
  };
  const price = toPrice(meta(["product:price:amount", "og:price:amount", "price"]));
  const currency = meta(["product:price:currency", "og:price:currency", "pricecurrency"]);
  const availability = meta(["product:availability", "og:availability", "availability"]);
  return { price, currency: currency?.toUpperCase() ?? null, inStock: toStock(availability) };
}

export function toPrice(value: unknown): number | null {
  if (typeof value === "number") return Number.isFinite(value) && value >= 0 ? value : null;
  if (typeof value !== "string") return null;
  let s = value.trim().replace(/[^\d.,]/g, "");
  if (!s) return null;
  // « 1.299,90 » / « 1,299.90 » / « 12,5 » : le dernier séparateur est décimal.
  const lastComma = s.lastIndexOf(","), lastDot = s.lastIndexOf(".");
  if (lastComma > lastDot) s = s.replace(/\./g, "").replace(",", ".");
  else s = s.replace(/,/g, "");
  const n = Number(s);
  return Number.isFinite(n) && n >= 0 ? n : null;
}

export function toStock(value: unknown): boolean | null {
  if (typeof value !== "string") return null;
  const v = value.toLowerCase();
  if (/outofstock|soldout|discontinued|out of stock/.test(v)) return false;
  if (/instock|limitedavailability|onlineonly|instoreonly|preorder|in stock/.test(v)) return true;
  return null;
}

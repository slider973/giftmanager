import { assertEquals } from "jsr:@std/assert@1";
import { parseProductPage, toPrice } from "./parse.ts";

Deno.test("JSON-LD : prix, devise et disponibilité", () => {
  const html = `<script type="application/ld+json">{"@type":"Product","offers":{"@type":"Offer","price":"89.90","priceCurrency":"chf","availability":"https://schema.org/InStock"}}</script>`;
  assertEquals(parseProductPage(html), { price: 89.9, currency: "CHF", inStock: true });
});

Deno.test("JSON-LD : @graph et rupture de stock", () => {
  const html = `<script type="application/ld+json">{"@graph":[{"@type":"Product","offers":[{"price":12,"priceCurrency":"EUR","availability":"OutOfStock"}]}]}</script>`;
  assertEquals(parseProductPage(html), { price: 12, currency: "EUR", inStock: false });
});

Deno.test("balises meta produit en repli", () => {
  const html = `<meta property="product:price:amount" content="1.299,90"><meta property="product:price:currency" content="EUR"><meta property="og:availability" content="out of stock">`;
  assertEquals(parseProductPage(html), { price: 1299.9, currency: "EUR", inStock: false });
});

Deno.test("page sans information ni JSON invalide : rien, sans erreur", () => {
  assertEquals(parseProductPage(`<script type="application/ld+json">{oops</script><p>hello</p>`), { price: null, currency: null, inStock: null });
});

Deno.test("formats de prix", () => {
  assertEquals(toPrice("CHF 12.50"), 12.5);
  assertEquals(toPrice("1,299.90"), 1299.9);
  assertEquals(toPrice("abc"), null);
});

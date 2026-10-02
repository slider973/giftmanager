import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { assertSafeUrl, type Deps, fetchPageSafely, isBlockedHostname, isPrivateIp, readLimited } from "./safe_fetch.ts";

Deno.test("noms d'hôte refusés", () => {
  for (const h of ["localhost", "foo.localhost", "printer.local", "db.internal", "intranet", "127.0.0.1", "2130706433",
    "0x7f.0.0.1", "0x7f000001", "017700000001", "10.0.0.1", "[::1]", "::1", "metadata.internal.", "1.2.3.4"]) {
    assertEquals(isBlockedHostname(h), true, h);
  }
  for (const h of ["www.amazon.fr", "shop.example.com", "a1.example.co.uk"]) assertEquals(isBlockedHostname(h), false, h);
});

Deno.test("adresses privées refusées", () => {
  for (const ip of ["10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.1.1", "127.0.0.1", "169.254.169.254", "100.64.0.1",
    "100.127.255.255", "0.0.0.0", "224.0.0.1", "255.255.255.255", "::1", "::", "fc00::1", "fd12:3456::1", "fe80::1", "ff02::1",
    "::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:7f00:1", "::ffff:a9fe:a9fe", "64:ff9b::7f00:1", "2002:7f00:1::", "garbage"]) {
    assertEquals(isPrivateIp(ip), true, ip);
  }
  for (const ip of ["8.8.8.8", "93.184.216.34", "172.32.0.1", "100.128.0.1", "2606:4700:4700::1111", "::ffff:8.8.8.8", "2a00:1450:4001::200e"]) {
    assertEquals(isPrivateIp(ip), false, ip);
  }
});

const deps = (dns: Record<string, string[]>, responses: Record<string, Response> = {}): Deps & { calls: string[] } => {
  const calls: string[] = [];
  return {
    calls,
    resolve: (host, type) => Promise.resolve(type === "A" ? (dns[host] ?? []).filter((a) => !a.includes(":")) : (dns[host] ?? []).filter((a) => a.includes(":"))),
    fetch: (input) => {
      const href = String(input);
      calls.push(href);
      const r = responses[href];
      return Promise.resolve(r ?? new Response("nope", { status: 404 }));
    },
  };
};

Deno.test("assertSafeUrl : schéma, port, identifiants, DNS privé", async () => {
  const d = deps({ "shop.example.com": ["93.184.216.34"], "evil.example.com": ["10.0.0.5"], "mixed.example.com": ["93.184.216.34", "::1"] });
  await assertSafeUrl("https://shop.example.com/p", d);
  await assertSafeUrl("http://shop.example.com:80/p", d);
  for (const u of ["ftp://shop.example.com/", "https://shop.example.com:8443/", "https://user:pw@shop.example.com/",
    "https://evil.example.com/", "https://mixed.example.com/", "https://unknown.example.com/", "http://127.0.0.1/", "https://localhost/"]) {
    await assertRejects(() => assertSafeUrl(u, d), Error, undefined, u);
  }
});

Deno.test("redirection vers une adresse interne : bloquée au saut", async () => {
  const d = deps({ "shop.example.com": ["93.184.216.34"], "evil.example.com": ["169.254.169.254"] }, {
    "https://shop.example.com/p": new Response(null, { status: 302, headers: { location: "https://evil.example.com/secret" } }),
    "https://evil.example.com/secret": new Response("<html>secret</html>", { headers: { "content-type": "text/html" } }),
  });
  assertEquals(await fetchPageSafely("https://shop.example.com/p", { timeoutMs: 1000, maxBytes: 1000 }, d), null);
  assertEquals(d.calls, ["https://shop.example.com/p"]);
});

Deno.test("redirection légitime suivie ; boucle de redirections coupée à 5 sauts", async () => {
  const ok = deps({ "a.example.com": ["93.184.216.34"], "b.example.com": ["93.184.216.35"] }, {
    "https://a.example.com/": new Response(null, { status: 301, headers: { location: "https://b.example.com/x" } }),
    "https://b.example.com/x": new Response("<html>ok</html>", { headers: { "content-type": "text/html; charset=utf-8" } }),
  });
  assertEquals(await fetchPageSafely("https://a.example.com/", { timeoutMs: 1000, maxBytes: 1000 }, ok), "<html>ok</html>");

  const loop = deps({ "a.example.com": ["93.184.216.34"] });
  loop.fetch = (input) => {
    loop.calls.push(String(input));
    return Promise.resolve(new Response(null, { status: 302, headers: { location: "https://a.example.com/again" } }));
  };
  assertEquals(await fetchPageSafely("https://a.example.com/", { timeoutMs: 1000, maxBytes: 1000 }, loop), null);
  assertEquals(loop.calls.length, 6);
});

Deno.test("lecture bornée : flux tronqué, content-length refusé", async () => {
  const big = new Response(new Blob(["x".repeat(5000)]).stream(), { headers: { "content-type": "text/html" } });
  assertEquals((await readLimited(big, 100))?.length, 100);
  const declared = new Response("small", { headers: { "content-length": "999999" } });
  assertEquals(await readLimited(declared, 100), null);
  assertEquals(await readLimited(new Response("abc"), 100), "abc");
});

// Récupération de pages protégée contre le SSRF : les liens sont saisis par des utilisateurs, la fonction
// ne doit jamais atteindre le réseau interne. Redirections suivies à la main (chaque saut revalidé),
// ports 80/443 seulement, pas d'IP littérale ni de noms internes, résolution DNS filtrée, lecture bornée.
//
// Limite connue : fetch() résout le nom une seconde fois ; un DNS malveillant pourrait répondre autrement
// (rebinding). Le filtrage DNS préalable réduit fortement le risque sans l'éliminer.

export type Deps = {
  resolve: (host: string, type: "A" | "AAAA") => Promise<string[]>;
  fetch: typeof fetch;
};

const defaultDeps: Deps = {
  resolve: (host, type) => Deno.resolveDns(host, type),
  fetch: (input, init) => fetch(input, init),
};

export const MAX_REDIRECTS = 5;

// ---------------------------------------------------------------- noms d'hôte

export function isBlockedHostname(hostname: string): boolean {
  const h = hostname.toLowerCase().replace(/\.$/, "");
  if (!h || h.includes(":") || h.startsWith("[")) return true; // IPv6 littérale
  if (h === "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true;
  if (!h.includes(".")) return true; // nom à une seule étiquette (intranet)
  // IPv4 littérale sous toutes ses formes (décimal, hexadécimal, octal, pointée) : dernière étiquette numérique.
  const last = h.slice(h.lastIndexOf(".") + 1);
  if (/^(0x[0-9a-f]*|\d+)$/i.test(last)) return true;
  return false;
}

// ---------------------------------------------------------------- adresses IP

export function isPrivateIp(ip: string): boolean {
  const v = ip.trim().toLowerCase();
  if (v.includes(":")) return isPrivateV6(v);
  const parts = parseV4(v);
  return parts === null ? true : isPrivateV4(parts);
}

function parseV4(s: string): number[] | null {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(s);
  if (!m) return null;
  const parts = m.slice(1).map(Number);
  return parts.every((n) => n <= 255) ? parts : null;
}

function isPrivateV4([a, b]: number[]): boolean {
  return a === 0 || a === 10 || a === 127 ||
    (a === 100 && b >= 64 && b <= 127) || // CGNAT
    (a === 169 && b === 254) ||
    (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) ||
    (a === 192 && b === 0) || // 192.0.0.0/24 et 192.0.2.0/24 (réservés)
    (a === 198 && (b === 18 || b === 19)) ||
    a >= 224; // multicast, réservé, broadcast
}

function isPrivateV6(ip: string): boolean {
  const groups = expandV6(ip);
  if (groups === null) return true;
  const [g0, g1, g2, g3, g4, g5, g6, g7] = groups;
  if (groups.every((g) => g === 0)) return true; // ::
  if (groups.slice(0, 7).every((g) => g === 0) && g7 === 1) return true; // ::1
  // ::ffff:a.b.c.d (mappée) et ::a.b.c.d (compatible) : on juge l'IPv4 contenue.
  const embedded = [g6 >> 8, g6 & 255, g7 >> 8, g7 & 255];
  if (g0 === 0 && g1 === 0 && g2 === 0 && g3 === 0 && g4 === 0 && (g5 === 0xffff || g5 === 0)) return isPrivateV4(embedded);
  if (g0 === 0x64 && g1 === 0xff9b && g2 === 0 && g3 === 0 && g4 === 0 && g5 === 0) return isPrivateV4(embedded); // NAT64
  if ((g0 & 0xfe00) === 0xfc00) return true; // fc00::/7 (ULA)
  if ((g0 & 0xffc0) === 0xfe80) return true; // fe80::/10 (lien local)
  if ((g0 & 0xffc0) === 0xfec0) return true; // fec0::/10 (site local, obsolète)
  if ((g0 & 0xff00) === 0xff00) return true; // multicast
  if (g0 === 0x2001 && g1 === 0x0db8) return true; // documentation
  if (g0 === 0x2002) return isPrivateV4([g1 >> 8, g1 & 255]); // 6to4
  return false;
}

// Développe une IPv6 textuelle en 8 groupes de 16 bits ; null si invalide.
function expandV6(ip: string): number[] | null {
  let s = ip.split("%")[0];
  const dotted = /(\d+\.\d+\.\d+\.\d+)$/.exec(s);
  if (dotted) {
    const v4 = parseV4(dotted[1]);
    if (!v4) return null;
    s = s.slice(0, -dotted[1].length) + ((v4[0] << 8) | v4[1]).toString(16) + ":" + ((v4[2] << 8) | v4[3]).toString(16);
  }
  const halves = s.split("::");
  if (halves.length > 2) return null;
  const head = halves[0] ? halves[0].split(":") : [];
  const tail = halves.length === 2 && halves[1] ? halves[1].split(":") : [];
  const missing = 8 - head.length - tail.length;
  if (halves.length === 1 ? missing !== 0 : missing < 1) return null;
  const all = [...head, ...Array(halves.length === 2 ? missing : 0).fill("0"), ...tail];
  if (all.length !== 8) return null;
  const nums = all.map((g) => (/^[0-9a-f]{1,4}$/.test(g) ? parseInt(g, 16) : NaN));
  return nums.some(Number.isNaN) ? null : nums;
}

// ---------------------------------------------------------------- validation d'une URL

// Lève une Error si l'URL ne peut pas être relue en toute sécurité.
export async function assertSafeUrl(raw: string, deps: Deps = defaultDeps): Promise<URL> {
  const url = new URL(raw);
  if (url.protocol !== "http:" && url.protocol !== "https:") throw new Error("scheme");
  if (url.username || url.password) throw new Error("credentials");
  const port = url.port || (url.protocol === "https:" ? "443" : "80");
  if (port !== "80" && port !== "443") throw new Error("port");
  if (isBlockedHostname(url.hostname)) throw new Error("host");

  const [v4, v6] = await Promise.all([
    deps.resolve(url.hostname, "A").catch(() => [] as string[]),
    deps.resolve(url.hostname, "AAAA").catch(() => [] as string[]),
  ]);
  const addresses = [...v4, ...v6];
  if (addresses.length === 0) throw new Error("dns");
  if (addresses.some(isPrivateIp)) throw new Error("private-address");
  return url;
}

// ---------------------------------------------------------------- lecture bornée

export async function readLimited(res: Response, maxBytes: number): Promise<string | null> {
  const declared = Number(res.headers.get("content-length"));
  if (Number.isFinite(declared) && declared > maxBytes) {
    await res.body?.cancel();
    return null;
  }
  if (!res.body) return "";
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  while (total < maxBytes) {
    const { done, value } = await reader.read();
    if (done) break;
    chunks.push(value);
    total += value.byteLength;
  }
  await reader.cancel().catch(() => {});
  const bytes = new Uint8Array(Math.min(total, maxBytes));
  let offset = 0;
  for (const chunk of chunks) {
    const part = chunk.subarray(0, Math.max(0, Math.min(chunk.byteLength, bytes.length - offset)));
    bytes.set(part, offset);
    offset += part.byteLength;
  }
  return new TextDecoder().decode(bytes);
}

// ---------------------------------------------------------------- relecture

export type FetchOptions = { timeoutMs: number; maxBytes: number };

// Renvoie le HTML (tronqué à maxBytes) ou null en cas d'échec ou d'URL refusée.
export async function fetchPageSafely(url: string, options: FetchOptions, deps: Deps = defaultDeps): Promise<string | null> {
  try {
    const signal = AbortSignal.timeout(options.timeoutMs);
    let current = url;
    for (let hop = 0; hop <= MAX_REDIRECTS; hop++) {
      const safe = await assertSafeUrl(current, deps);
      const res = await deps.fetch(safe.href, {
        redirect: "manual",
        signal,
        headers: {
          "user-agent": "Mozilla/5.0 (compatible; GiftManagerBot/1.0)",
          accept: "text/html,application/xhtml+xml",
          "accept-language": "fr,en;q=0.8",
        },
      });
      if (res.status >= 300 && res.status < 400) {
        const location = res.headers.get("location");
        await res.body?.cancel();
        if (!location) return null;
        current = new URL(location, safe).href;
        continue;
      }
      if (!res.ok || !(res.headers.get("content-type") ?? "").includes("html")) {
        await res.body?.cancel();
        return null;
      }
      return await readLimited(res, options.maxBytes);
    }
    return null; // trop de redirections
  } catch {
    return null;
  }
}

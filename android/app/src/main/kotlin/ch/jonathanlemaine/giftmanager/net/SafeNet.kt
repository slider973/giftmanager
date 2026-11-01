package ch.jonathanlemaine.giftmanager.net

import java.util.Locale

/**
 * Garde SSRF pure, portée de `supabase/functions/track-prices/safe_fetch.ts` : les liens sont saisis par des
 * utilisateurs, l'app ne doit jamais atteindre un réseau privé en les relisant.
 */
object SafeNet {
    private val numericLabel = Regex("""^(0x[0-9a-f]*|\d+)$""", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val hexGroup = Regex("""^[0-9a-f]{1,4}$""")

    /** `true` pour un nom qui ne doit pas être joint : local, interne, une seule étiquette ou IPv4 littérale. */
    fun isBlockedHostname(hostname: String): Boolean {
        val h = hostname.lowercase(Locale.ROOT).removeSuffix(".")
        if (h.isEmpty() || ':' in h || h.startsWith("[")) return true // IPv6 littérale
        if (h == "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")) return true
        if ('.' !in h) return true // nom à une seule étiquette (intranet)
        // IPv4 littérale sous toutes ses formes (décimal, hexadécimal, octal, pointée) : dernière étiquette numérique.
        return numericLabel.matches(h.substringAfterLast('.'))
    }

    /** `true` pour une adresse privée, réservée ou illisible (par prudence). */
    fun isPrivateIp(ip: String): Boolean {
        val v = ip.trim().lowercase(Locale.ROOT)
        if (':' in v) return isPrivateV6(v)
        val parts = parseV4(v) ?: return true
        return isPrivateV4(parts)
    }

    private fun parseV4(s: String): List<Int>? {
        val m = ipv4.matchEntire(s) ?: return null
        val parts = m.groupValues.drop(1).map { it.toInt() }
        return parts.takeIf { list -> list.all { it <= 255 } }
    }

    private fun isPrivateV4(p: List<Int>): Boolean {
        val a = p[0]
        val b = p[1]
        return a == 0 || a == 10 || a == 127 ||
            (a == 100 && b in 64..127) || // CGNAT
            (a == 169 && b == 254) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 192 && b == 0) || // 192.0.0.0/24 et 192.0.2.0/24 (réservés)
            (a == 198 && (b == 18 || b == 19)) ||
            a >= 224 // multicast, réservé, broadcast
    }

    private fun isPrivateV6(ip: String): Boolean {
        val g = expandV6(ip) ?: return true
        if (g.all { it == 0 }) return true // ::
        if (g.take(7).all { it == 0 } && g[7] == 1) return true // ::1
        val embedded = listOf(g[6] shr 8, g[6] and 255, g[7] shr 8, g[7] and 255)
        // ::ffff:a.b.c.d (mappée) et ::a.b.c.d (compatible) : on juge l'IPv4 contenue.
        if (g.take(5).all { it == 0 } && (g[5] == 0xffff || g[5] == 0)) return isPrivateV4(embedded)
        if (g[0] == 0x64 && g[1] == 0xff9b && g.subList(2, 6).all { it == 0 }) return isPrivateV4(embedded) // NAT64
        if ((g[0] and 0xfe00) == 0xfc00) return true // fc00::/7 (ULA)
        if ((g[0] and 0xffc0) == 0xfe80) return true // fe80::/10 (lien local)
        if ((g[0] and 0xffc0) == 0xfec0) return true // fec0::/10 (site local, obsolète)
        if ((g[0] and 0xff00) == 0xff00) return true // multicast
        if (g[0] == 0x2001 && g[1] == 0x0db8) return true // documentation
        if (g[0] == 0x2002) return isPrivateV4(listOf(g[1] shr 8, g[1] and 255, 0, 0)) // 6to4
        return false
    }

    /** Développe une IPv6 textuelle en 8 groupes de 16 bits ; `null` si invalide. */
    private fun expandV6(ip: String): List<Int>? {
        var s = ip.substringBefore('%')
        Regex("""(\d+\.\d+\.\d+\.\d+)$""").find(s)?.let { dotted ->
            val v4 = parseV4(dotted.value) ?: return null
            s = s.dropLast(dotted.value.length) +
                ((v4[0] shl 8) or v4[1]).toString(16) + ":" + ((v4[2] shl 8) or v4[3]).toString(16)
        }
        val halves = s.split("::")
        if (halves.size > 2) return null
        val head = if (halves[0].isNotEmpty()) halves[0].split(":") else emptyList()
        val tail = if (halves.size == 2 && halves[1].isNotEmpty()) halves[1].split(":") else emptyList()
        val missing = 8 - head.size - tail.size
        if (if (halves.size == 1) missing != 0 else missing < 1) return null
        val all = head + List(if (halves.size == 2) missing else 0) { "0" } + tail
        if (all.size != 8) return null
        return all.map { group -> if (hexGroup.matches(group)) group.toInt(16) else return null }
    }
}

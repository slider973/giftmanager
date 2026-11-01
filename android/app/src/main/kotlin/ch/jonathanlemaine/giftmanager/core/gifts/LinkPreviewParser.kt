package ch.jonathanlemaine.giftmanager.core.gifts

import java.math.BigDecimal
import java.net.URI

/**
 * Lecture pure des liens et des prix saisis (iOS : `LinkPreviewService.normalizedURL` / `parsePrice`).
 * L'analyse des pages (titre, image, prix affiché) viendra compléter cet objet avec l'aperçu de lien.
 */
object LinkPreviewParser {
    private val linkToken = Regex(
        """^(?:https?://)?[\p{L}\p{N}_-]+(?:\.[\p{L}\p{N}_-]+)+(?::\d+)?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', '»', '"', '\'')
    private const val MAX_PRICE = 100_000_000L

    /**
     * Premier lien d'un texte (« Regarde ça 👉 https://… trop bien »), préfixé de `https://` s'il n'a pas de
     * schéma. `null` sans lien exploitable : l'hôte doit contenir un point.
     */
    fun normalizedUrl(raw: String): String? {
        val token = raw.trim().split(Regex("\\s+"))
            .map { it.trimEnd(*trailingPunctuation) }
            .firstOrNull { linkToken.matches(it) }
            ?: return null
        val text = if (token.startsWith("http://", ignoreCase = true) || token.startsWith("https://", ignoreCase = true)) {
            token
        } else {
            "https://$token"
        }
        val host = try {
            URI(text).host
        } catch (_: Exception) {
            null
        }
        return text.takeIf { host != null && '.' in host }
    }

    /**
     * Lit un prix saisi ou extrait d'une page : « 199,99 », « 1.299,00 », « 1'299.90 », « CHF 49.– ».
     * Le dernier séparateur (point ou virgule) suivi d'au plus 2 chiffres est le séparateur décimal.
     */
    fun parsePrice(text: String): BigDecimal? {
        var digits = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (digits.isEmpty()) return null
        val last = digits.indexOfLast { it == '.' || it == ',' }
        digits = if (last >= 0 && digits.length - last - 1 <= 2) {
            digits.substring(0, last).filter { it.isDigit() } + "." + digits.substring(last + 1)
        } else {
            digits.filter { it.isDigit() }
        }
        val value = digits.toBigDecimalOrNull() ?: return null
        return value.takeIf { it < BigDecimal(MAX_PRICE) }
    }
}

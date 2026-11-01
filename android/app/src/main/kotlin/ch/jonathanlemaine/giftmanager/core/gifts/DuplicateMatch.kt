package ch.jonathanlemaine.giftmanager.core.gifts

import java.net.URI
import java.text.Normalizer
import java.util.Locale

/**
 * Détection des doublons dans la liste d'un enfant (#61) — `DuplicateMatch.swift`.
 *
 * Un cadeau rangé dans un autre événement n'apparaît pas dans la vue courante : sans avertissement,
 * l'utilisateur le croit perdu et le recrée. La comparaison est tolérante (casse, accents, espaces,
 * ponctuation) et les URL sont réduites à leur partie significative pour ignorer les paramètres de suivi.
 */
object DuplicateMatch {
    private val diacritics = Regex("\\p{Mn}+")

    /** Titre réduit à ses caractères significatifs, en minuscules et sans accents. */
    fun normalize(title: String): String {
        val folded = diacritics.replace(Normalizer.normalize(title, Normalizer.Form.NFD), "").lowercase(Locale.FRENCH)
        val words = mutableListOf<String>()
        val current = StringBuilder()
        for (c in folded) {
            if (c.isLetterOrDigit()) {
                current.append(c)
            } else if (current.isNotEmpty()) {
                words += current.toString()
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words.joinToString(" ")
    }

    /**
     * Deux liens désignent le même article : hôte et chemin identiques, paramètres ignorés (`?tag=`, `utm_*`…
     * changent à chaque partage sans changer le produit).
     */
    fun sameUrl(lhs: String, rhs: String): Boolean {
        val a = canonical(lhs) ?: return false
        val b = canonical(rhs) ?: return false
        return a == b
    }

    private fun canonical(raw: String): String? {
        val uri = try {
            URI(raw.trim())
        } catch (_: Exception) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = (uri.path.orEmpty()).removeSuffix("/")
        return host.removePrefix("www.") + path.lowercase(Locale.ROOT)
    }
}

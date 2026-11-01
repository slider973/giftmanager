package ch.jonathanlemaine.giftmanager.ui.theme

/**
 * Pastels du design system, dans l'ordre stable d'iOS (`Color.Theme.pastels`) : l'ordre compte,
 * `forSeed` l'utilise comme index. Les valeurs ARGB sont dans [ThemeTokens].
 */
enum class Pastel {
    PINK,
    MINT,
    BLUE,
    PEACH,
    LAVENDER;

    /** Nom du token iOS (`pastelMint`), tel que stocké dans `children.color`. */
    val tokenName: String get() = "pastel" + name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** Résout `pastelMint` ou `mint` (casse et espaces ignorés) ; `null` si inconnu. */
        fun named(name: String?): Pastel? {
            val raw = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            val short = if (raw.startsWith("pastel")) raw.removePrefix("pastel") else raw
            return entries.firstOrNull { it.name.lowercase() == short }
        }

        /** Pastel déterministe dérivé d'une chaîne (même prénom, même couleur), comme sur iOS. */
        fun forSeed(seed: String): Pastel {
            var sum = 0L
            seed.codePoints().forEach { sum = (sum * 31 + it) and 0x7FFF_FFFF }
            return entries[(sum % entries.size).toInt()]
        }
    }
}

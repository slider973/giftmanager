package ch.jonathanlemaine.giftmanager.ui.components

import ch.jonathanlemaine.giftmanager.core.format.Countries

/** Logique du drapeau de pays (`CountryFlag.swift`), sans Compose. */
object CountryFlagLogic {
    private const val REGIONAL_INDICATOR_A = 0x1F1E6

    /** Code normalisé (majuscules, « UK » devient « GB »), ou `null` s'il n'est pas valide. */
    fun normalized(code: String?): String? {
        val upper = code?.trim()?.uppercase() ?: return null
        val mapped = if (upper == "UK") "GB" else upper
        return mapped.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
    }

    /** Émoji drapeau, ou `null` (l'écran affiche alors un globe). */
    fun emoji(code: String?): String? =
        normalized(code)?.map { String(Character.toChars(REGIONAL_INDICATOR_A + (it - 'A'))) }?.joinToString("")

    /** Nom du pays en français pour TalkBack, « Pays inconnu » si le code est invalide. */
    fun name(code: String?): String {
        val iso = normalized(code) ?: return "Pays inconnu"
        return Countries.all.firstOrNull { it.code == iso }?.name
            ?: java.util.Locale.Builder().setRegion(iso).build().getDisplayCountry(java.util.Locale.FRENCH)
                .takeIf { it.isNotBlank() && it != iso }
            ?: "Pays inconnu"
    }
}

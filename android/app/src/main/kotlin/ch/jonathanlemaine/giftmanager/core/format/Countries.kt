package ch.jonathanlemaine.giftmanager.core.format

/** Pays proposés dans les sélecteurs (Suisse et France en tête). */
object Countries {
    data class Country(val code: String, val name: String) {
        val id: String get() = code

        /** Drapeau en emoji : deux indicateurs régionaux. */
        val flag: String
            get() = code.map { String(Character.toChars(REGIONAL_INDICATOR_OFFSET + it.code)) }.joinToString("")
    }

    private const val REGIONAL_INDICATOR_OFFSET = 127_397

    val all: List<Country> = listOf(
        Country("CH", "Suisse"),
        Country("FR", "France"),
        Country("BE", "Belgique"),
        Country("LU", "Luxembourg"),
        Country("DE", "Allemagne"),
        Country("AT", "Autriche"),
        Country("IT", "Italie"),
        Country("ES", "Espagne"),
        Country("PT", "Portugal"),
        Country("NL", "Pays-Bas"),
        Country("GB", "Royaume-Uni"),
        Country("IE", "Irlande"),
        Country("US", "États-Unis"),
        Country("CA", "Canada"),
    )

    val currencies: List<String> = listOf("CHF", "EUR", "GBP", "USD", "CAD")

    /** Pays de repli quand la région de l'appareil n'est pas proposée. */
    const val DEFAULT_COUNTRY = "CH"

    /** Devise usuelle par pays (iOS : `StoreCatalog.currencyByCountry`). */
    private val currencyByCountry: Map<String, String> = mapOf(
        "CH" to "CHF", "LI" to "CHF", "FR" to "EUR", "DE" to "EUR", "IT" to "EUR", "ES" to "EUR", "BE" to "EUR",
        "LU" to "EUR", "AT" to "EUR", "NL" to "EUR", "PT" to "EUR", "IE" to "EUR", "GB" to "GBP", "US" to "USD",
        "CA" to "CAD",
    )

    /** Devise suggérée pour [country], ou `null` si inconnue. */
    fun currencyFor(country: String?): String? = country?.let { currencyByCountry[it] }

    /** Région de [locale] si elle fait partie des pays proposés, sinon la Suisse. */
    fun defaultCountry(locale: java.util.Locale): String =
        locale.country.takeIf { code -> all.any { it.code == code } } ?: DEFAULT_COUNTRY

    fun name(code: String?): String = all.firstOrNull { it.code == code }?.name ?: (code ?: "—")
}

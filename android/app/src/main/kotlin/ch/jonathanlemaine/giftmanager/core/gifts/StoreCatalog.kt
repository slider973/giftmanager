package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import java.net.URI
import java.util.Locale

/** Déduit la boutique, le pays et la devise à partir du domaine d'un lien — `StoreCatalog.swift`. */
object StoreCatalog {
    data class Store(val name: String, val country: String?, val currency: String?)

    /** Domaine (sans « www. ») → boutique. Les domaines génériques (.com) n'ont pas de pays. */
    private val known: Map<String, Store> = mapOf(
        "amazon.fr" to Store("Amazon", "FR", "EUR"),
        "amazon.de" to Store("Amazon", "DE", "EUR"),
        "amazon.it" to Store("Amazon", "IT", "EUR"),
        "amazon.es" to Store("Amazon", "ES", "EUR"),
        "amazon.co.uk" to Store("Amazon", "GB", "GBP"),
        "amazon.com" to Store("Amazon", "US", "USD"),
        "amazon.com.be" to Store("Amazon", "BE", "EUR"),
        "galaxus.ch" to Store("Galaxus", "CH", "CHF"),
        "galaxus.de" to Store("Galaxus", "DE", "EUR"),
        "galaxus.fr" to Store("Galaxus", "FR", "EUR"),
        "digitec.ch" to Store("Digitec", "CH", "CHF"),
        "manor.ch" to Store("Manor", "CH", "CHF"),
        "brack.ch" to Store("Brack", "CH", "CHF"),
        "interdiscount.ch" to Store("Interdiscount", "CH", "CHF"),
        "fust.ch" to Store("Fust", "CH", "CHF"),
        "ricardo.ch" to Store("Ricardo", "CH", "CHF"),
        "franz-carl-weber.ch" to Store("Franz Carl Weber", "CH", "CHF"),
        "fcw.ch" to Store("Franz Carl Weber", "CH", "CHF"),
        "smythstoys.com" to Store("Smyths Toys", null, null),
        "mediamarkt.ch" to Store("MediaMarkt", "CH", "CHF"),
        "mediamarkt.de" to Store("MediaMarkt", "DE", "EUR"),
        "fnac.com" to Store("Fnac", "FR", "EUR"),
        "fnac.ch" to Store("Fnac", "CH", "CHF"),
        "darty.com" to Store("Darty", "FR", "EUR"),
        "boulanger.com" to Store("Boulanger", "FR", "EUR"),
        "cdiscount.com" to Store("Cdiscount", "FR", "EUR"),
        "joueclub.fr" to Store("JouéClub", "FR", "EUR"),
        "king-jouet.com" to Store("King Jouet", "FR", "EUR"),
        "lagranderecre.fr" to Store("La Grande Récré", "FR", "EUR"),
        "oxybul.com" to Store("Oxybul", "FR", "EUR"),
        "auchan.fr" to Store("Auchan", "FR", "EUR"),
        "carrefour.fr" to Store("Carrefour", "FR", "EUR"),
        "leclerc.com" to Store("E.Leclerc", "FR", "EUR"),
        "decathlon.fr" to Store("Decathlon", "FR", "EUR"),
        "decathlon.ch" to Store("Decathlon", "CH", "CHF"),
        "zalando.fr" to Store("Zalando", "FR", "EUR"),
        "zalando.ch" to Store("Zalando", "CH", "CHF"),
        "ikea.com" to Store("IKEA", null, null),
        "lego.com" to Store("LEGO", null, null),
        "nike.com" to Store("Nike", null, null),
        "apple.com" to Store("Apple", null, null),
        "etsy.com" to Store("Etsy", null, null),
        "vinted.fr" to Store("Vinted", "FR", "EUR"),
        "leboncoin.fr" to Store("leboncoin", "FR", "EUR"),
    )

    fun currency(country: String?): String? = Countries.currencyFor(country)

    fun store(url: String): Store? {
        val host = try {
            URI(url.trim()).host?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        } ?: return null
        val domain = host.removePrefix("www.")
        known[domain]?.let { return it }
        // Sous-domaines (ex. fr.shop.example.ch) : on retente avec les deux derniers niveaux.
        val parts = domain.split(".").filter { it.isNotEmpty() }
        if (parts.size > 2) known[parts.takeLast(2).joinToString(".")]?.let { return it }

        // Inconnu : nom tiré du domaine, pays tiré de l'extension.
        val name = parts.dropLast(1).lastOrNull()?.replaceFirstChar { it.uppercase() } ?: domain
        val tld = parts.lastOrNull()?.uppercase(Locale.ROOT)
        val country = tld?.takeIf { Countries.currencyFor(it) != null }
        return Store(name, country, currency(country))
    }

    /** Ordonne les liens : ceux du pays du lecteur d'abord, puis par boutique. */
    fun sorted(links: List<ItemLink>, preferredCountry: String?): List<ItemLink> =
        links.sortedWith(
            compareByDescending<ItemLink> { it.country == preferredCountry }.thenBy { it.store.orEmpty() },
        )
}

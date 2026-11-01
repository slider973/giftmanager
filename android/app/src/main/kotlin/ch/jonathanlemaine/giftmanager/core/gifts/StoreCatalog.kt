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

    // region Recherche par pays (#78)

    /** Boutique où lancer une recherche de jouet, avec le gabarit d'URL correspondant. */
    data class SearchableStore(
        val domain: String,
        val name: String,
        val country: String,
        /** Gabarit où `{q}` est remplacé par la requête, déjà échappée. */
        val searchTemplate: String,
    )

    /**
     * Boutiques interrogeables, par pays du foyer. Le pays vient de `households.country`, jamais de la locale de
     * l'appareil : un parent suisse en voyage continue à voir des boutiques suisses. Les enseignes de jouets sont
     * en tête — ce sont elles qui donnent les résultats les plus pertinents pour une envie d'enfant.
     */
    private val searchable: Map<String, List<SearchableStore>> = mapOf(
        "CH" to listOf(
            SearchableStore("franz-carl-weber.ch", "Franz Carl Weber", "CH", "https://www.franz-carl-weber.ch/fr/search?q={q}"),
            SearchableStore("galaxus.ch", "Galaxus", "CH", "https://www.galaxus.ch/fr/search?q={q}"),
            SearchableStore("fnac.ch", "Fnac", "CH", "https://www.fnac.ch/SearchResult/ResultList.aspx?Search={q}"),
            SearchableStore("manor.ch", "Manor", "CH", "https://www.manor.ch/fr/search?q={q}"),
            SearchableStore("digitec.ch", "Digitec", "CH", "https://www.digitec.ch/fr/search?q={q}"),
        ),
        "FR" to listOf(
            SearchableStore("king-jouet.com", "King Jouet", "FR", "https://www.king-jouet.com/recherche.htm?mot={q}"),
            SearchableStore("joueclub.fr", "JouéClub", "FR", "https://www.joueclub.fr/catalogsearch/result/?q={q}"),
            SearchableStore("lagranderecre.fr", "La Grande Récré", "FR", "https://www.lagranderecre.fr/catalogsearch/result/?q={q}"),
            SearchableStore("fnac.com", "Fnac", "FR", "https://www.fnac.com/SearchResult/ResultList.aspx?Search={q}"),
            SearchableStore("amazon.fr", "Amazon", "FR", "https://www.amazon.fr/s?k={q}"),
        ),
        "DE" to listOf(
            SearchableStore("galaxus.de", "Galaxus", "DE", "https://www.galaxus.de/search?q={q}"),
            SearchableStore("amazon.de", "Amazon", "DE", "https://www.amazon.de/s?k={q}"),
            SearchableStore("mediamarkt.de", "MediaMarkt", "DE", "https://www.mediamarkt.de/de/search.html?query={q}"),
        ),
        "BE" to listOf(
            SearchableStore("amazon.com.be", "Amazon", "BE", "https://www.amazon.com.be/s?k={q}"),
            SearchableStore("fnac.com", "Fnac", "BE", "https://www.fnac.com/SearchResult/ResultList.aspx?Search={q}"),
        ),
    )

    /**
     * Boutiques à interroger pour un foyer donné. Un pays sans liste dédiée retombe sur Amazon de ce pays s'il
     * existe dans le catalogue, plutôt que de ne rien proposer.
     */
    fun searchableStores(country: String?): List<SearchableStore> {
        if (country.isNullOrEmpty()) return emptyList()
        searchable[country]?.let { return it }

        val amazonDomain = "amazon." + country.lowercase(Locale.ROOT)
        val store = known[amazonDomain]
        val storeCountry = store?.country
        if (store != null && storeCountry != null) {
            return listOf(SearchableStore(amazonDomain, store.name, storeCountry, "https://www.$amazonDomain/s?k={q}"))
        }
        return emptyList()
    }

    /**
     * URL de recherche pour une envie exprimée par l'enfant. `null` si la requête est vide, pour ne jamais ouvrir une
     * page de recherche vide.
     */
    fun searchUrl(query: String, store: SearchableStore): String? {
        val clean = query.trim()
        if (clean.isEmpty()) return null
        return store.searchTemplate.replace("{q}", percentEncode(clean))
    }

    /**
     * Même échappement qu'iOS (`alphanumerics` + « -_. », UTF-8 en %XX, espace = %20) : `URLEncoder` produirait « + »
     * et laisserait « * » passer. Les lettres accentuées sont échappées (iOS : par `URL(string:)`).
     */
    private const val HEX = "0123456789ABCDEF"

    private fun percentEncode(text: String): String = buildString {
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val plain = c < 0x80 && (c.toChar() in 'a'..'z' || c.toChar() in 'A'..'Z' || c.toChar() in '0'..'9' || c.toChar() in "-_.")
            if (plain) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
        }
    }

    // endregion
}

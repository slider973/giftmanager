package ch.jonathanlemaine.giftmanager.core.gifts

import java.math.BigDecimal
import java.net.URI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Lecture pure des liens et des prix saisis (iOS : `LinkPreviewService.normalizedURL` / `parsePrice`).
 * Lit aussi une page produit (`parse`), dans l'ordre de fiabilité du script d'extraction iOS : JSON-LD Product,
 * meta produit, `itemprop`, prix affiché (Amazon).
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

    // MARK: - Prix et devise affichés

    private val displayedAmount = Regex(
        """\d{1,3}(?:[ '\u2019.,\u00A0\u202F]\d{3})*(?:[.,]\d{1,2})?(?!\d)|\d+(?:[.,]\d{1,2})?""",
    )

    /** Premier montant d'un texte affiché, ex. « 157,09 € avec 13 % d'économies » -> 157.09. */
    fun priceFromDisplayedText(text: String): BigDecimal? =
        displayedAmount.find(text)?.let { parsePrice(it.value) }

    /**
     * Devise d'un prix affiché (« 149,31 CHF », « 159,99 € », « £20 », « CA$ 30 »).
     * Le symbole `$` seul est résolu avec le pays de la boutique (USD par défaut).
     */
    fun currency(inDisplayedText: String, storeCountry: String?): String? {
        val text = inDisplayedText
        val upper = text.uppercase()
        for (code in listOf("CHF", "EUR", "GBP", "USD", "CAD")) if (code in upper) return code
        return when {
            "FR." in upper || "SFR" in upper -> "CHF"
            '\u20AC' in text -> "EUR"
            '\u00A3' in text -> "GBP"
            "CA$" in upper || "C$" in upper -> "CAD"
            '$' in text -> if (storeCountry == "CA") "CAD" else "USD"
            else -> null
        }
    }

    private val titleSeparators = listOf(
        " : Amazon", ": Amazon.", " | Galaxus", " - acheter sur Galaxus", " | Digitec", " - acheter sur Digitec",
        " - Fnac", " | Fnac", " | Manor", " | Smyths Toys", " \u2013 ",
    )

    /** « Produit : Amazon.fr: Jeux et Jouets » -> « Produit ». */
    fun cleanTitle(title: String): String {
        var result = title.trim()
        for (separator in titleSeparators) {
            val index = result.indexOf(separator)
            if (index >= 0) result = result.substring(0, index)
        }
        if (result.startsWith("Amazon.fr") || result.startsWith("Amazon.com")) {
            result = result.split(": ").drop(1).joinToString(": ")
        }
        return result.trim().take(MAX_TITLE)
    }

    // MARK: - Page produit

    private const val MAX_TITLE = 200
    private val json = Json

    private val amazonPriceSelectors = listOf(
        "#corePrice_mobile_feature_div .a-offscreen", "#corePrice_feature_div .a-offscreen",
        "#corePriceDisplay_desktop_feature_div .a-offscreen", ".priceToPay .a-offscreen",
        ".apex-pricetopay-value .a-offscreen", "#apex-pricetopay-accessibility-label",
        ".reinventPricePriceToPayMargin", "#apex_desktop .a-offscreen", "#price_inside_buybox",
        "#sns-base-price", ".a-price .a-offscreen",
    )

    private data class StructuredProduct(val name: String?, val price: String?, val currency: String?, val image: String?)

    /**
     * Aperçu d'une page HTML. `baseUrl` résout les URL relatives ; `store` donne la devise de repli.
     * `null` si la page n'expose ni titre, ni image, ni prix.
     */
    fun parse(html: String, baseUrl: String, store: StoreCatalog.Store?): LinkPreview? {
        val doc = try {
            Jsoup.parse(html, baseUrl)
        } catch (_: Exception) {
            return null
        }
        val metas = metaMap(doc)
        val product = jsonLdProduct(doc)

        var price: String? = null
        var currency: String? = null
        if (product?.price != null) {
            price = product.price
            currency = product.currency
        } else if (!metas["product:price:amount"].isNullOrEmpty() || !metas["og:price:amount"].isNullOrEmpty()) {
            price = metas["product:price:amount"].takeUnless { it.isNullOrEmpty() } ?: metas["og:price:amount"]
            currency = (metas["product:price:currency"].takeUnless { it.isNullOrEmpty() } ?: metas["og:price:currency"])
                ?.takeUnless { it.isEmpty() }
        } else {
            doc.selectFirst("[itemprop=price]")?.let { element ->
                price = element.attr("content").ifEmpty { element.text().trim() }
                currency = doc.selectFirst("[itemprop=priceCurrency]")
                    ?.let { it.attr("content").ifEmpty { it.text().trim() } }
            }
        }
        var priceText: String? = null
        if (price.isNullOrEmpty()) {
            for (selector in amazonPriceSelectors) {
                val text = doc.selectFirst(selector)?.text()?.trim()
                if (text != null && text.any { it.isDigit() }) {
                    priceText = text.take(80)
                    break
                }
            }
        }

        var image = metas["og:image"].takeUnless { it.isNullOrEmpty() }
            ?: metas["og:image:secure_url"].takeUnless { it.isNullOrEmpty() }
            ?: metas["twitter:image"].takeUnless { it.isNullOrEmpty() }
        if (image == null) {
            doc.selectFirst("#landingImage, #imgBlkFront, #main-image")?.let { img ->
                image = img.attr("data-old-hires").ifEmpty { img.attr("src") }.takeUnless { it.isEmpty() }
            }
        }
        if (image == null) image = product?.image

        val rawTitle = metas["og:title"].takeUnless { it.isNullOrEmpty() }
            ?: product?.name
            ?: doc.title().takeUnless { it.isEmpty() }
        val title = rawTitle?.let(::cleanTitle)?.takeUnless { it.isEmpty() }
        val imageUrl = image?.let { resolve(it, baseUrl) }

        var value: BigDecimal? = null
        var priceCurrency: String? = null
        val structured = price?.let { parsePrice(it) }
        if (structured != null) {
            // Donnée structurée : devise déclarée par la page, sinon celle de la boutique.
            value = structured
            priceCurrency = currency?.uppercase() ?: store?.currency
        } else if (priceText != null) {
            val displayed = priceFromDisplayedText(priceText)
            if (displayed != null) {
                // Prix affiché : devise du texte (Amazon peut convertir), sinon celle de la boutique.
                value = displayed
                priceCurrency = currency(priceText, store?.country) ?: store?.currency
            }
        }

        if (title == null && imageUrl == null && value == null) return null
        return LinkPreview(title = title, imageUrl = imageUrl, price = value, currency = priceCurrency)
    }

    private fun resolve(reference: String, baseUrl: String): String? = try {
        URI(baseUrl).resolve(URI(reference.trim())).toString()
    } catch (_: Exception) {
        null
    }

    /** Première valeur de chaque meta (`property`, `name` ou `itemprop`, en minuscules). */
    private fun metaMap(doc: Document): Map<String, String> {
        val metas = LinkedHashMap<String, String>()
        for (meta in doc.select("meta")) {
            val key = listOf("property", "name", "itemprop").map { meta.attr(it) }.firstOrNull { it.isNotEmpty() }
                ?.lowercase().orEmpty()
            val content = meta.attr("content")
            if (key.isNotEmpty() && content.isNotEmpty() && key !in metas) metas[key] = content
        }
        return metas
    }

    private fun jsonLdProduct(doc: Document): StructuredProduct? {
        for (script in doc.select("script[type=application/ld+json]")) {
            val root = try {
                json.parseToJsonElement(script.data())
            } catch (_: Exception) {
                continue
            }
            findProduct(root)?.let { return it }
        }
        return null
    }

    private fun findProduct(node: JsonElement): StructuredProduct? = when (node) {
        is JsonArray -> node.firstNotNullOfOrNull { findProduct(it) }
        is JsonObject -> if (isProduct(node["@type"])) toProduct(node) else node.values.firstNotNullOfOrNull { findProduct(it) }
        else -> null
    }

    private fun isProduct(type: JsonElement?): Boolean = when (type) {
        is JsonPrimitive -> type.isString && type.content == "Product"
        is JsonArray -> type.any { it is JsonPrimitive && it.isString && it.content == "Product" }
        else -> false
    }

    private fun toProduct(product: JsonObject): StructuredProduct {
        val offers = product["offers"]
        val offer = (if (offers is JsonArray) offers.firstOrNull() else offers) as? JsonObject
        val specElement = offer?.get("priceSpecification")
        val spec = (if (specElement is JsonArray) specElement.firstOrNull() else specElement) as? JsonObject
        val price = offer?.let { text(it["price"]) ?: text(it["lowPrice"]) ?: spec?.let { s -> text(s["price"]) } }
        val currency = offer?.let { text(it["priceCurrency"]) ?: spec?.let { s -> text(s["priceCurrency"]) } }
        return StructuredProduct(
            name = text(product["name"])?.takeUnless { it.isEmpty() },
            price = price,
            currency = currency?.takeUnless { it.isEmpty() },
            image = imageOf(product["image"]),
        )
    }

    private fun imageOf(image: JsonElement?): String? {
        val first = if (image is JsonArray) image.firstOrNull() else image
        val value = if (first is JsonObject) text(first["url"]) else text(first)
        return value?.takeUnless { it.isEmpty() }
    }

    /** Valeur textuelle d'un primitif JSON (nombre ou chaîne) ; `null` pour l'absent, `null` JSON ou objet. */
    private fun text(element: JsonElement?): String? =
        (element as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
}

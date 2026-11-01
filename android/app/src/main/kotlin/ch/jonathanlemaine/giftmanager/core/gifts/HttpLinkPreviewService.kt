package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.net.LinkFetcher
import kotlin.coroutines.cancellation.CancellationException

/** Autre moyen de lire une page (ex. WebView pour les boutiques qui exigent du JavaScript). */
fun interface PreviewSource {
    suspend fun preview(url: String, store: StoreCatalog.Store?): LinkPreview?
}

/**
 * Aperçu de lien par relecture HTTP : lien normalisé, page récupérée en sécurité, puis analysée.
 * Une `PreviewSource` de repli complète ce qui manque (prix surtout). Jamais d'exception, jamais d'URL journalisée.
 */
class HttpLinkPreviewService(
    private val fetcher: LinkFetcher,
    private val fallback: PreviewSource? = null,
) : LinkPreviewService {
    override suspend fun preview(raw: String): LinkPreview? {
        val url = LinkPreviewParser.normalizedUrl(raw) ?: return null
        val store = StoreCatalog.store(url)
        val primary = attempt { fetcher.fetch(url)?.let { LinkPreviewParser.parse(it.html, it.finalUrl, store) } }
        if (fallback == null || primary?.price != null) return primary
        val secondary = attempt { fallback.preview(url, store) } ?: return primary
        return LinkPreview(
            title = primary?.title ?: secondary.title,
            imageUrl = primary?.imageUrl ?: secondary.imageUrl,
            price = secondary.price,
            currency = secondary.currency ?: primary?.currency,
        )
    }

    private suspend fun attempt(block: suspend () -> LinkPreview?): LinkPreview? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}

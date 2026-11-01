package ch.jonathanlemaine.giftmanager.core.gifts

import java.math.BigDecimal

/** Aperçu d'un lien produit : titre, image, prix et devise si la page les expose. */
data class LinkPreview(
    val title: String? = null,
    val imageUrl: String? = null,
    val price: BigDecimal? = null,
    val currency: String? = null,
)

/** Lit une page produit. `null` : aucun aperçu (le formulaire se remplit alors à la main). */
fun interface LinkPreviewService {
    suspend fun preview(raw: String): LinkPreview?
}

/** Implémentation d'attente : aucun aperçu. L'analyse des pages la remplace. */
object NoLinkPreview : LinkPreviewService {
    override suspend fun preview(raw: String): LinkPreview? = null
}

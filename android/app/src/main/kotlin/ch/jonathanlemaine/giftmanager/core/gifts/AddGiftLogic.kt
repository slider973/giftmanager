package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.DraftLink
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** Cadeau à créer, tel qu'envoyé à `wish_items` (iOS : `GiftRepository.NewItem`). */
data class NewItem(
    val childId: UUID,
    val eventId: UUID?,
    val kind: WishKind,
    val title: String,
    val notes: String?,
    val imageUrl: String?,
    val priority: Int,
    val owned: Boolean,
    val createdBy: UUID,
    /** `pending` soumet l'idée aux parents, `none` garde la surprise totale (#57). */
    val reviewStatus: IdeaReview = IdeaReview.NONE,
)

/** Champs du formulaire au moment de l'enregistrement (iOS : `AddGiftModel.Draft`). */
data class GiftDraft(
    val title: String,
    val notes: String = "",
    val imageUrl: String? = null,
    /** Photo choisie par l'utilisateur, déjà redimensionnée en JPEG ; envoyée au stockage à l'enregistrement. */
    val imageBytes: ByteArray? = null,
    val isFavorite: Boolean = false,
    val eventId: UUID? = null,
    val links: List<DraftLink> = emptyList(),
    /** #57 : soumettre l'idée aux parents plutôt que la garder invisible. */
    val submitToParents: Boolean = true,
)

/** Règles d'enregistrement d'un cadeau, séparées du formulaire (iOS : `AddGiftModel`). */
object AddGiftLogic {
    /** #57 : seule une idée peut être soumise aux parents ; un souhait est déjà visible d'eux. */
    fun reviewStatus(kind: WishKind, submitToParents: Boolean): IdeaReview =
        if (kind == WishKind.IDEA && submitToParents) IdeaReview.PENDING else IdeaReview.NONE

    fun trimmedNotes(notes: String): String? = notes.trim().ifEmpty { null }

    /**
     * Cadeau à créer. Un cadeau déjà possédé n'appartient à aucun événement : il ne doit pas réapparaître dans
     * la liste de Noël ou d'un anniversaire.
     */
    fun newItem(childId: UUID, kind: WishKind, owned: Boolean, draft: GiftDraft, imageUrl: String?, userId: UUID) = NewItem(
        childId = childId,
        eventId = if (owned) null else draft.eventId,
        kind = kind,
        title = draft.title.trim(),
        notes = trimmedNotes(draft.notes),
        imageUrl = imageUrl,
        priority = if (draft.isFavorite) 1 else 0,
        owned = owned,
        createdBy = userId,
        reviewStatus = reviewStatus(kind, draft.submitToParents),
    )

    /** Lien principal du formulaire : URL normalisée, boutique (détectée si absente), pays, prix et devise. */
    fun mainLink(urlText: String, store: StoreCatalog.Store?, priceText: String, currency: String): DraftLink? {
        val url = LinkPreviewParser.normalizedUrl(urlText) ?: return null
        val detected = store ?: StoreCatalog.store(url)
        return DraftLink(
            url = url,
            store = detected?.name,
            country = detected?.country,
            price = LinkPreviewParser.parsePrice(priceText),
            currency = currency,
        )
    }

    /** Prix pré-rempli avec deux décimales : « 108.00 ». */
    fun priceInputText(price: BigDecimal): String = price.setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** Un cadeau déjà présent pour cet enfant : même titre (casse et accents ignorés) ou même lien d'achat. */
    suspend fun findDuplicate(items: ItemRepository, childId: UUID, title: String, links: List<DraftLink>): WishItem? {
        val needle = DuplicateMatch.normalize(title)
        if (needle.isEmpty()) return null
        // La détection est un confort : en cas d'échec réseau, on laisse enregistrer.
        return runCatchingCancellable {
            // Aucun filtre d'événement : on cherche dans toute la liste de l'enfant, pas seulement l'événement
            // affiché — c'est l'origine du doublon signalé (#61).
            val all = items.childItems(childId, eventId = null, kind = null)
            all.firstOrNull { DuplicateMatch.normalize(it.title) == needle }
                ?: links.firstOrNull()?.url?.let { url ->
                    val match = items.links(all.map { it.id }).firstOrNull { DuplicateMatch.sameUrl(it.url, url) }
                    match?.let { m -> all.firstOrNull { it.id == m.itemId } }
                }
        }.getOrNull()
    }
}

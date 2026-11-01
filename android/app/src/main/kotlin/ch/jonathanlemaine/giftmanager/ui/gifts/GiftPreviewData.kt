package ch.jonathanlemaine.giftmanager.ui.gifts

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Données d'aperçu des écrans de cadeaux (#73) : jamais utilisées hors des `@Preview`. */
internal object GiftPreviewData {
    fun id(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

    val leo = Child(id("leo"), id("h1"), "Léo", DayDate(LocalDate.of(2018, 3, 12)), "🦊", "pastelPeach")
    val zoe = Child(id("zoe"), id("h2"), "Zoé", DayDate(LocalDate.of(2019, 4, 2)), "🦄", "pastelPink")
    val mamie = Child(id("mamie"), id("h3"), "Mamie Jo", avatarEmoji = "🌸", avatarColor = "pastelLavender", isAdult = true)

    val christmas = GiftEvent(id("noel"), id("g"), GiftEventKind.CHRISTMAS, "Noël 2026", DayDate(LocalDate.of(2026, 12, 25)))
    val birthday = GiftEvent(id("anniv"), null, GiftEventKind.BIRTHDAY, "Anniversaire de Léo", DayDate(LocalDate.of(2027, 3, 12)), id("leo"))

    private fun item(
        seed: String,
        title: String,
        status: ItemStatus? = null,
        priority: Int = 0,
        owned: Boolean = false,
        kind: WishKind = WishKind.WISH,
        mine: ReservationState? = null,
        notes: String? = null,
    ) = WishItem(
        id = id(seed), childId = leo.id, eventId = christmas.id, kind = kind, title = title, notes = notes,
        priority = priority, owned = owned, status = status, myReservation = mine,
    )

    /** Liste vue par un parent : le serveur ne renvoie aucun statut. */
    val parentItems = listOf(
        item("lego", "LEGO Technic McLaren F1", priority = 1),
        item("velo", "Vélo 20 pouces Woom 4"),
        item("casque", "Casque Sony WH-CH520 pour enfants, bleu"),
        item("livre", "Le Petit Prince, édition illustrée", owned = true),
    )

    /** La même liste vue par Mamie : un statut par cadeau, et des idées. */
    val familyItems = listOf(
        item("lego", "LEGO Technic McLaren F1", ItemStatus.AVAILABLE, priority = 1),
        item("velo", "Vélo 20 pouces Woom 4", ItemStatus.POT),
        item("casque", "Casque Sony WH-CH520 pour enfants, bleu", ItemStatus.MINE, mine = ReservationState.RESERVED),
        item("switch", "Nintendo Switch 2", ItemStatus.TAKEN),
        item("livre", "Le Petit Prince, édition illustrée", ItemStatus.OWNED, owned = true),
        item("idee", "Atelier poterie pour enfants", ItemStatus.AVAILABLE, kind = WishKind.IDEA),
    )

    fun links(items: List<WishItem>): Map<UUID, List<ItemLink>> = items.associate { wish ->
        wish.id to listOf(
            ItemLink(id(wish.title + "ch"), wish.id, "https://www.galaxus.ch/fr/s1/product/1", "Galaxus", "CH", BigDecimal("199.00"), "CHF"),
            ItemLink(id(wish.title + "fr"), wish.id, "https://www.amazon.fr/dp/1", "Amazon", "FR", BigDecimal("189.99"), "EUR"),
        )
    }

    fun detailItem(status: ItemStatus?, mine: ReservationState? = null, contribution: BigDecimal? = null) = WishItem(
        id = id("lego"), childId = leo.id, eventId = christmas.id, kind = WishKind.WISH, title = "LEGO Technic McLaren F1",
        notes = "La version 42141, pas la petite. Il a déjà la Ferrari.", priority = 1, status = status, myReservation = mine,
        potTotal = if (status == ItemStatus.POT) BigDecimal("120") else null,
        potCurrency = if (status == ItemStatus.POT) "CHF" else null,
        potCount = if (status == ItemStatus.POT) 3 else null,
        myContribution = contribution,
    )

    fun idea(review: IdeaReview, note: String? = null) = WishItem(
        id = id("idee"), childId = leo.id, kind = WishKind.IDEA, title = "Atelier poterie pour enfants",
        status = ItemStatus.AVAILABLE, reviewStatus = review, reviewNote = note, createdBy = id("me"),
    )
}

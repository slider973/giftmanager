package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.currency.RatesApproximator
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.gifts.GiftActionReducer
import ch.jonathanlemaine.giftmanager.core.gifts.GiftListLogic
import ch.jonathanlemaine.giftmanager.core.gifts.ReservationActions
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.gifts.ThanksLogic
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.ItemDonor
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.PotParticipant
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.core.model.ReceiptRules
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.prices.PriceTrend
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.isParent
import ch.jonathanlemaine.giftmanager.data.ItemNotifier
import ch.jonathanlemaine.giftmanager.data.PotRepository
import ch.jonathanlemaine.giftmanager.data.PriceHistoryRepository
import ch.jonathanlemaine.giftmanager.data.ReservationRepository
import ch.jonathanlemaine.giftmanager.data.ThanksRepository
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Petite célébration après une réservation ou une participation (consommée par l'écran, puis remise à `null`). */
enum class Celebration { RESERVED, POT }

/**
 * Fiche d'un cadeau. Tout ce qui touche au secret dérive de [isParentView] et du `status` renvoyé par le serveur :
 * en vue parent, aucun badge, aucune réservation, aucune cagnotte ni participant.
 */
data class GiftDetailUiState(
    val isLoading: Boolean = true,
    val item: WishItem? = null,
    /** Liens triés : ceux du pays du lecteur d'abord. */
    val links: List<ItemLink> = emptyList(),
    /** Enfant connu de la famille courante ; `null` pour une fiche ouverte par son seul identifiant. */
    val child: Child? = null,
    /**
     * Vrai pour les parents de l'enfant. Par défaut vrai tant que rien n'est chargé : dans le doute on ne montre
     * rien de plus.
     */
    val isParentView: Boolean = true,
    val isMyUserId: UUID? = null,
    /** Devise du profil : les prix d'une autre devise y sont convertis à titre indicatif (#74). */
    val profileCurrency: String? = null,
    /** Taux courants ; `null` tant qu'aucun n'est connu (aucune conversion affichée). */
    val rates: ExchangeRates? = null,
    val readOnly: Boolean = false,
    val eventDate: DayDate? = null,
    val participants: List<PotParticipant> = emptyList(),
    /** « Reçu ! » (#42) : un parent, après la fête (calculé avec l'horloge du ViewModel). */
    val canMarkReceived: Boolean = false,
    /** « Dire merci » (#42) : un parent, sur un souhait reçu. */
    val canSendThanks: Boolean = false,
    /** Donateurs qui se sont fait connaître (« Offert par Mamie »), chargés pour un parent sur un cadeau reçu. */
    val donors: List<ItemDonor> = emptyList(),
    /** Évolution du prix de chaque lien, pour celui qui a réservé le cadeau (#77) ; vide sinon. */
    val trends: List<PriceTrend> = emptyList(),
    val isWorking: Boolean = false,
    val celebration: Celebration? = null,
    /** « Reçu ! » vient de réussir : l'écran de remerciement peut s'ouvrir. */
    val thanksRequested: Boolean = false,
    val error: GiftError? = null,
    /** Le cadeau n'existe plus, ou vient d'être supprimé : l'écran se ferme. */
    val isClosed: Boolean = false,
) {
    /** Actions de réservation autorisées (jamais de bouton Réserver ni de cagnotte en vue parent). */
    val reservation: ReservationActions? get() = item?.let { GiftActionReducer.availability(it, isParentView) }

    val canEdit: Boolean get() = item?.let { GiftListLogic.canEdit(it, isParentView, isMyUserId, readOnly) } ?: false

    val canParentReserve: Boolean get() = item?.let { GiftListLogic.canParentReserve(it, isParentView, readOnly) } ?: false

    val canToggleOwned: Boolean get() = canEdit && item?.let { GiftListLogic.canToggleOwned(it, isParentView) } == true

    val canToggleFavorite: Boolean get() = item?.let { GiftListLogic.canToggleFavorite(it, isParentView, readOnly) } ?: false

    /** « ≈ 214 € » à côté du prix d'un lien dans une autre devise que celle du profil, sinon `null`. */
    fun approxText(link: ItemLink): String? = RatesApproximator(rates).approxText(link.price, link.currency, profileCurrency)

    /** Date des taux (« 2 octobre »), pour la mention « conversion indicative » ; `null` si aucun taux. */
    val rateDateText: String? get() = rates?.publicationText

    /** Au moins un prix de la fiche est converti : la mention « conversion indicative » est due. */
    val hasConversion: Boolean
        get() = RatesApproximator(rates).hasConversion(links.map { it.price to it.currency }, profileCurrency)

    /** Cagnotte à afficher ; jamais pour un parent (le serveur ne renseigne rien non plus). */
    val potProgress: PotProgress? get() = if (isParentView) null else item?.let { PotProgress.make(it, links) }

    /** « Mamie », « Mamie et Paul »… ; vide si aucun donateur ne s'est fait connaître. */
    val donorNames: String get() = ThanksLogic.donorNames(donors.map { it.displayName })

    /** Verdict d'une idée, visible de son auteur et des parents (#57). */
    val ideaReview: IdeaReview? get() = item?.takeIf { it.kind == WishKind.IDEA }?.reviewStatus
}

/** Logique de la fiche cadeau, séparée de son affichage (iOS : `GiftDetailModel`). */
class GiftDetailViewModel(
    private val itemId: UUID,
    private val session: AppSession,
    private val items: ItemRepository,
    private val reservations: ReservationRepository,
    private val pots: PotRepository,
    private val notifier: ItemNotifier,
    private val thanks: ThanksRepository,
    private val priceHistory: PriceHistoryRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
    /** Taux de change courants (`CurrencyService.rates`) ; aucun par défaut. */
    private val rates: StateFlow<ExchangeRates?> = MutableStateFlow(null),
) : ViewModel() {
    private val loader = ItemLoader(items, clock)

    private val _local = MutableStateFlow(GiftDetailUiState(isMyUserId = session.state.value.userId))

    val uiState: StateFlow<GiftDetailUiState> = combine(_local, session.state, rates) { local, s, r -> derive(local, s, r) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(_local.value, session.state.value, rates.value))

    /** Le pays du lecteur et l'appartenance de l'enfant suivent la session. */
    private fun derive(local: GiftDetailUiState, s: SessionState, r: ExchangeRates?): GiftDetailUiState {
        val item = local.item
        val parentView = isParentView(s, item)
        return local.copy(
            child = item?.let { s.child(it.childId) },
            isParentView = parentView,
            isMyUserId = s.userId,
            profileCurrency = s.profile?.currency,
            rates = r,
            links = StoreCatalog.sorted(local.links, s.profile?.country),
            canMarkReceived = item?.let { ReceiptRules.canMarkReceived(parentView, it.kind, it.owned, local.eventDate, clock) } ?: false,
            canSendThanks = item?.let { ReceiptRules.canSendThanks(parentView, it.kind, it.owned, local.eventDate, clock) } ?: false,
        )
    }

    /**
     * Vue parent : la session tranche pour un enfant de la famille courante ; pour une fiche ouverte par son seul
     * identifiant (enfant hors famille), on se fie au serveur — `status == null` signifie « mode surprise » — et,
     * sans cadeau chargé, on ne montre rien de plus.
     */
    private fun isParentView(s: SessionState, item: WishItem?): Boolean =
        item?.let { GiftActionReducer.isParentView(s.child(it.childId)?.let { child -> s.isParent(child) }, it) } ?: true

    init {
        viewModelScope.launch { load() }
    }

    private val item: WishItem? get() = _local.value.item

    private val isParentView: Boolean get() = isParentView(session.state.value, item)

    /** (Re)charge le cadeau, ses liens et, si nécessaire, les participants. Aussi appelée après une participation. */
    suspend fun load() {
        val result = runCatchingCancellable { loader.load(itemId, session.state.value.events) }
        result.onSuccess { loaded ->
            _local.update {
                it.copy(
                    isLoading = false, item = loaded.item, links = loaded.links, readOnly = loaded.readOnly,
                    eventDate = loaded.eventDate,
                )
            }
            loadParticipants()
            loadDonors()
            loadTrends()
        }.onFailure { failure ->
            val error = GiftError.from(failure)
            _local.update { it.copy(isLoading = false, error = error, isClosed = error == GiftError.NotFound) }
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Après une participation à la cagnotte : recharge le montant réuni puis célèbre. */
    fun onPotJoined() {
        viewModelScope.launch {
            load()
            session.itemsChanged()
            _local.update { it.copy(celebration = Celebration.POT) }
        }
    }

    // region Actions

    fun reserve() = perform {
        val current = requireItem()
        reservations.reserve(current.id)
        val parent = isParentView
        update(GiftActionReducer.reserved(current, parent))
        _local.update { it.copy(celebration = Celebration.RESERVED) }
        // #58 : prévient anonymement les autres foyers, pour éviter un second achat. Sans attente ni annulation.
        notifier.notifyReservation(current.id)
    }

    fun cancelReservation() = perform {
        val current = requireItem()
        reservations.cancel(current.id)
        update(GiftActionReducer.cancelled(current, isParentView))
    }

    fun setPurchased(purchased: Boolean) = perform {
        val current = requireItem()
        reservations.setPurchased(current.id, purchased)
        update(GiftActionReducer.purchased(current, purchased))
    }

    fun toggleFavorite() = perform {
        val current = requireItem()
        val next = GiftActionReducer.favoriteToggled(current)
        items.setPriority(current.id, next.isFavorite)
        update(next)
    }

    fun toggleOwned() = perform {
        val current = requireItem()
        val next = GiftActionReducer.ownedToggled(current)
        items.setOwned(current.id, next.owned)
        update(next)
    }

    /** Marque le cadeau comme reçu (#42) ; l'écran de remerciement s'ouvre si l'appel a réussi. */
    fun markReceived() = perform {
        val current = requireItem()
        items.setOwned(current.id, true)
        update(current.copy(owned = true))
        _local.update { it.copy(thanksRequested = true) }
        loadDonors()
    }

    fun delete() = perform {
        val current = requireItem()
        items.deleteItem(current.id)
        _local.update { it.copy(isClosed = true) }
        session.itemsChanged()
    }

    fun leavePot() = perform {
        val current = requireItem()
        pots.leave(current.id)
        load()
        session.itemsChanged()
    }

    // endregion

    // region Chargements

    /**
     * Participants d'une cagnotte : seulement si j'y participe et que je ne suis pas parent (le serveur ne les
     * renvoie qu'aux participants ; un parent ne doit jamais voir qui participe).
     */
    private suspend fun loadParticipants() {
        val current = item
        if (current == null || !current.isInMyPot || isParentView) {
            _local.update { it.copy(participants = emptyList()) }
            return
        }
        runCatchingCancellable { pots.participants(current.id) }
            .onSuccess { list -> _local.update { it.copy(participants = list) } }
            .onFailure { failure -> _local.update { it.copy(error = GiftError.from(failure)) } }
    }

    /**
     * Donateurs qui se sont fait connaître : un parent, sur un cadeau reçu. Le serveur ne renvoie que ceux qui l'ont
     * choisi ; un échec n'est pas signalé (la mention « Offert par » est un plus).
     */
    private suspend fun loadDonors() {
        val current = item
        if (current == null || !current.owned || !isParentView) {
            _local.update { it.copy(donors = emptyList()) }
            return
        }
        runCatchingCancellable { thanks.itemDonors(current.id) }
            .onSuccess { list -> _local.update { it.copy(donors = list) } }
    }

    /**
     * Évolution des prix : seulement si j'ai réservé le cadeau (le serveur ne renvoie rien aux autres). Un échec ou
     * un historique vide donne une liste vide, sans erreur : la section est un plus.
     */
    private suspend fun loadTrends() {
        val current = item
        if (current == null || current.myReservation == null) {
            _local.update { it.copy(trends = emptyList()) }
            return
        }
        val trends = runCatchingCancellable { PriceTrend.trends(priceHistory.priceHistory(current.id)) }.getOrDefault(emptyList())
        _local.update { it.copy(trends = trends) }
    }

    // endregion

    fun consumeCelebration() = _local.update { it.copy(celebration = null) }

    fun consumeThanksRequest() = _local.update { it.copy(thanksRequested = false) }

    fun dismissError() = _local.update { it.copy(error = null) }

    private fun requireItem(): WishItem = item ?: error("ITEM_NOT_FOUND")

    private fun update(next: WishItem) {
        _local.update { it.copy(item = next) }
        // Participants et liste de l'enfant dépendent de l'état du cadeau.
        session.itemsChanged()
    }

    /**
     * Enveloppe commune : indicateur d'activité, erreur remontée, et règle du cadeau pris (quelqu'un d'autre a
     * réservé entre-temps : le cadeau est pris, pas disponible).
     */
    private fun perform(action: suspend () -> Unit) {
        if (_local.value.isWorking) return
        _local.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable { action() }.exceptionOrNull()
            _local.update { state ->
                val error = failure?.let(GiftError::from)
                val current = state.item
                state.copy(
                    isWorking = false,
                    error = error,
                    item = if (error == GiftError.Unavailable && current != null) {
                        GiftActionReducer.unavailable(current, isParentView)
                    } else {
                        current
                    },
                )
            }
        }
    }
}

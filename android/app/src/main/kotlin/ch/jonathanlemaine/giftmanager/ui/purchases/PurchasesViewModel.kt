package ch.jonathanlemaine.giftmanager.ui.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.currency.IndicativePrice
import ch.jonathanlemaine.giftmanager.core.currency.RatesApproximator
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.purchases.PurchasesLogic
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import ch.jonathanlemaine.giftmanager.data.PotRepository
import ch.jonathanlemaine.giftmanager.data.PurchaseRepository
import ch.jonathanlemaine.giftmanager.data.ReservationRepository
import ch.jonathanlemaine.giftmanager.data.ThanksRepository
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * « Mes achats » (iOS : `MyPurchasesView` + `MyPurchasesModel`). Les réservations, les parts de cagnotte et les
 * liens ne vivent qu'ici, en mémoire : ni dans la session ni sur le disque, car ils révèlent ce que j'offre.
 */
data class PurchasesUiState(
    val reservations: List<MyReservation> = emptyList(),
    val contributions: List<MyContribution> = emptyList(),
    val links: Map<UUID, List<ItemLink>> = emptyMap(),
    val preferredCountry: String? = null,
    /** Devise du profil : les totaux et prix d'une autre devise y sont convertis à titre indicatif (#74). */
    val profileCurrency: String? = null,
    /** Taux courants ; `null` tant qu'aucun n'est connu (aucune conversion affichée). */
    val rates: ExchangeRates? = null,
    /** Faux jusqu'à la fin du premier chargement (réussi ou non) : l'écran affiche un indicateur. */
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Remerciements reçus pour mes cadeaux (#42, #75) : donne la bannière qui ouvre la boîte. */
    val thanksCount: Int = 0,
    /** Une action (acheté, annuler, quitter) est en cours. */
    val isWorking: Boolean = false,
    /** Participation dont on demande confirmation avant de la quitter. */
    val leaving: MyContribution? = null,
    val error: GiftError? = null,
) {
    val groups: List<PurchasesLogic.EventGroup> get() = PurchasesLogic.groups(reservations)

    val remainingCount: Int get() = PurchasesLogic.remainingCount(reservations)

    val summaryCountLabel: String get() = PurchasesLogic.summaryCountLabel(reservations, contributions)

    val isEmpty: Boolean get() = hasLoaded && reservations.isEmpty() && contributions.isEmpty()

    /** Total par devise, jamais mélangées. */
    val totals: List<PurchasesLogic.CurrencyTotal> get() = PurchasesLogic.totals(reservations, contributions, links, preferredCountry)

    /** Lien le plus pertinent d'un cadeau : donne le prix, la boutique et le pays de sa ligne. */
    fun bestLink(itemId: UUID): ItemLink? = PurchasesLogic.bestLink(itemId, links, preferredCountry)

    private val approximator get() = RatesApproximator(rates)

    /** « ≈ 214 € » à côté du prix d'un lien dans une autre devise que celle du profil, sinon `null`. */
    fun approxText(link: ItemLink?): String? = link?.let { approximator.approxText(it.price, it.currency, profileCurrency) }

    /** Idem pour ma part d'une cagnotte. */
    fun approxText(contribution: MyContribution): String? =
        approximator.approxText(contribution.amount, contribution.currency, profileCurrency)

    private val convertedTotal
        get() = approximator.convertedTotal(totals.map { it.currency to it.amount }, profileCurrency)?.let {
            IndicativePrice.amountText(it, profileCurrency.orEmpty())
        }

    /** « ≈ 412 € au total · indicatif » ; absent si tout est déjà dans la devise du profil ou si un taux manque. */
    val convertedTotalText: String? get() = convertedTotal?.let { "≈ $it au total · indicatif" }

    val convertedTotalSpokenText: String? get() = convertedTotal?.let { "environ $it au total, conversion indicative" }

    /** Une conversion est affichée quelque part : la mention « conversion indicative » est due. */
    val hasConversion: Boolean
        get() = convertedTotalText != null ||
            approximator.hasConversion(
                reservations.map { bestLink(it.itemId) }.map { it?.price to it?.currency } +
                    contributions.map { it.amount to it.currency },
                profileCurrency,
            )

    /** Date des taux (« 2 octobre »), pour la mention « taux BCE du … » ; `null` si aucun taux. */
    val rateDateText: String? get() = rates?.publicationText
}

class PurchasesViewModel(
    private val session: AppSession,
    private val purchases: PurchaseRepository,
    private val items: ItemRepository,
    private val reservations: ReservationRepository,
    private val pots: PotRepository,
    /** Taux de change courants (`CurrencyService.rates`). */
    private val rates: StateFlow<ExchangeRates?>,
    private val thanks: ThanksRepository,
) : ViewModel() {
    private data class Local(
        val reservations: List<MyReservation> = emptyList(),
        val contributions: List<MyContribution> = emptyList(),
        val links: Map<UUID, List<ItemLink>> = emptyMap(),
        val hasLoaded: Boolean = false,
        val isRefreshing: Boolean = false,
        val thanksCount: Int = 0,
        val isWorking: Boolean = false,
        val leaving: MyContribution? = null,
        val error: GiftError? = null,
    )

    private val local = MutableStateFlow(Local())
    /** Chargements en cours (fil principal uniquement) : un rafraîchissement ne double pas un chargement actif. */
    private var loading = 0

    val uiState: StateFlow<PurchasesUiState> = combine(local, session.state, rates) { l, s, r -> build(l, s, r) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(local.value, session.state.value, rates.value))

    private fun build(l: Local, s: SessionState, r: ExchangeRates?) = PurchasesUiState(
        reservations = l.reservations,
        contributions = l.contributions,
        links = l.links,
        preferredCountry = s.profile?.country,
        profileCurrency = s.profile?.currency,
        rates = r,
        hasLoaded = l.hasLoaded,
        isRefreshing = l.isRefreshing,
        thanksCount = l.thanksCount,
        isWorking = l.isWorking,
        leaving = l.leaving,
        error = l.error,
    )

    init {
        // Au départ, puis à chaque changement de cadeaux (réservation, achat, participation…) ; collectLatest
        // abandonne un chargement devenu obsolète.
        viewModelScope.launch { session.itemsRevision.collectLatest { load() } }
    }

    /**
     * Recharge, sauf si un chargement est déjà en cours : appelée en tirant pour actualiser et quand l'écran
     * revient au premier plan (`ON_RESUME`), car une réservation peut changer hors de l'app.
     */
    fun refresh() {
        if (loading > 0) return
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        loading++
        local.update { it.copy(isRefreshing = it.hasLoaded) }
        val result = try {
            runCatchingCancellable {
                coroutineScope {
                    val mine = async { purchases.myReservations() }
                    val pots = async { purchases.myContributions() }
                    val reserved = mine.await()
                    val links = items.links(reserved.map { it.itemId }).groupBy { it.itemId }
                    Triple(reserved, pots.await(), links)
                }
            }
        } finally {
            loading--
        }
        // Une erreur garde ce qui était affiché plutôt que de vider l'écran ; le chargement est marqué dans tous les cas.
        result.onSuccess { (reserved, shares, links) ->
            local.update { it.copy(reservations = reserved, contributions = shares, links = links, hasLoaded = true, isRefreshing = false) }
        }.onFailure { failure ->
            local.update { it.copy(hasLoaded = true, isRefreshing = false, error = GiftError.from(failure)) }
        }
        // Le décompte des remerciements est un plus : un échec garde le dernier connu, sans erreur.
        runCatchingCancellable { thanks.myThanks().size }.onSuccess { count -> local.update { it.copy(thanksCount = count) } }
    }

    // region Actions

    /** Bascule acheté / pas encore acheté ; la liste se recharge via [AppSession.itemsChanged]. */
    fun togglePurchased(reservation: MyReservation) = perform {
        val purchased = reservation.status != ReservationState.PURCHASED
        reservations.setPurchased(reservation.itemId, purchased)
    }

    fun cancel(reservation: MyReservation) = perform { reservations.cancel(reservation.itemId) }

    fun requestLeave(contribution: MyContribution) = local.update { it.copy(leaving = contribution) }

    fun cancelLeave() = local.update { it.copy(leaving = null) }

    fun confirmLeave() {
        val contribution = local.value.leaving ?: return
        local.update { it.copy(leaving = null) }
        perform { pots.leave(contribution.itemId) }
    }

    // endregion

    fun dismissError() = local.update { it.copy(error = null) }

    private fun perform(action: suspend () -> Unit) {
        if (local.value.isWorking) return
        local.update { it.copy(isWorking = true, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable { action() }.exceptionOrNull()
            // Écrit : les autres écrans (liste de l'enfant, fiche, budgets) et celui-ci se rechargent.
            if (failure == null) session.itemsChanged()
            local.update { it.copy(isWorking = false, error = failure?.let(GiftError::from) ?: it.error) }
        }
    }
}

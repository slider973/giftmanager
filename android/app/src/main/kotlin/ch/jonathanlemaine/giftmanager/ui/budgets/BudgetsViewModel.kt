package ch.jonathanlemaine.giftmanager.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.budgets.BudgetMath
import ch.jonathanlemaine.giftmanager.core.currency.ExchangeRates
import ch.jonathanlemaine.giftmanager.core.currency.IndicativePrice
import ch.jonathanlemaine.giftmanager.core.currency.RatesApproximator
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.event
import ch.jonathanlemaine.giftmanager.data.BudgetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un budget prêt à afficher : tout est calculé ici, la carte ne fait que le montrer. */
data class BudgetRow(
    val budget: Budget,
    val level: BudgetMath.Level,
    /** « 180 / 200 CHF ». */
    val gaugeText: String,
    /** « Reste 20 CHF », « Plus que 20 CHF » ou « Dépassé de 45 CHF ». */
    val statusText: String,
    /** « ≈ 170 / 190 € » dans la devise du profil, si elle diffère et que les taux sont connus. */
    val approxText: String?,
)

/** Total converti de budgets en plusieurs devises. */
data class IndicativeTotal(val text: String, val spokenText: String)

data class BudgetsUiState(
    val rows: List<BudgetRow> = emptyList(),
    /** Faux jusqu'à la fin du premier chargement (réussi ou non). */
    val hasLoaded: Boolean = false,
    val indicativeTotal: IndicativeTotal? = null,
    /** Date des taux (« 2 octobre »), pour la mention « conversion indicative » ; `null` si aucun taux. */
    val rateDateText: String? = null,
    val error: GiftError? = null,
) {
    /** Aucun budget défini : l'écran propose d'en créer un. */
    val showsInvitation: Boolean get() = hasLoaded && rows.isEmpty()

    /** Au moins une conversion est affichée : la mention « conversion indicative » est due. */
    val hasConversion: Boolean get() = indicativeTotal != null || rows.any { it.approxText != null }
}

/** Section « Budgets » de Mes achats (iOS : `BudgetsSection`). Les budgets sont privés et ne vivent qu'en mémoire. */
class BudgetsViewModel(
    private val session: AppSession,
    private val budgets: BudgetRepository,
    private val rates: StateFlow<ExchangeRates?>,
) : ViewModel() {
    private data class Local(
        val budgets: List<Budget> = emptyList(),
        val hasLoaded: Boolean = false,
        val error: GiftError? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<BudgetsUiState> = combine(local, session.state, rates) { l, s, r -> build(l, s, r) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(local.value, session.state.value, rates.value))

    init {
        // Au départ, puis à chaque changement de cadeaux ou de budget (l'éditeur le signale à l'enregistrement).
        viewModelScope.launch { session.itemsRevision.collectLatest { load() } }
    }

    private fun build(l: Local, s: SessionState, r: ExchangeRates?): BudgetsUiState {
        val profileCurrency = s.profile?.currency
        val approximator = RatesApproximator(r)
        // `my_budgets()` ne garantit aucun ordre : événement le plus proche d'abord, puis prénom.
        val sorted = BudgetMath.sorted(
            l.budgets,
            eventDate = { budget -> s.event(budget.eventId)?.eventDate },
            childName = { budget -> s.child(budget.childId)?.firstName ?: budget.childName },
        )
        val rows = sorted.map { budget ->
            val level = BudgetMath.level(budget)
            BudgetRow(
                budget = budget,
                level = level,
                gaugeText = BudgetMath.gaugeText(budget.spent, budget.amount, budget.currency),
                statusText = statusText(budget, level),
                approxText = approxText(budget, profileCurrency, approximator),
            )
        }
        return BudgetsUiState(
            rows = rows,
            hasLoaded = l.hasLoaded,
            indicativeTotal = indicativeTotal(sorted, profileCurrency, approximator),
            rateDateText = r?.publicationText,
            error = l.error,
        )
    }

    private fun statusText(budget: Budget, level: BudgetMath.Level): String = when (level) {
        BudgetMath.Level.OVER -> "Dépassé de ${BudgetMath.amountText(budget.remaining.negate(), budget.currency)}"
        BudgetMath.Level.NEARLY_REACHED -> "Plus que ${BudgetMath.amountText(budget.remaining, budget.currency)}"
        BudgetMath.Level.COMFORTABLE -> "Reste ${BudgetMath.amountText(budget.remaining, budget.currency)}"
    }

    /** « ≈ 170 / 190 € » : jauge convertie dans la devise du profil, arrondie comme les autres montants indicatifs. */
    private fun approxText(budget: Budget, profileCurrency: String?, approximator: RatesApproximator): String? {
        val spent = approximator.convert(budget.spent, budget.currency, profileCurrency) ?: return null
        val amount = approximator.convert(budget.amount, budget.currency, profileCurrency) ?: return null
        return "≈ " + BudgetMath.gaugeText(IndicativePrice.rounded(spent), IndicativePrice.rounded(amount), profileCurrency.orEmpty())
    }

    /**
     * Total converti indicatif, seulement si les budgets sont plusieurs, ne se recoupent pas (sinon un même cadeau
     * serait compté deux fois), mélangent les devises, et que tous les taux sont connus.
     */
    private fun indicativeTotal(sorted: List<Budget>, profileCurrency: String?, approximator: RatesApproximator): IndicativeTotal? {
        if (profileCurrency == null || sorted.size <= 1 || !BudgetMath.areDisjoint(sorted)) return null
        val spent = approximator.convertedTotal(sorted.map { it.currency to it.spent }, profileCurrency) ?: return null
        val amount = approximator.convertedTotal(sorted.map { it.currency to it.amount }, profileCurrency) ?: return null
        val spentText = IndicativePrice.amountText(spent, profileCurrency)
        val amountText = IndicativePrice.amountText(amount, profileCurrency)
        return IndicativeTotal(
            text = "≈ $spentText sur $amountText au total · indicatif",
            spokenText = "environ $spentText dépensés sur $amountText au total, conversion indicative",
        )
    }

    private suspend fun load() {
        runCatchingCancellable { budgets.myBudgets() }
            .onSuccess { fetched -> local.update { it.copy(budgets = fetched, hasLoaded = true) } }
            // Une erreur garde les budgets affichés ; le chargement est marqué dans tous les cas.
            .onFailure { failure -> local.update { it.copy(hasLoaded = true, error = GiftError.from(failure)) } }
    }

    /** Tirer pour actualiser, ou retour sur l'écran. */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    fun dismissError() = local.update { it.copy(error = null) }
}

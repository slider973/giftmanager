package ch.jonathanlemaine.giftmanager.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.budgets.BudgetMath
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewParser
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.upcomingEvents
import ch.jonathanlemaine.giftmanager.data.BudgetRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce que l'éditeur fait (iOS : `BudgetEditorView.Target`). */
sealed interface BudgetEditorMode {
    data object Create : BudgetEditorMode

    /** Seuls l'identifiant, le périmètre, le montant et la devise du budget servent. */
    data class Edit(val budget: Budget) : BudgetEditorMode
}

data class BudgetEditorUiState(
    val isEditing: Boolean = false,
    val childId: UUID? = null,
    val eventId: UUID? = null,
    val amountText: String = "",
    val currency: String = "EUR",
    /** Enfants proposés (« Tous les enfants » = aucun). */
    val children: List<Child> = emptyList(),
    /** Événements à venir, plus celui du budget édité s'il est passé. */
    val events: List<GiftEvent> = emptyList(),
    val currencies: List<String> = Countries.currencies,
    /** Montant lu dans [amountText] (positif, plafonné, à deux décimales) ; `null` tant qu'il est vide ou illisible. */
    val amount: BigDecimal? = null,
    /** Texte non vide mais illisible : le champ passe en erreur. */
    val showsInvalid: Boolean = false,
    val isSaving: Boolean = false,
    val confirmDelete: Boolean = false,
    val error: GiftError? = null,
    /** L'écran doit se fermer (enregistré, supprimé). */
    val finished: Boolean = false,
) {
    /** Un budget vise au moins un enfant ou un événement. */
    val hasScope: Boolean get() = childId != null || eventId != null

    val canSave: Boolean get() = hasScope && amount != null && !isSaving
}

/** Définir, modifier ou supprimer un budget privé (iOS : `BudgetEditorView`). */
class BudgetEditorViewModel(
    private val mode: BudgetEditorMode,
    private val session: AppSession,
    private val budgets: BudgetRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private val existing: Budget? = (mode as? BudgetEditorMode.Edit)?.budget

    private data class Local(
        val childId: UUID? = null,
        val eventId: UUID? = null,
        val amountText: String = "",
        /** Devise choisie à la main ; à défaut celle du budget édité, du profil, ou l'euro. */
        val chosenCurrency: String? = null,
        val isSaving: Boolean = false,
        val confirmDelete: Boolean = false,
        val error: GiftError? = null,
        val finished: Boolean = false,
    )

    private val local = MutableStateFlow(
        existing?.let {
            Local(childId = it.childId, eventId = it.eventId, amountText = BudgetMath.number(it.amount), chosenCurrency = it.currency)
        } ?: Local(),
    )

    val uiState: StateFlow<BudgetEditorUiState> = combine(local, session.state) { l, s -> derive(l, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(local.value, session.state.value))

    private fun derive(l: Local, s: SessionState): BudgetEditorUiState {
        val amount = parseAmount(l.amountText)
        val upcoming = s.upcomingEvents(clock)
        val edited = existing?.eventId?.let { id -> s.events.firstOrNull { it.id == id } }
        return BudgetEditorUiState(
            isEditing = existing != null,
            childId = l.childId,
            eventId = l.eventId,
            amountText = l.amountText,
            currency = l.chosenCurrency ?: s.profile?.currency ?: DEFAULT_CURRENCY,
            children = s.children,
            events = if (edited != null && upcoming.none { it.id == edited.id }) upcoming + edited else upcoming,
            amount = amount,
            showsInvalid = l.amountText.trim().isNotEmpty() && amount == null,
            isSaving = l.isSaving,
            confirmDelete = l.confirmDelete,
            error = l.error,
            finished = l.finished,
        )
    }

    fun onChildChange(childId: UUID?) = local.update { it.copy(childId = childId, error = null) }

    fun onEventChange(eventId: UUID?) = local.update { it.copy(eventId = eventId, error = null) }

    fun onAmountChange(text: String) = local.update { it.copy(amountText = text, error = null) }

    fun onCurrencyChange(currency: String) = local.update { it.copy(chosenCurrency = currency, error = null) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun requestDelete() = local.update { if (existing != null) it.copy(confirmDelete = true) else it }

    fun cancelDelete() = local.update { it.copy(confirmDelete = false) }

    fun save() {
        val state = uiState.value
        val amount = state.amount
        if (!state.canSave || amount == null) return
        local.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            var saved = false
            val failure = runCatchingCancellable {
                val newId = budgets.setBudget(state.childId, state.eventId, amount, state.currency)
                saved = true
                // Périmètre ou devise changés : set_budget a créé un autre budget, on retire l'ancien.
                existing?.takeIf { it.id != newId }?.let { budgets.deleteBudget(it.id) }
            }.exceptionOrNull()
            finish(failure, changed = saved)
        }
    }

    fun delete() {
        val budget = existing ?: return
        if (uiState.value.isSaving) return
        local.update { it.copy(confirmDelete = false, isSaving = true, error = null) }
        viewModelScope.launch {
            val failure = runCatchingCancellable { budgets.deleteBudget(budget.id) }.exceptionOrNull()
            finish(failure, changed = failure == null)
        }
    }

    /** Un budget a pu changer même si l'écriture a échoué en route : la liste se recharge dans ce cas. */
    private fun finish(failure: Throwable?, changed: Boolean) {
        if (changed) session.itemsChanged()
        local.update {
            if (failure == null) it.copy(isSaving = false, finished = true) else it.copy(isSaving = false, error = GiftError.from(failure))
        }
    }

    companion object {
        const val DEFAULT_CURRENCY = "EUR"
        val MAXIMUM: BigDecimal = BigDecimal(99_999_999)

        /** « 200 », « 199,90 », « 1 200 » : strictement positif, au plus [MAXIMUM], arrondi à deux décimales. */
        fun parseAmount(text: String): BigDecimal? {
            val value = LinkPreviewParser.parsePrice(text)?.setScale(2, RoundingMode.HALF_UP) ?: return null
            return value.takeIf { it.signum() > 0 && it <= MAXIMUM }
        }
    }
}

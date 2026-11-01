package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.gifts.PotJoinLogic
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.PotAmount
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import ch.jonathanlemaine.giftmanager.data.PotRepository
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JoinPotUiState(
    val isLoading: Boolean = true,
    val item: WishItem? = null,
    /** Prénom de l'enfant, si la session le connaît. */
    val childName: String? = null,
    val progress: PotProgress? = null,
    val amountText: String = "",
    /** Montant lu dans [amountText] ; `null` tant qu'il est vide ou illisible. */
    val amount: BigDecimal? = null,
    /** Texte non vide mais illisible : le champ passe en erreur. */
    val showsInvalid: Boolean = false,
    val currency: String = PotJoinLogic.FALLBACK_CURRENCY,
    /** Devise imposée par la cagnotte existante : pas de sélecteur. */
    val lockedCurrency: String? = null,
    val currencies: List<String> = Countries.currencies,
    val suggestions: List<BigDecimal> = emptyList(),
    /** Reste à réunir hors ma part ; sert à repérer la suggestion « Le reste ». */
    val remainingForMe: BigDecimal? = null,
    /** Je modifie ma part plutôt que d'en créer une. */
    val isUpdate: Boolean = false,
    val isSaving: Boolean = false,
    val error: GiftError? = null,
    /** La participation est enregistrée : la feuille peut se fermer. */
    val finished: Boolean = false,
) {
    val canSave: Boolean get() = amount != null && !isSaving && item != null
}

/** Participer à une cagnotte, ou modifier sa part (iOS : `JoinPotView`). */
class JoinPotViewModel(
    private val itemId: UUID,
    private val session: AppSession,
    items: ItemRepository,
    private val pots: PotRepository,
    clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private data class Local(
        val isLoading: Boolean = true,
        val item: WishItem? = null,
        val links: List<ItemLink> = emptyList(),
        val amountText: String = "",
        /** Devise choisie à la main ; ignorée quand la cagnotte impose la sienne. */
        val chosenCurrency: String? = null,
        val isSaving: Boolean = false,
        val error: GiftError? = null,
        val finished: Boolean = false,
    )

    private val loader = ItemLoader(items, clock)
    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<JoinPotUiState> = combine(local, session.state) { l, s -> derive(l, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, derive(local.value, session.state.value))

    private fun derive(l: Local, s: SessionState): JoinPotUiState {
        val item = l.item
        val locked = item?.potCurrency
        val currency = locked ?: l.chosenCurrency ?: PotJoinLogic.defaultCurrency(null, l.links, s.profile?.currency)
        val progress = item?.let { PotProgress.make(it, l.links) }
        val remaining = PotJoinLogic.remainingForMe(progress, item?.myContribution, currency, l.links)
        val amount = PotAmount.parse(l.amountText)
        return JoinPotUiState(
            isLoading = l.isLoading,
            item = item,
            childName = item?.let { s.child(it.childId)?.firstName },
            progress = progress,
            amountText = l.amountText,
            amount = amount,
            showsInvalid = l.amountText.trim().isNotEmpty() && amount == null,
            currency = currency,
            lockedCurrency = locked,
            suggestions = PotJoinLogic.suggestions(remaining),
            remainingForMe = remaining,
            isUpdate = item?.isInMyPot == true,
            isSaving = l.isSaving,
            error = l.error,
            finished = l.finished,
        )
    }

    init {
        viewModelScope.launch {
            runCatchingCancellable { loader.load(itemId, session.state.value.events) }
                .onSuccess { loaded ->
                    local.update {
                        it.copy(
                            isLoading = false, item = loaded.item, links = loaded.links,
                            // Modifier sa part part du montant actuel.
                            amountText = loaded.item.myContribution?.let(PotAmount::editableText) ?: it.amountText,
                        )
                    }
                }
                .onFailure { failure -> local.update { it.copy(isLoading = false, error = GiftError.from(failure)) } }
        }
    }

    fun onAmountChange(text: String) = local.update { it.copy(amountText = text, error = null) }

    /** Sans effet quand la cagnotte impose sa devise. */
    fun onCurrencyChange(currency: String) {
        if (uiState.value.lockedCurrency != null) return
        local.update { it.copy(chosenCurrency = currency) }
    }

    fun onSuggestion(amount: BigDecimal) = local.update { it.copy(amountText = PotAmount.editableText(amount), error = null) }

    fun save() {
        val state = uiState.value
        val amount = state.amount ?: return
        if (state.isSaving || state.item == null) return
        local.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            // Une cagnotte existante impose sa devise ; un écart vient du serveur (CURRENCY_MISMATCH).
            val failure = runCatchingCancellable { pots.join(itemId, amount, state.currency) }.exceptionOrNull()
            if (failure == null) session.itemsChanged()
            local.update {
                it.copy(isSaving = false, error = failure?.let(GiftError::from), finished = failure == null || it.finished)
            }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    companion object {
        /** Cadeau visé, passé par les `extras` de la fabrique (la feuille n'a pas de route propre). */
        val ITEM_ID_KEY = object : CreationExtras.Key<UUID> {}
    }
}

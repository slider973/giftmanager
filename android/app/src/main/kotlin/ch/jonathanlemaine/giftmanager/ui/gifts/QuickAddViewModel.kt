package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.currentEvent
import ch.jonathanlemaine.giftmanager.core.session.myChildren
import ch.jonathanlemaine.giftmanager.core.session.otherChildren
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Une liste où ajouter depuis le bouton « + » : [kind] est un souhait pour mes enfants, une idée pour les autres. */
data class QuickAddTarget(val child: Child, val kind: WishKind)

data class QuickAddUiState(
    /** Mes enfants et listes d'adultes : on y ajoute des envies (visibles de moi). */
    val myLists: List<QuickAddTarget> = emptyList(),
    /** Les enfants des autres foyers : on y propose des idées (cachées de leurs parents). */
    val otherLists: List<QuickAddTarget> = emptyList(),
    /** Événement proposé par défaut : le prochain Noël, sinon le prochain événement. */
    val defaultEventId: UUID? = null,
) {
    val isEmpty: Boolean get() = myLists.isEmpty() && otherLists.isEmpty()
}

/** Feuille « + » de la barre d'onglets : choisir la liste où ajouter un cadeau. */
class QuickAddViewModel(session: AppSession, private val clock: Clock = Clock.systemDefaultZone()) : ViewModel() {
    val uiState: StateFlow<QuickAddUiState> = session.state.map(::build)
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(session.state.value))

    private fun build(s: SessionState) = QuickAddUiState(
        myLists = s.myChildren.map { QuickAddTarget(it, WishKind.WISH) },
        otherLists = s.otherChildren.map { QuickAddTarget(it, WishKind.IDEA) },
        defaultEventId = s.currentEvent(clock)?.id,
    )
}

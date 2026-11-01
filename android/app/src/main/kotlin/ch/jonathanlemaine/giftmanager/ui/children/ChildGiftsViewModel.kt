package ch.jonathanlemaine.giftmanager.ui.children

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.child
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Provisoire (#73) : ne porte que le prénom affiché par [ChildGiftsPlaceholder]. #73 le remplace par le vrai
 * ViewModel de la liste, avec les mêmes arguments de route (`ChildGiftsRoute`).
 */
class ChildGiftsViewModel(childId: UUID, session: AppSession) : ViewModel() {
    val childName: StateFlow<String?> = session.state
        .map { it.child(childId)?.firstName }
        .stateIn(viewModelScope, SharingStarted.Eagerly, session.state.value.child(childId)?.firstName)
}

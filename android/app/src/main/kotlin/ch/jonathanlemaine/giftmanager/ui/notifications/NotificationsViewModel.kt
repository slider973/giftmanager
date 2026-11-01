package ch.jonathanlemaine.giftmanager.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.push.NotificationPermission
import ch.jonathanlemaine.giftmanager.push.NotificationPermissionController
import ch.jonathanlemaine.giftmanager.push.NotificationTarget
import ch.jonathanlemaine.giftmanager.push.PendingNavigation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Notifications push côté écrans (#76) : destination d'une notification touchée et autorisation `POST_NOTIFICATIONS`.
 * Tout l'état vit dans des singletons de `AppContainer` : plusieurs écrans (écran principal, Profil) peuvent utiliser
 * ce ViewModel sans se désynchroniser.
 */
class NotificationsViewModel(
    private val navigation: PendingNavigation,
    private val permissions: NotificationPermissionController,
    childModeLock: ChildModeLock,
) : ViewModel() {
    /** Écran à ouvrir ; `null` sans notification en attente ou pendant le mode enfant. À acquitter par [consumeTarget]. */
    val pendingTarget: StateFlow<NotificationTarget?> =
        navigation.deliverable().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun consumeTarget(target: NotificationTarget) = navigation.consume(target)

    /** État de l'autorisation (bandeau du Profil : [NotificationPermission.CanAsk] → « Activer », [NotificationPermission.PermanentlyDenied] → réglages du système). */
    val permission: StateFlow<NotificationPermission> = permissions.permission

    /** Feuille d'explication à afficher (une seule fois, première arrivée sur l'écran principal) ; jamais pendant le mode enfant. */
    val promptVisible: StateFlow<Boolean> =
        combine(permissions.promptVisible, childModeLock.active) { visible, childMode -> visible && !childMode }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** L'écran principal vient d'apparaître. */
    fun onMainScreenReached() {
        viewModelScope.launch { permissions.onMainScreenReached() }
    }

    /** Lance la demande du système (depuis la feuille ou le bandeau) ; pour [NotificationPermission.PermanentlyDenied], ouvrir `notificationSettingsIntent` à la place. */
    fun requestPermission() {
        viewModelScope.launch { permissions.requestPermission() }
    }

    fun dismissPrompt() = permissions.dismissPrompt()
}

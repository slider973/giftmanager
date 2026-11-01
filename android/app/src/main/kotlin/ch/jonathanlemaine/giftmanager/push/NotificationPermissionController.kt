package ch.jonathanlemaine.giftmanager.push

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ce que le contrôleur demande à Android : isolé pour être testé sans appareil. */
interface NotificationPermissionEnvironment {
    val sdk: Int

    fun isGranted(): Boolean

    fun shouldShowRationale(): Boolean

    /** Affiche la boîte de dialogue du système ; le résultat revient par [NotificationPermissionController.onRequestResult]. */
    fun launchRequest()
}

/**
 * Autorisation des notifications, partagée par toute l'app (singleton de `AppContainer`). [permission] se recalcule à
 * chaque [refresh] (retour au premier plan : l'utilisateur a pu changer le réglage dans les paramètres du système).
 *
 * Pour l'interface : [permission] pour un bandeau (Profil), [promptVisible] pour la feuille d'explication de
 * l'écran principal, [requestPermission] pour le bouton « Activer », et les réglages de l'app quand la permission est
 * [NotificationPermission.PermanentlyDenied].
 */
class NotificationPermissionController(
    private val environment: NotificationPermissionEnvironment,
    private val prefs: PushPrefs,
) {
    // Estimation avant le premier refresh() (qui lit aussi la mémoire de l'appareil) : jamais « refusée définitivement ».
    private val _permission = MutableStateFlow(
        NotificationPermission.resolve(environment.sdk, environment.isGranted(), shouldShowRationale = false, alreadyAsked = false),
    )
    val permission: StateFlow<NotificationPermission> = _permission.asStateFlow()

    private val _promptVisible = MutableStateFlow(false)

    /** La feuille d'explication doit s'afficher (une seule fois, première arrivée sur l'écran principal). */
    val promptVisible: StateFlow<Boolean> = _promptVisible.asStateFlow()

    suspend fun refresh() {
        _permission.value = NotificationPermission.resolve(
            sdk = environment.sdk,
            granted = environment.isGranted(),
            shouldShowRationale = environment.shouldShowRationale(),
            alreadyAsked = prefs.permissionAsked(),
        )
    }

    /** L'écran principal vient d'apparaître : annonce la feuille d'explication si c'est la première fois. */
    suspend fun onMainScreenReached() {
        refresh()
        if (NotificationPermission.shouldPrompt(_permission.value, prefs.promptShown())) {
            prefs.setPromptShown()
            _promptVisible.value = true
        }
    }

    fun dismissPrompt() {
        _promptVisible.value = false
    }

    /** Lance la demande du système ; sans effet si la permission n'est pas demandable (accordée, inutile ou refusée définitivement). */
    suspend fun requestPermission() {
        _promptVisible.value = false
        if (_permission.value != NotificationPermission.CanAsk) return
        prefs.setPermissionAsked()
        environment.launchRequest()
    }

    suspend fun onRequestResult() = refresh()
}

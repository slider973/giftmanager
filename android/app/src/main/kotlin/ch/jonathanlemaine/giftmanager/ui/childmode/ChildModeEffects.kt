package ch.jonathanlemaine.giftmanager.ui.childmode

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Effets de fenêtre du mode enfant (iOS : `statusBarHidden`, `persistentSystemOverlays(.hidden)`,
 * `interactiveDismissDisabled`) :
 *  - le retour système est consommé (la sortie passe par le bouton « Quitter » maintenu, puis la vérification) ;
 *  - les barres système sont masquées, et restaurées en quittant ;
 *  - l'épinglage d'écran (`startLockTask`) est demandé au mieux : sans épinglage activé par l'utilisateur, ou sur un
 *    appareil qui le refuse, rien ne se passe et le mode enfant reste protégé par le reste.
 */
@Composable
fun ChildModeEffects() {
    BackHandler(enabled = true) { /* consommé : on ne quitte pas par le retour */ }
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        runCatching { activity?.startLockTask() }
        onDispose {
            runCatching { activity?.stopLockTask() }
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

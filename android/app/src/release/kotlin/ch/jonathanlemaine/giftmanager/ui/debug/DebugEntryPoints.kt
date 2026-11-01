package ch.jonathanlemaine.giftmanager.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Outils de debug, version release : vide. La galerie du design system et la connexion aux comptes de
 * démo n'existent que dans `src/debug` ; ce fichier garde la même API pour que `main` compile sans elles.
 */
internal object DebugEntryPoints {
    const val HAS_DESIGN_SYSTEM_GALLERY: Boolean = false
    const val HAS_DEMO_SIGN_IN: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun DesignSystemGalleryScreen(onBack: () -> Unit) = Unit

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun DemoSignIn(enabled: Boolean, onError: (String?) -> Unit, modifier: Modifier = Modifier) = Unit
}

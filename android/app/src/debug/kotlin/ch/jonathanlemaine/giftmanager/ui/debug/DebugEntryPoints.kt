package ch.jonathanlemaine.giftmanager.ui.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Outils de debug, version debug : galerie du design system ([DesignSystemGallery]) et comptes de démo ([DemoSignInMenu]). */
internal object DebugEntryPoints {
    const val HAS_DESIGN_SYSTEM_GALLERY: Boolean = true
    const val HAS_DEMO_SIGN_IN: Boolean = true

    @Composable
    fun DesignSystemGalleryScreen(onBack: () -> Unit) = DesignSystemGallery(onBack = onBack)

    @Composable
    fun DemoSignIn(enabled: Boolean, onError: (String?) -> Unit, modifier: Modifier = Modifier) =
        DemoSignInMenu(enabled = enabled, onError = onError, modifier = modifier)
}

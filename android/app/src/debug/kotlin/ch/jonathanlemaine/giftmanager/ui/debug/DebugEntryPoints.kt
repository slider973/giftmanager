package ch.jonathanlemaine.giftmanager.ui.debug

import androidx.compose.runtime.Composable

/** Outils de debug, version debug : galerie du design system ([DesignSystemGallery]). */
internal object DebugEntryPoints {
    const val HAS_DESIGN_SYSTEM_GALLERY: Boolean = true

    @Composable
    fun DesignSystemGalleryScreen(onBack: () -> Unit) = DesignSystemGallery(onBack = onBack)
}

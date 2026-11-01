package ch.jonathanlemaine.giftmanager.ui.debug

import androidx.compose.runtime.Composable

/**
 * Outils de debug, version release : vide. La galerie du design system n'existe que dans
 * `src/debug` ; ce fichier garde la même API pour que `main` compile sans elle.
 */
internal object DebugEntryPoints {
    const val HAS_DESIGN_SYSTEM_GALLERY: Boolean = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun DesignSystemGalleryScreen(onBack: () -> Unit) = Unit
}

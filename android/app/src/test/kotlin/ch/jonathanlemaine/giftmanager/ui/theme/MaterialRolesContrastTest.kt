package ch.jonathanlemaine.giftmanager.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paires texte / fond des rôles Material 3 tels que projetés par `GiftColors.toColorScheme()` : un composant
 * M3 (Snackbar, Slider, sélection…) ne doit jamais poser un texte sous AA.
 */
class MaterialRolesContrastTest {
    private val themes = mapOf("clair" to ThemeTokens.Light, "sombre" to ThemeTokens.Dark)

    private fun assertContrast(min: Double, theme: String, label: String, fg: Long, bg: Long) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$theme : $label = %.2f (< $min)".format(ratio), ratio >= min)
    }

    @Test fun textRolesMeetAA() {
        themes.forEach { (n, t) ->
            assertContrast(4.5, n, "onSurface/surface", t.textPrimary, t.surface)
            assertContrast(4.5, n, "onSurfaceVariant/surface", t.textSecondary, t.surface)
            assertContrast(4.5, n, "onSurfaceVariant/surfaceVariant", t.textSecondary, t.background)
            assertContrast(4.5, n, "onSecondary/secondary", t.onPrimary, t.secondary)
            assertContrast(4.5, n, "onPrimaryContainer/primaryContainer", t.mineFg, t.mineBg)
            assertContrast(4.5, n, "onSecondaryContainer/secondaryContainer", t.availableFg, t.availableBg)
            assertContrast(4.5, n, "onTertiaryContainer/tertiaryContainer", t.potFg, t.potBg)
            assertContrast(4.5, n, "onError/error", t.onPrimary, t.takenFg)
            assertContrast(4.5, n, "onErrorContainer/errorContainer", t.takenFg, t.takenBg)
            assertContrast(4.5, n, "inverseOnSurface/inverseSurface", t.background, t.textPrimary)
            assertContrast(4.5, n, "primary/surface (liens, onglet actif)", t.primary, t.surface)
        }
    }

    // `tertiary` = accentAmber. En clair, onTertiary (blanc) n'atteint que 4,49:1 : le rôle tertiary ne
    // porte jamais de texte sur Android (formes, icônes, étincelles). En sombre, il est AA.
    @Test fun tertiaryCarriesNoTextInLight() {
        assertContrast(3.0, "clair", "onTertiary/tertiary (non-texte)", ThemeTokens.Light.onPrimary, ThemeTokens.Light.accentAmber)
        assertContrast(4.5, "sombre", "onTertiary/tertiary", ThemeTokens.Dark.onPrimary, ThemeTokens.Dark.accentAmber)
    }
}

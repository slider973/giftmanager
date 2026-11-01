package ch.jonathanlemaine.giftmanager.ui.auth

import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import ch.jonathanlemaine.giftmanager.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les couleurs du bouton Google sont imposées par Google (hors tokens) : ce test vérifie qu'elles restent
 * lisibles dans l'app — libellé AA sur le fond du bouton, et bouton distinct (≥ 3:1) du fond d'écran.
 */
class GoogleSignInButtonContrastTest {
    private fun assertContrast(min: Double, label: String, fg: Long, bg: Long) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label = %.2f (< $min)".format(ratio), ratio >= min)
    }

    @Test fun labelMeetsAA() {
        assertContrast(4.5, "clair : libellé", GoogleButtonColors.LIGHT_CONTENT, GoogleButtonColors.LIGHT_CONTAINER)
        assertContrast(4.5, "sombre : libellé", GoogleButtonColors.DARK_CONTENT, GoogleButtonColors.DARK_CONTAINER)
    }

    @Test fun buttonOutlineStandsOutFromScreen() {
        assertContrast(3.0, "clair : bordure / background", GoogleButtonColors.LIGHT_BORDER, ThemeTokens.Light.background)
        assertContrast(3.0, "clair : bordure / surface", GoogleButtonColors.LIGHT_BORDER, ThemeTokens.Light.surface)
        assertContrast(3.0, "sombre : bordure / background", GoogleButtonColors.DARK_BORDER, ThemeTokens.Dark.background)
        assertContrast(3.0, "sombre : bordure / surface", GoogleButtonColors.DARK_BORDER, ThemeTokens.Dark.surface)
    }
}

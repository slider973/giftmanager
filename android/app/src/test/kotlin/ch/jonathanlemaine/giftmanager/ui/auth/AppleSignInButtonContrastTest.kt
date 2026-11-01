package ch.jonathanlemaine.giftmanager.ui.auth

import ch.jonathanlemaine.giftmanager.ui.theme.ThemeTokens
import ch.jonathanlemaine.giftmanager.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les couleurs du bouton Apple sont imposées par Apple (hors tokens) : ce test vérifie qu'elles restent
 * lisibles dans l'app — libellé et logo AA sur le fond du bouton, et bouton distinct (≥ 3:1) du fond d'écran.
 */
class AppleSignInButtonContrastTest {
    private fun assertContrast(min: Double, label: String, fg: Long, bg: Long) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label = %.2f (< $min)".format(ratio), ratio >= min)
    }

    @Test fun labelMeetsAA() {
        assertContrast(4.5, "clair : libellé", AppleButtonColors.LIGHT_CONTENT, AppleButtonColors.LIGHT_CONTAINER)
        assertContrast(4.5, "sombre : libellé", AppleButtonColors.DARK_CONTENT, AppleButtonColors.DARK_CONTAINER)
    }

    @Test fun buttonStandsOutFromScreen() {
        assertContrast(3.0, "clair : bouton / background", AppleButtonColors.LIGHT_CONTAINER, ThemeTokens.Light.background)
        assertContrast(3.0, "clair : bouton / surface", AppleButtonColors.LIGHT_CONTAINER, ThemeTokens.Light.surface)
        assertContrast(3.0, "sombre : bouton / background", AppleButtonColors.DARK_CONTAINER, ThemeTokens.Dark.background)
        assertContrast(3.0, "sombre : bouton / surface", AppleButtonColors.DARK_CONTAINER, ThemeTokens.Dark.surface)
    }

    @Test fun whiteButtonOutlineStandsOutFromItsFill() {
        assertContrast(3.0, "sombre : bordure / bouton", AppleButtonColors.DARK_BORDER, AppleButtonColors.DARK_CONTAINER)
    }
}

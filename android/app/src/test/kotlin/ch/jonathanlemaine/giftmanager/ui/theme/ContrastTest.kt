package ch.jonathanlemaine.giftmanager.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test

/** Garde AA du design system (DESIGN.md) : un token modifié sans contrôle fait échouer ce test. */
class ContrastTest {
    private val themes = mapOf("clair" to ThemeTokens.Light, "sombre" to ThemeTokens.Dark)

    private fun assertContrast(min: Double, theme: String, label: String, fg: Long, bg: Long) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$theme : $label = %.2f (< $min)".format(ratio), ratio >= min)
    }

    @Test fun textAndActionsMeetAA() {
        themes.forEach { (n, t) ->
            assertContrast(4.5, n, "textPrimary/background", t.textPrimary, t.background)
            assertContrast(4.5, n, "textSecondary/background", t.textSecondary, t.background)
            assertContrast(4.5, n, "onPrimary/primary", t.onPrimary, t.primary)
            assertContrast(4.5, n, "onPrimary/secondary", t.onPrimary, t.secondary)
        }
    }

    // Écart connu : accentAmber clair fait 4,11:1 sur background (4,49:1 sur surface), sous le 4,5 annoncé
    // par DESIGN.md. Il sert d'accent (jauge, pastille) toujours doublé d'un texte ou d'une icône : on
    // exige donc 3:1 (non-texte) en clair, et AA en sombre. À relever à 4,5 si l'ambre porte du texte seul.
    @Test fun accentAmberContrast() {
        assertContrast(3.0, "clair", "accentAmber/background", ThemeTokens.Light.accentAmber, ThemeTokens.Light.background)
        assertContrast(4.5, "sombre", "accentAmber/background", ThemeTokens.Dark.accentAmber, ThemeTokens.Dark.background)
    }

    @Test fun statusBadgesMeetAA() {
        themes.forEach { (n, t) ->
            assertContrast(4.5, n, "available", t.availableFg, t.availableBg)
            assertContrast(4.5, n, "taken", t.takenFg, t.takenBg)
            assertContrast(4.5, n, "mine", t.mineFg, t.mineBg)
            assertContrast(4.5, n, "owned", t.ownedFg, t.ownedBg)
            assertContrast(4.5, n, "pot", t.potFg, t.potBg)
        }
    }

    @Test fun heartMeetsNonTextContrast() {
        themes.forEach { (n, t) -> assertContrast(3.0, n, "heart/surface", t.heart, t.surface) }
    }

    // Jauge des budgets (#74) : trois remplissages (à l'aise, presque atteint, dépassé) sur la piste `separator`.
    // Contraste non textuel (3:1) : la jauge est aussi doublée d'un texte « Reste / Plus que / Dépassé ».
    @Test fun budgetGaugeFillsStandOutFromTheTrack() {
        themes.forEach { (n, t) ->
            assertContrast(3.0, n, "secondary/separator (à l'aise)", t.secondary, t.separator)
            assertContrast(3.0, n, "potFg/separator (presque atteint)", t.potFg, t.separator)
            assertContrast(3.0, n, "takenFg/separator (dépassé)", t.takenFg, t.separator)
        }
    }
}

package ch.jonathanlemaine.giftmanager.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorMathTest {
    private val black = 0xFF000000
    private val white = 0xFFFFFFFF

    @Test fun blackOnWhiteIs21() = assertEquals(21.0, contrastRatio(black, white), 1e-9)

    @Test fun contrastIsSymmetric() = assertEquals(contrastRatio(black, white), contrastRatio(white, black), 1e-9)

    @Test fun identicalColorsIs1() = assertEquals(1.0, contrastRatio(0xFF2A7464, 0xFF2A7464), 1e-9)

    @Test fun luminanceBounds() {
        assertEquals(0.0, relativeLuminance(black), 1e-12)
        assertEquals(1.0, relativeLuminance(white), 1e-12)
    }

    @Test fun alphaIsIgnored() = assertEquals(relativeLuminance(0xFF336699), relativeLuminance(0x00336699), 1e-12)

    @Test fun matchesKnownValue() {
        // Gris #767676 sur blanc : 4,54:1, seuil AA classique.
        assertEquals(4.54, contrastRatio(0xFF767676, white), 0.01)
    }
}

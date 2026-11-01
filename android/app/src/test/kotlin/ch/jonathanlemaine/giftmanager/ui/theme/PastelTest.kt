package ch.jonathanlemaine.giftmanager.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PastelTest {
    @Test fun orderMatchesIos() =
        assertEquals(listOf("PINK", "MINT", "BLUE", "PEACH", "LAVENDER"), Pastel.entries.map { it.name })

    @Test fun namedAcceptsTokenAndShortNames() {
        assertEquals(Pastel.MINT, Pastel.named("pastelMint"))
        assertEquals(Pastel.MINT, Pastel.named("mint"))
        assertEquals(Pastel.LAVENDER, Pastel.named("  PastelLavender "))
        assertEquals(Pastel.PEACH, Pastel.named("PEACH"))
    }

    @Test fun namedRejectsUnknown() {
        assertNull(Pastel.named(null))
        assertNull(Pastel.named(""))
        assertNull(Pastel.named("  "))
        assertNull(Pastel.named("pastel"))
        assertNull(Pastel.named("red"))
    }

    @Test fun tokenNameRoundTrips() = Pastel.entries.forEach { assertEquals(it, Pastel.named(it.tokenName)) }

    // Valeurs calculées à la main : sum = (sum * 31 + point de code) & 0x7FFFFFFF, puis sum % 5.
    @Test fun forSeedMatchesHandComputedValues() {
        // L=76, é=233, a=97 : 76*31+233 = 2589 ; 2589*31+97 = 80356 ; 80356 % 5 = 1
        assertEquals(Pastel.MINT, Pastel.forSeed("Léa"))
        // T=84, o=111, m=109 : 2715 ; 84274 ; 84274 % 5 = 4
        assertEquals(Pastel.LAVENDER, Pastel.forSeed("Tom"))
        assertEquals(Pastel.PINK, Pastel.forSeed(""))
        assertEquals(Pastel.PINK, Pastel.forSeed("A")) // 65 % 5 = 0
        assertEquals(Pastel.BLUE, Pastel.forSeed("C")) // 67 % 5 = 2
        // Un émoji est un seul point de code (U+1F600 = 128512), pas deux unités UTF-16.
        assertEquals(Pastel.BLUE, Pastel.forSeed("😀"))
    }

    @Test fun forSeedIsStableAndMasksTo31Bits() {
        assertEquals(Pastel.forSeed("Jonathan"), Pastel.forSeed("Jonathan"))
        // "Jonathan" dépasse 2^31 sans masque : calcul indépendant en Long.
        var sum = 0L
        "Jonathan".forEach { sum = (sum * 31 + it.code) and 0x7FFFFFFF }
        assertEquals(Pastel.entries[(sum % 5).toInt()], Pastel.forSeed("Jonathan"))
    }
}

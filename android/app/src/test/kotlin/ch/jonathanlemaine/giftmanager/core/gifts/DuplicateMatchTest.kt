package ch.jonathanlemaine.giftmanager.core.gifts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** #61 — Détection des doublons quand un cadeau a été recréé faute de le retrouver (`DuplicateMatchTests.swift`). */
class DuplicateMatchTest {
    @Test fun titleIsEqualDespiteCaseAndSpaces() {
        assertEquals(DuplicateMatch.normalize("LEGO  Technic"), DuplicateMatch.normalize("lego technic"))
    }

    @Test fun titleIsEqualDespiteAccentsAndPunctuation() {
        assertEquals(DuplicateMatch.normalize("Télécommande, 2 piles !"), DuplicateMatch.normalize("telecommande 2 piles"))
    }

    @Test fun differentTitlesAreNotMixedUp() {
        assertNotEquals(DuplicateMatch.normalize("Vélo rouge"), DuplicateMatch.normalize("Vélo bleu"))
    }

    @Test fun blankTitleMatchesNothing() {
        assertTrue(DuplicateMatch.normalize("   ").isEmpty())
    }

    @Test fun sameProductDespiteTrackingParameters() {
        assertTrue(DuplicateMatch.sameUrl("https://www.amazon.fr/dp/B09QFZ2X7C?tag=aff-21&utm_source=wa", "https://amazon.fr/dp/B09QFZ2X7C"))
    }

    @Test fun sameProductDespiteTrailingSlash() {
        assertTrue(
            DuplicateMatch.sameUrl("https://www.galaxus.ch/fr/s1/product/lego-42141/", "https://www.galaxus.ch/fr/s1/product/lego-42141"),
        )
    }

    @Test fun differentProductsAreNotMixedUp() {
        assertFalse(DuplicateMatch.sameUrl("https://www.amazon.fr/dp/B09QFZ2X7C", "https://www.amazon.fr/dp/B09Y2MYL5C"))
    }

    @Test fun differentStoresAreNotMixedUp() {
        assertFalse(DuplicateMatch.sameUrl("https://www.amazon.fr/dp/B09QFZ2X7C", "https://www.fnac.com/dp/B09QFZ2X7C"))
    }

    @Test fun invalidUrlNeverMatches() {
        assertFalse(DuplicateMatch.sameUrl("pas une url", "pas une url"))
    }

    @Test fun pathCaseIsIgnored() {
        assertTrue(DuplicateMatch.sameUrl("https://amazon.fr/DP/ABC", "https://www.amazon.fr/dp/abc"))
    }
}

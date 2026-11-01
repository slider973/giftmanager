package ch.jonathanlemaine.giftmanager.core.gifts

import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.testChild
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddTarget
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTargetsTest {
    private val lea = QuickAddTarget(testChild(1, name = "Léa"), WishKind.WISH)
    private val zoe = QuickAddTarget(testChild(3, householdN = 2, name = "Zoé"), WishKind.IDEA)

    @Test fun noChildMeansNoList() = assertEquals(ShareResolution.NoList, ShareTargets.resolve(QuickAddUiState()))

    @Test fun oneOfMyChildrenIsAWish() =
        assertEquals(ShareResolution.Single(lea), ShareTargets.resolve(QuickAddUiState(myLists = listOf(lea))))

    @Test fun oneOtherChildIsAnIdea() =
        assertEquals(ShareResolution.Single(zoe), ShareTargets.resolve(QuickAddUiState(otherLists = listOf(zoe))))

    @Test fun severalListsAskTheUser() {
        assertEquals(ShareResolution.Choose, ShareTargets.resolve(QuickAddUiState(myLists = listOf(lea), otherLists = listOf(zoe))))
        assertEquals(ShareResolution.Choose, ShareTargets.resolve(QuickAddUiState(myLists = listOf(lea, lea))))
    }

    @Test fun domainDropsWww() {
        assertEquals("amazon.fr", ShareTargets.domain("https://www.amazon.fr/dp/B0"))
        assertEquals("shop.example", ShareTargets.domain("https://shop.example"))
        assertNull(ShareTargets.domain("pas un lien"))
    }
}

package ch.jonathanlemaine.giftmanager.push

import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeLock
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class PendingNavigationTest {
    private val lock = ChildModeLock()
    private val navigation = PendingNavigation(lock)
    private val gift = NotificationTarget.GiftDetail(UUID.randomUUID())

    @Test fun nothingPendingAtStart() = runTest {
        assertNull(navigation.deliverable().first())
    }

    @Test fun targetIsDeliveredThenConsumedOnlyOnce() = runTest {
        navigation.post(gift)
        assertEquals(gift, navigation.deliverable().first())
        navigation.consume(gift)
        assertNull(navigation.deliverable().first())
    }

    @Test fun newNotificationReplacesTheOldOne() = runTest {
        navigation.post(gift)
        navigation.post(NotificationTarget.MyPurchases)
        assertEquals(NotificationTarget.MyPurchases, navigation.deliverable().first())
    }

    @Test fun consumingAnOutdatedTargetKeepsTheNewOne() = runTest {
        navigation.post(gift)
        navigation.post(NotificationTarget.MyPurchases)
        navigation.consume(gift)
        assertEquals(NotificationTarget.MyPurchases, navigation.deliverable().first())
    }

    @Test fun targetWaitsThroughLoadingAndSignIn() = runTest {
        // Démarrage à froid : la notification arrive avant que la session soit prête.
        navigation.post(gift)
        navigation.onAuthState(AuthState.Loading)
        navigation.onAuthState(AuthState.SignedOut)
        assertEquals(gift, navigation.deliverable().first())
        navigation.onAuthState(AuthState.SignedIn(UUID.randomUUID()))
        assertEquals(gift, navigation.deliverable().first())
    }

    @Test fun signingOutClearsTheTarget() = runTest {
        navigation.onAuthState(AuthState.SignedIn(UUID.randomUUID()))
        navigation.post(gift)
        navigation.onAuthState(AuthState.SignedOut)
        assertNull(navigation.deliverable().first())
    }

    @Test fun targetPostedAfterSigningOutWaitsForTheNextSignIn() = runTest {
        navigation.onAuthState(AuthState.SignedIn(UUID.randomUUID()))
        navigation.onAuthState(AuthState.SignedOut)
        navigation.post(gift)
        navigation.onAuthState(AuthState.SignedOut)
        assertEquals(gift, navigation.deliverable().first())
    }

    @Test fun targetWaitsForTheEndOfChildMode() = runTest {
        lock.activate()
        navigation.post(gift)
        assertNull(navigation.deliverable().first())
        lock.release()
        assertEquals(gift, navigation.deliverable().first())
    }
}

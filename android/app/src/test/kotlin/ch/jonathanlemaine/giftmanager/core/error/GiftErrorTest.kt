package ch.jonathanlemaine.giftmanager.core.error

import ch.jonathanlemaine.giftmanager.core.error.GiftError.Companion.from
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GiftErrorTest {
    private fun server(text: String) = RuntimeException(text)

    @Test fun businessCodes() {
        assertEquals(GiftError.Unavailable, from(server("ITEM_UNAVAILABLE")))
        assertEquals(GiftError.Owned, from(server("ITEM_OWNED")))
        assertEquals(GiftError.InvalidInvite, from(server("INVALID_INVITE_CODE")))
        assertEquals(GiftError.AlreadyInHousehold, from(server("ALREADY_IN_HOUSEHOLD")))
        assertEquals(GiftError.CurrencyMismatch, from(server("CURRENCY_MISMATCH")))
        assertEquals(GiftError.NotAMember, from(server("NOT_A_MEMBER")))
        assertEquals(GiftError.NoHousehold, from(server("NO_HOUSEHOLD")))
        assertEquals(GiftError.NotAuthenticated, from(server("NOT_AUTHENTICATED")))
    }

    @Test fun notFoundVariants() {
        assertEquals(GiftError.NotFound, from(server("ITEM_NOT_FOUND")))
        assertEquals(GiftError.NotFound, from(server("RESERVATION_NOT_FOUND")))
    }

    @Test fun missingColumnOrFunctionMeansOutdatedApp() {
        assertEquals(GiftError.OutdatedApp, from(server("""{"code":"42703","message":"column households.group_id does not exist"}""")))
        assertEquals(GiftError.OutdatedApp, from(server("""{"code":"PGRST202","message":"function not found"}""")))
        assertEquals(GiftError.OutdatedApp, from(server("""{"code":"42883"}""")))
    }

    @Test fun notFoundIsNotMistakenForOutdatedApp() {
        assertEquals(GiftError.NotFound, from(server("ITEM_NOT_FOUND ... does not exist")))
    }

    @Test fun codeIsFoundInTheCauseChain() {
        assertEquals(GiftError.InvalidInvite, from(RuntimeException("échec", server("INVALID_INVITE_CODE"))))
    }

    @Test fun networkFailures() {
        assertEquals(GiftError.Network, from(IOException("timeout")))
        assertEquals(GiftError.Network, from(RuntimeException("x", java.net.UnknownHostException("h"))))
    }

    @Test fun unknownErrorsStayGenericAndNeverLeakDetails() {
        assertEquals(GiftError.Other, from(server("boom secret_detail")))
    }

    @Test fun everyErrorHasAMessage() {
        assertTrue(GiftError.entries.all { it.messageRes != 0 })
        assertEquals(GiftError.entries.size, GiftError.entries.map { it.messageRes }.toSet().size)
    }

    @Test fun cancellationIsNeverSwallowed() = runTest {
        var rethrown = false
        try {
            runCatchingCancellable { throw CancellationException("annulé") }
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
        assertFalse(runCatchingCancellable { throw IOException() }.isSuccess)
        assertEquals(3, runCatchingCancellable { 3 }.getOrThrow())
    }
}

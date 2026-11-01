package ch.jonathanlemaine.giftmanager.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonceTest {
    @Test fun sha256OfAbcMatchesReferenceVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex("abc"))
    }

    @Test fun sha256HasFixedLength() {
        assertEquals(64, sha256Hex("").length)
    }

    @Test fun nonceIsHexOfTwiceTheByteCount() {
        val nonce = randomNonce(32)
        assertEquals(64, nonce.length)
        assertTrue(nonce.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test fun noncesDiffer() {
        assertNotEquals(randomNonce(32), randomNonce(32))
    }
}

package ch.jonathanlemaine.giftmanager.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InviteLinkTest {
    @Test fun pathCodeIsUppercased() {
        assertEquals("ABC123", InviteLink.parse("famillecadeaux://join/abc123"))
        assertEquals("ABC123", InviteLink.parse("famillecadeaux://join/ABC123/"))
    }

    @Test fun queryCode() {
        assertEquals("ABC123", InviteLink.parse("famillecadeaux://join?code=abc123"))
        assertEquals("XYZ", InviteLink.parse("famillecadeaux://join?x=1&code=xyz"))
    }

    @Test fun pathWinsOverQuery() {
        assertEquals("AAA", InviteLink.parse("famillecadeaux://join/aaa?code=bbb"))
    }

    @Test fun otherLinksAreIgnored() {
        assertNull(InviteLink.parse(null))
        assertNull(InviteLink.parse(""))
        assertNull(InviteLink.parse("famillecadeaux://join"))
        assertNull(InviteLink.parse("famillecadeaux://join/"))
        assertNull(InviteLink.parse("famillecadeaux://join?code="))
        assertNull(InviteLink.parse("famillecadeaux://other/ABC123"))
        assertNull(InviteLink.parse("https://join/ABC123"))
        assertNull(InviteLink.parse("pas un lien \u0000"))
    }

    @Test fun messageMatchesIos() {
        assertEquals(
            "Rejoins « Les Lemaine » sur Gift Manager pour coordonner les cadeaux des enfants 🎁\n" +
                "Code d'invitation : ABC123\n" +
                "famillecadeaux://join/ABC123",
            InviteLink.message(group(1, name = "Les Lemaine", code = "ABC123")),
        )
    }

    @Test fun messageLinkRoundTrips() {
        val message = InviteLink.message(group(1, code = "ZZ9988"))
        assertEquals("ZZ9988", InviteLink.parse(message.lines().last()))
    }
}

package ch.jonathanlemaine.giftmanager.core.session

import org.junit.Assert.assertEquals
import org.junit.Test

class DeepLinkRouteTest {
    @Test fun authCallbackWithCode() {
        assertEquals(DeepLinkRoute.AuthCallback, DeepLinkRoute.parse("giftmanager://login-callback?code=abc123"))
    }

    @Test fun authCallbackWithFragmentAndCase() {
        assertEquals(DeepLinkRoute.AuthCallback, DeepLinkRoute.parse("GiftManager://Login-Callback#access_token=x&refresh_token=y"))
        assertEquals(DeepLinkRoute.AuthCallback, DeepLinkRoute.parse("giftmanager://login-callback"))
    }

    @Test fun authCallbackWithErrorStillRoutesToAuth() {
        assertEquals(
            DeepLinkRoute.AuthCallback,
            DeepLinkRoute.parse("giftmanager://login-callback?error=access_denied&error_description=denied"),
        )
    }

    @Test fun inviteIsNotAnAuthCallback() {
        assertEquals(DeepLinkRoute.Invite("ABC123"), DeepLinkRoute.parse("famillecadeaux://join/abc123"))
        assertEquals(DeepLinkRoute.Invite("ABC123"), DeepLinkRoute.parse("famillecadeaux://join?code=abc123"))
    }

    @Test fun otherHostsAndSchemesAreIgnored() {
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse("giftmanager://other"))
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse("famillecadeaux://login-callback?code=abc"))
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse("https://login-callback/?code=abc"))
    }

    @Test fun nullAndGarbageAreIgnored() {
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse(null))
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse("pas un lien"))
        assertEquals(DeepLinkRoute.None, DeepLinkRoute.parse(""))
    }

    @Test fun redirectMatchesTheManifestAndSupabaseConfig() {
        assertEquals("giftmanager://login-callback", DeepLinkRoute.AUTH_REDIRECT)
    }
}

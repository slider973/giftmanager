package ch.jonathanlemaine.giftmanager.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentTest {
    private val send = ShareIntent.ACTION_SEND

    private fun link(text: String?, subject: String? = null, action: String? = send, mime: String? = "text/plain") =
        ShareIntent.extractLink(action, mime, text, subject)

    @Test fun bareUrl() = assertEquals("https://www.amazon.fr/dp/B0", link("https://www.amazon.fr/dp/B0"))

    @Test fun textAroundTheUrl() =
        assertEquals("https://shop.example/velo", link("Regarde ça 👉 https://shop.example/velo trop bien"))

    @Test fun missingSchemeGetsHttps() = assertEquals("https://shop.example/velo", link("shop.example/velo"))

    @Test fun trailingPunctuationIsDropped() = assertEquals("https://shop.example/velo", link("Vu ici : https://shop.example/velo."))

    @Test fun mimeTypeWithCharsetIsAccepted() = assertEquals("https://shop.example", link("https://shop.example", mime = "text/plain; charset=utf-8"))

    @Test fun imageMimeIsRejected() = assertNull(link("https://shop.example", mime = "image/png"))

    @Test fun nullMimeIsRejected() = assertNull(link("https://shop.example", mime = null))

    @Test fun viewActionIsRejected() = assertNull(link("https://shop.example", action = "android.intent.action.VIEW"))

    @Test fun textWithoutLinkIsNull() = assertNull(link("Une super idée de cadeau"))

    @Test fun nullTextIsNull() = assertNull(link(null))

    @Test fun subjectIsTheFallback() = assertEquals("https://shop.example/velo", link("Un vélo", subject = "https://shop.example/velo"))

    @Test fun textWinsOverSubject() = assertEquals("https://a.example", link("https://a.example", subject = "https://b.example"))

    @Test fun dangerousSchemesAreRejected() {
        listOf(
            "javascript:alert(1)",
            "javascript://shop.example/%0aalert(1)",
            "file:///sdcard/secret.txt",
            "ftp://shop.example/velo",
            "content://media/external/images/1",
            "intent://shop.example/#Intent;scheme=https;end",
        ).forEach { assertNull(it, link(it)) }
    }

    @Test fun textShareDetection() {
        assertTrue(ShareIntent.isTextShare(send, "text/plain"))
        assertFalse(ShareIntent.isTextShare(send, "image/png"))
        assertFalse(ShareIntent.isTextShare("android.intent.action.VIEW", "text/plain"))
    }
}

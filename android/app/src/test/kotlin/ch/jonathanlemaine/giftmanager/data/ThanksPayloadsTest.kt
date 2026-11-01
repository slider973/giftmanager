package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.session.uuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Paramètres des RPC de remerciement, et conformité des photos avec la contrainte du serveur. */
class ThanksPayloadsTest {
    private val item = uuid(701)

    @Test fun sendThanksWithoutPhotoOmitsThePhotoParameter() {
        val json = ThanksPayloads.sendThanks(item, "Merci !", null)
        assertEquals("""{"p_item":"$item","p_message":"Merci !"}""", json.toString())
        assertFalse("p_photo_url" in json)
    }

    @Test fun sendThanksWithPhotoSendsItsUrl() {
        val json = ThanksPayloads.sendThanks(item, "Merci !", "https://x.supabase.co/a.jpg")
        assertEquals("""{"p_item":"$item","p_message":"Merci !","p_photo_url":"https://x.supabase.co/a.jpg"}""", json.toString())
    }

    @Test fun revealSendsTheChoice() {
        assertEquals("""{"p_item":"$item","p_reveal":false}""", ThanksPayloads.reveal(item, false).toString())
        assertEquals("""{"p_item":"$item","p_reveal":true}""", ThanksPayloads.reveal(item, true).toString())
    }

    /** L'URL d'une photo produite par l'envoi doit passer la contrainte `thanks_photo_url_storage` du serveur. */
    @Test fun uploadedPhotoUrlSatisfiesTheServerConstraint() {
        val migration = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "supabase/migrations/20261002000900_review_fixes_serveur.sql") }.first { it.isFile }.readText()
        val pattern = Regex("""photo_url ~ '(.+)'""").find(migration.substringAfter("add constraint thanks_photo_url_storage"))!!.groupValues[1]
        val regex = Regex(pattern)
        val path = SupabaseImageUploader.path(uuid(1), uuid(2))
        // Même forme que `bucket.publicUrl(path)` de supabase-kt.
        val url = "https://abcdefgh.supabase.co/storage/v1/object/public/${SupabaseImageUploader.BUCKET}/$path"
        assertTrue(url, regex.matches(url))
        assertFalse(regex.matches("https://abcdefgh.supabase.co/storage/v1/object/public/images/../x.png"))
    }
}

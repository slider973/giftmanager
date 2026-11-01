package ch.jonathanlemaine.giftmanager.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.util.UUID

/** Envoi d'une photo de cadeau dans le stockage (bucket public `images`, dossier de l'utilisateur). */
interface ImageUploader {
    /** Envoie une image JPEG et renvoie son URL publique. */
    suspend fun upload(bytes: ByteArray, userId: UUID): String
}

class SupabaseImageUploader(private val client: SupabaseClient) : ImageUploader {
    override suspend fun upload(bytes: ByteArray, userId: UUID): String {
        val path = path(userId, UUID.randomUUID())
        val bucket = client.storage.from(BUCKET)
        bucket.upload(path, bytes) { contentType = ContentType.Image.JPEG }
        return bucket.publicUrl(path)
    }

    internal companion object {
        const val BUCKET = "images"

        /** `<utilisateur>/<image>.jpg` en minuscules : la politique de stockage compare le premier dossier à `auth.uid()`. */
        fun path(userId: UUID, imageId: UUID): String = "${userId.toString().lowercase()}/${imageId.toString().lowercase()}.jpg"
    }
}

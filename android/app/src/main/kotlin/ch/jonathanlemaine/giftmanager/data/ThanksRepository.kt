package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.ItemDonor
import ch.jonathanlemaine.giftmanager.core.model.ThanksNote
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Remerciements (#42, #75). Le serveur transmet le message aux donateurs sans jamais révéler leur identité aux
 * parents, sauf si le donateur choisit de se faire connaître ([revealMyself]).
 */
interface ThanksRepository {
    /** Envoie un merci aux donateurs de [itemId] ; [photoUrl] est l'URL publique d'une photo déjà envoyée. */
    suspend fun sendThanks(itemId: UUID, message: String, photoUrl: String?)

    /** Remerciements reçus pour les cadeaux que j'ai offerts. */
    suspend fun myThanks(): List<ThanksNote>

    /** Me fait connaître (ou non) des parents pour ce cadeau seulement. */
    suspend fun revealMyself(itemId: UUID, reveal: Boolean)

    /** Donateurs qui se sont fait connaître (parents seulement). */
    suspend fun itemDonors(itemId: UUID): List<ItemDonor>
}

class SupabaseThanksRepository(private val client: SupabaseClient) : ThanksRepository {
    override suspend fun sendThanks(itemId: UUID, message: String, photoUrl: String?) {
        client.postgrest.rpc(Rpc.SEND_THANKS, ThanksPayloads.sendThanks(itemId, message, photoUrl))
    }

    override suspend fun myThanks(): List<ThanksNote> = client.postgrest.rpc(Rpc.MY_THANKS).decodeTolerantList()

    override suspend fun revealMyself(itemId: UUID, reveal: Boolean) {
        client.postgrest.rpc(Rpc.REVEAL_MYSELF, ThanksPayloads.reveal(itemId, reveal))
    }

    override suspend fun itemDonors(itemId: UUID): List<ItemDonor> =
        client.postgrest.rpc(Rpc.ITEM_DONORS, ItemPayloads.itemParams(itemId)).decodeTolerantList()
}

/** Paramètres des RPC de remerciement. */
internal object ThanksPayloads {
    /** `p_photo_url` est omis sans photo : le paramètre SQL vaut `null` par défaut. */
    fun sendThanks(itemId: UUID, message: String, photoUrl: String?): JsonObject = buildJsonObject {
        put(Rpc.P_ITEM, itemId.toString())
        put(Rpc.P_MESSAGE, message)
        photoUrl?.let { put(Rpc.P_PHOTO_URL, it) }
    }

    fun reveal(itemId: UUID, reveal: Boolean): JsonObject = buildJsonObject {
        put(Rpc.P_ITEM, itemId.toString())
        put(Rpc.P_REVEAL, reveal)
    }
}

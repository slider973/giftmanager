package ch.jonathanlemaine.giftmanager.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import java.util.UUID

/**
 * Réservations anonymes (#11). Le serveur applique le secret : un cadeau déjà pris refuse la réservation
 * (`ITEM_UNAVAILABLE`), et rien n'est jamais renvoyé aux parents de l'enfant.
 */
interface ReservationRepository {
    suspend fun reserve(itemId: UUID)

    suspend fun cancel(itemId: UUID)

    suspend fun setPurchased(itemId: UUID, purchased: Boolean)
}

class SupabaseReservationRepository(private val client: SupabaseClient) : ReservationRepository {
    override suspend fun reserve(itemId: UUID) {
        client.postgrest.rpc(Rpc.RESERVE_ITEM, ItemPayloads.itemParams(itemId))
    }

    override suspend fun cancel(itemId: UUID) {
        client.postgrest.rpc(Rpc.CANCEL_RESERVATION, ItemPayloads.itemParams(itemId))
    }

    override suspend fun setPurchased(itemId: UUID, purchased: Boolean) {
        client.postgrest.rpc(Rpc.SET_RESERVATION_PURCHASED, ItemPayloads.purchasedParams(itemId, purchased))
    }
}

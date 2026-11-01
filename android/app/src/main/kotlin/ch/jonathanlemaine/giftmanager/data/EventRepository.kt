package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.ReservationCounts
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Événements (Noël, anniversaires, fêtes de famille) et compteurs d'équilibre entre enfants (#8, #41, #43). */
interface EventRepository {
    /**
     * Événements visibles : Noël (global), anniversaires des enfants visibles et événements des familles de
     * l'utilisateur. La RLS fait le tri (#60), triés par date.
     */
    suspend fun events(): List<GiftEvent>

    /** Création ou modification (seul le type « autre » est accepté par la RLS). */
    suspend fun saveEvent(event: GiftEvent)

    suspend fun deleteEvent(eventId: UUID)

    /** Compteurs anonymes par liste ; jamais renvoyés pour mes propres enfants. `null` : tous les événements. */
    suspend fun reservationCounts(eventId: UUID?): List<ReservationCounts>

    /** Crée l'anniversaire de l'an prochain une fois le précédent passé ; sans effet si tout est à jour. */
    suspend fun ensureBirthdayEvents(groupId: UUID)
}

class SupabaseEventRepository(private val client: SupabaseClient) : EventRepository {
    override suspend fun events(): List<GiftEvent> =
        client.from("events").select(Columns.raw(COLUMNS)) {
            order("event_date", Order.ASCENDING)
        }.decodeTolerantList()

    override suspend fun saveEvent(event: GiftEvent) {
        // `toUpsert()` : group_id / child_id à null explicite, created_by jamais envoyé (posé par la base).
        client.from("events").upsert(event.toUpsert())
    }

    override suspend fun deleteEvent(eventId: UUID) {
        client.from("events").delete { filter { eq("id", eventId.toString()) } }
    }

    override suspend fun reservationCounts(eventId: UUID?): List<ReservationCounts> =
        client.postgrest.rpc(Rpc.CHILDREN_RESERVATION_COUNTS, buildJsonObject { put(Rpc.P_EVENT, eventId?.toString()) })
            .decodeTolerantList()

    override suspend fun ensureBirthdayEvents(groupId: UUID) {
        client.postgrest.rpc(Rpc.ENSURE_BIRTHDAY_EVENTS, buildJsonObject { put(Rpc.P_GROUP, groupId.toString()) })
    }

    internal companion object {
        const val COLUMNS = "id, group_id, kind, title, event_date, child_id, created_by"
    }
}

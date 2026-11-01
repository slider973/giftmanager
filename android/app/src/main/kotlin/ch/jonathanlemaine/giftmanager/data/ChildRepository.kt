package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/** Enfants et listes d'adultes d'un foyer (#7, #37). L'écriture est réservée aux membres du foyer (RLS). */
interface ChildRepository {
    /**
     * Enfants visibles dans cette famille, triés par prénom. Un même enfant peut remonter plusieurs fois s'il
     * est visible via plusieurs partages (#60) : l'appelant déduplique.
     */
    suspend fun children(groupId: UUID): List<Child>

    suspend fun saveChild(child: Child)

    suspend fun deleteChild(childId: UUID)
}

class SupabaseChildRepository(private val client: SupabaseClient) : ChildRepository {
    override suspend fun children(groupId: UUID): List<Child> =
        client.from("children").select(Columns.raw(COLUMNS)) {
            filter { eq("households.household_groups.group_id", groupId.toString()) }
            order("first_name", Order.ASCENDING)
        }.decodeTolerantList()

    override suspend fun saveChild(child: Child) {
        client.from("children").upsert(child.toUpsertJson())
    }

    override suspend fun deleteChild(childId: UUID) {
        client.from("children").delete { filter { eq("id", childId.toString()) } }
    }

    internal companion object {
        // La jointure `!inner` sert au filtre par famille ; sa clé n'est pas lue (voir decodeTolerantList).
        const val COLUMNS =
            "id, household_id, first_name, birthdate, avatar_emoji, avatar_color, avatar_url, is_adult, " +
                "households!inner(household_groups!inner(group_id))"
    }
}

/**
 * Corps d'upsert : encodé avec [GiftJson.write] pour que les optionnels valent `null` explicite (effacer une
 * date ou un avatar), ce que le sérialiseur par défaut de supabase-kt omettrait.
 */
internal fun Child.toUpsertJson(): JsonObject = GiftJson.write.encodeToJsonElement(this).jsonObject

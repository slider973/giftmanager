package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.GroupMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Familles (groupes) de l'utilisateur : création, invitation par code, renommage. */
interface FamilyRepository {
    /** Mes familles, de la plus ancienne à la plus récente. */
    suspend fun myGroups(): List<FamilyGroup>

    /** Crée la famille et le foyer du créateur ; renvoie l'identifiant de la famille. */
    suspend fun createGroup(name: String, householdName: String, country: String): UUID

    /** Rejoint une famille par son code ; renvoie son identifiant. */
    suspend fun joinGroup(code: String): UUID

    suspend fun renameGroup(groupId: UUID, name: String)

    suspend fun groupMembers(groupId: UUID): List<GroupMember>
}

@Serializable
internal data class GroupNamePatch(val name: String)

class SupabaseFamilyRepository(private val client: SupabaseClient) : FamilyRepository {
    override suspend fun myGroups(): List<FamilyGroup> =
        client.from("groups").select { order("created_at", Order.ASCENDING) }.decodeTolerantList()

    override suspend fun createGroup(name: String, householdName: String, country: String): UUID =
        client.postgrest.rpc(
            Rpc.CREATE_GROUP,
            buildJsonObject {
                put(Rpc.P_NAME, name)
                put(Rpc.P_HOUSEHOLD_NAME, householdName)
                put(Rpc.P_COUNTRY, country)
            },
        ).rpcUuid()

    override suspend fun joinGroup(code: String): UUID =
        client.postgrest.rpc(Rpc.JOIN_GROUP, buildJsonObject { put(Rpc.P_CODE, code) }).rpcUuid()

    override suspend fun renameGroup(groupId: UUID, name: String) {
        client.from("groups").update(GroupNamePatch(name)) { filter { eq("id", groupId.toString()) } }
    }

    override suspend fun groupMembers(groupId: UUID): List<GroupMember> =
        client.from("group_members").select { filter { eq("group_id", groupId.toString()) } }.decodeTolerantList()
}

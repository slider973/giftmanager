package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Foyers (un ou deux parents et leurs enfants) et leur partage avec les familles (#60). */
interface HouseholdRepository {
    /** Foyers partagés avec cette famille. */
    suspend fun households(groupId: UUID): List<Household>

    suspend fun householdMembers(groupId: UUID): List<HouseholdMember>

    /** Familles avec lesquelles mon foyer est partagé. */
    suspend fun myHouseholdGroups(): List<HouseholdGroup>

    suspend fun createHousehold(groupId: UUID, name: String, country: String): UUID

    suspend fun joinHousehold(code: String): UUID

    /** Partage mon foyer avec la famille : ses enfants y deviennent visibles. */
    suspend fun shareHousehold(groupId: UUID): UUID

    suspend fun unshareHousehold(groupId: UUID)
}

class SupabaseHouseholdRepository(private val client: SupabaseClient) : HouseholdRepository {
    override suspend fun households(groupId: UUID): List<Household> =
        client.from("households").select(Columns.raw(HOUSEHOLDS_COLUMNS)) {
            filter { eq("household_groups.group_id", groupId.toString()) }
            order("created_at", Order.ASCENDING)
        }.decodeTolerantList()

    override suspend fun householdMembers(groupId: UUID): List<HouseholdMember> =
        client.from("household_members").select(Columns.raw(MEMBERS_COLUMNS)) {
            filter { eq("households.household_groups.group_id", groupId.toString()) }
        }.decodeTolerantList()

    override suspend fun myHouseholdGroups(): List<HouseholdGroup> =
        client.from("household_groups").select(Columns.raw("household_id, group_id")).decodeTolerantList()

    override suspend fun createHousehold(groupId: UUID, name: String, country: String): UUID =
        client.postgrest.rpc(
            Rpc.CREATE_HOUSEHOLD,
            buildJsonObject {
                put(Rpc.P_GROUP, groupId.toString())
                put(Rpc.P_NAME, name)
                put(Rpc.P_COUNTRY, country)
            },
        ).rpcUuid()

    override suspend fun joinHousehold(code: String): UUID =
        client.postgrest.rpc(Rpc.JOIN_HOUSEHOLD, buildJsonObject { put(Rpc.P_CODE, code) }).rpcUuid()

    override suspend fun shareHousehold(groupId: UUID): UUID =
        client.postgrest.rpc(Rpc.SHARE_HOUSEHOLD, buildJsonObject { put(Rpc.P_GROUP, groupId.toString()) }).rpcUuid()

    override suspend fun unshareHousehold(groupId: UUID) {
        client.postgrest.rpc(Rpc.UNSHARE_HOUSEHOLD, buildJsonObject { put(Rpc.P_GROUP, groupId.toString()) })
    }

    internal companion object {
        // Les jointures `!inner` filtrent sur la famille ; leurs clés ne sont pas lues (voir decodeTolerantList).
        const val HOUSEHOLDS_COLUMNS = "id, name, country, invite_code, household_groups!inner(group_id)"
        const val MEMBERS_COLUMNS = "household_id, user_id, households!inner(household_groups!inner(group_id))"
    }
}

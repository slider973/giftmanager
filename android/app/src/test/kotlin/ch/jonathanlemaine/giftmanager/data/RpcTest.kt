package ch.jonathanlemaine.giftmanager.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

/** Noms de RPC et de paramètres : doivent correspondre à `supabase/migrations` et à `GiftRepository.swift`. */
class RpcTest {
    @Test fun functionNames() {
        assertEquals("create_group", Rpc.CREATE_GROUP)
        assertEquals("join_group", Rpc.JOIN_GROUP)
        assertEquals("create_household", Rpc.CREATE_HOUSEHOLD)
        assertEquals("join_household", Rpc.JOIN_HOUSEHOLD)
        assertEquals("share_household_with_group", Rpc.SHARE_HOUSEHOLD)
        assertEquals("unshare_household_from_group", Rpc.UNSHARE_HOUSEHOLD)
        assertEquals("delete_account", Rpc.DELETE_ACCOUNT)
    }

    @Test fun parameterNames() {
        assertEquals("p_name", Rpc.P_NAME)
        assertEquals("p_household_name", Rpc.P_HOUSEHOLD_NAME)
        assertEquals("p_country", Rpc.P_COUNTRY)
        assertEquals("p_code", Rpc.P_CODE)
        assertEquals("p_group", Rpc.P_GROUP)
    }

    @Test fun everyNameExistsInTheMigrations() {
        val sql = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "supabase/migrations") }.first { it.isDirectory }
            .listFiles { f -> f.extension == "sql" }!!.joinToString("\n") { it.readText() }
        listOf(
            Rpc.CREATE_GROUP, Rpc.JOIN_GROUP, Rpc.CREATE_HOUSEHOLD, Rpc.JOIN_HOUSEHOLD,
            Rpc.SHARE_HOUSEHOLD, Rpc.UNSHARE_HOUSEHOLD, Rpc.DELETE_ACCOUNT,
        ).forEach { assert("function public.$it(" in sql) { it }; }
        listOf(Rpc.P_NAME, Rpc.P_HOUSEHOLD_NAME, Rpc.P_COUNTRY, Rpc.P_CODE, Rpc.P_GROUP).forEach { assert(it in sql) { it } }
    }

    @Test fun uuidReturnedByRpcIsAJsonString() {
        val id = UUID.fromString("3f2a1b4c-0000-4000-8000-123456789abc")
        assertEquals(id, parseRpcUuid("\"3f2a1b4c-0000-4000-8000-123456789abc\""))
    }

    @Test fun joinedSelectColumnsMatchIos() {
        assertEquals("id, name, country, invite_code, household_groups!inner(group_id)", SupabaseHouseholdRepository.HOUSEHOLDS_COLUMNS)
        assertEquals("household_id, user_id, households!inner(household_groups!inner(group_id))", SupabaseHouseholdRepository.MEMBERS_COLUMNS)
    }
}

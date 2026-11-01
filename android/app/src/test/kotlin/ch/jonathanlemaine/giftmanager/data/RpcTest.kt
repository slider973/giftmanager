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
        assertEquals("children_reservation_counts", Rpc.CHILDREN_RESERVATION_COUNTS)
        assertEquals("ensure_birthday_events", Rpc.ENSURE_BIRTHDAY_EVENTS)
        assertEquals("child_items", Rpc.CHILD_ITEMS)
        assertEquals("reserve_item", Rpc.RESERVE_ITEM)
        assertEquals("cancel_reservation", Rpc.CANCEL_RESERVATION)
        assertEquals("set_reservation_purchased", Rpc.SET_RESERVATION_PURCHASED)
        assertEquals("join_pot", Rpc.JOIN_POT)
        assertEquals("leave_pot", Rpc.LEAVE_POT)
        assertEquals("pot_participants", Rpc.POT_PARTICIPANTS)
        assertEquals("review_idea", Rpc.REVIEW_IDEA)
        assertEquals("pending_ideas", Rpc.PENDING_IDEAS)
        assertEquals("idea_audience", Rpc.IDEA_AUDIENCE)
        assertEquals("my_reservations", Rpc.MY_RESERVATIONS)
        assertEquals("my_contributions", Rpc.MY_CONTRIBUTIONS)
        assertEquals("my_budgets", Rpc.MY_BUDGETS)
        assertEquals("set_budget", Rpc.SET_BUDGET)
        assertEquals("send_thanks", Rpc.SEND_THANKS)
        assertEquals("my_thanks", Rpc.MY_THANKS)
        assertEquals("reveal_myself", Rpc.REVEAL_MYSELF)
        assertEquals("item_donors", Rpc.ITEM_DONORS)
        assertEquals("my_price_history", Rpc.MY_PRICE_HISTORY)
    }

    @Test fun parameterNames() {
        assertEquals("p_name", Rpc.P_NAME)
        assertEquals("p_household_name", Rpc.P_HOUSEHOLD_NAME)
        assertEquals("p_country", Rpc.P_COUNTRY)
        assertEquals("p_code", Rpc.P_CODE)
        assertEquals("p_group", Rpc.P_GROUP)
        assertEquals("p_event", Rpc.P_EVENT)
        assertEquals("p_child", Rpc.P_CHILD)
        assertEquals("p_kind", Rpc.P_KIND)
        assertEquals("p_item", Rpc.P_ITEM)
        assertEquals("p_purchased", Rpc.P_PURCHASED)
        assertEquals("p_amount", Rpc.P_AMOUNT)
        assertEquals("p_currency", Rpc.P_CURRENCY)
        assertEquals("p_decision", Rpc.P_DECISION)
        assertEquals("p_note", Rpc.P_NOTE)
        assertEquals("p_message", Rpc.P_MESSAGE)
        assertEquals("p_photo_url", Rpc.P_PHOTO_URL)
        assertEquals("p_reveal", Rpc.P_REVEAL)
    }

    @Test fun everyNameExistsInTheMigrations() {
        val sql = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "supabase/migrations") }.first { it.isDirectory }
            .listFiles { f -> f.extension == "sql" }!!.joinToString("\n") { it.readText() }
        listOf(
            Rpc.CREATE_GROUP, Rpc.JOIN_GROUP, Rpc.CREATE_HOUSEHOLD, Rpc.JOIN_HOUSEHOLD,
            Rpc.SHARE_HOUSEHOLD, Rpc.UNSHARE_HOUSEHOLD, Rpc.DELETE_ACCOUNT, Rpc.CHILDREN_RESERVATION_COUNTS,
            Rpc.ENSURE_BIRTHDAY_EVENTS, Rpc.CHILD_ITEMS, Rpc.RESERVE_ITEM, Rpc.CANCEL_RESERVATION,
            Rpc.SET_RESERVATION_PURCHASED, Rpc.JOIN_POT, Rpc.LEAVE_POT, Rpc.POT_PARTICIPANTS, Rpc.REVIEW_IDEA,
            Rpc.PENDING_IDEAS, Rpc.IDEA_AUDIENCE, Rpc.MY_RESERVATIONS, Rpc.MY_CONTRIBUTIONS, Rpc.MY_BUDGETS, Rpc.SET_BUDGET,
            Rpc.SEND_THANKS, Rpc.MY_THANKS, Rpc.REVEAL_MYSELF, Rpc.ITEM_DONORS,
            Rpc.MY_PRICE_HISTORY,
        ).forEach { assert("function public.$it(" in sql) { it }; }
        listOf(
            Rpc.P_NAME, Rpc.P_HOUSEHOLD_NAME, Rpc.P_COUNTRY, Rpc.P_CODE, Rpc.P_GROUP, Rpc.P_EVENT, Rpc.P_CHILD, Rpc.P_KIND,
            Rpc.P_ITEM, Rpc.P_PURCHASED, Rpc.P_AMOUNT, Rpc.P_CURRENCY, Rpc.P_DECISION, Rpc.P_NOTE,
            Rpc.P_MESSAGE, Rpc.P_PHOTO_URL, Rpc.P_REVEAL,
        ).forEach { assert(it in sql) { it } }
    }

    @Test fun parametersBelongToTheirFunction() {
        // Un nom de paramètre présent ailleurs dans le schéma ne prouve rien : on vérifie la signature de chaque RPC.
        val sql = generateSequence(java.io.File("").absoluteFile) { it.parentFile }
            .map { java.io.File(it, "supabase/migrations") }.first { it.isDirectory }
            .listFiles { f -> f.extension == "sql" }!!.sortedBy { it.name }.joinToString("\n") { it.readText() }
        fun signature(fn: String): String =
            Regex("""create (?:or replace )?function public\.$fn\(([^)]*)\)""", RegexOption.IGNORE_CASE).findAll(sql).last().groupValues[1]
        assertParams(signature(Rpc.CHILD_ITEMS), Rpc.P_CHILD, Rpc.P_EVENT, Rpc.P_KIND)
        assertParams(signature(Rpc.RESERVE_ITEM), Rpc.P_ITEM)
        assertParams(signature(Rpc.CANCEL_RESERVATION), Rpc.P_ITEM)
        assertParams(signature(Rpc.SET_RESERVATION_PURCHASED), Rpc.P_ITEM, Rpc.P_PURCHASED)
        assertParams(signature(Rpc.JOIN_POT), Rpc.P_ITEM, Rpc.P_AMOUNT, Rpc.P_CURRENCY)
        assertParams(signature(Rpc.LEAVE_POT), Rpc.P_ITEM)
        assertParams(signature(Rpc.POT_PARTICIPANTS), Rpc.P_ITEM)
        assertParams(signature(Rpc.REVIEW_IDEA), Rpc.P_ITEM, Rpc.P_DECISION, Rpc.P_NOTE)
        assertParams(signature(Rpc.PENDING_IDEAS))
        assertParams(signature(Rpc.IDEA_AUDIENCE), Rpc.P_CHILD)
        assertParams(signature(Rpc.MY_RESERVATIONS))
        assertParams(signature(Rpc.MY_CONTRIBUTIONS))
        assertParams(signature(Rpc.MY_BUDGETS))
        assertParams(signature(Rpc.SET_BUDGET), Rpc.P_CHILD, Rpc.P_EVENT, Rpc.P_AMOUNT, Rpc.P_CURRENCY)
        assertParams(signature(Rpc.SEND_THANKS), Rpc.P_ITEM, Rpc.P_MESSAGE, Rpc.P_PHOTO_URL)
        assertParams(signature(Rpc.MY_THANKS))
        assertParams(signature(Rpc.REVEAL_MYSELF), Rpc.P_ITEM, Rpc.P_REVEAL)
        assertParams(signature(Rpc.ITEM_DONORS), Rpc.P_ITEM)
        assertParams(signature(Rpc.MY_PRICE_HISTORY), Rpc.P_ITEM)
    }

    private fun assertParams(signature: String, vararg expected: String) {
        val names = signature.split(",").map { it.trim().substringBefore(" ") }.filter { it.isNotEmpty() }
        assertEquals(expected.toList(), names)
    }

    @Test fun integerReturnedByRpcIsABareJsonNumber() {
        assertEquals(3, parseRpcInt("3"))
        assertEquals(0, parseRpcInt(" 0\n"))
    }

    @Test fun uuidReturnedByRpcIsAJsonString() {
        val id = UUID.fromString("3f2a1b4c-0000-4000-8000-123456789abc")
        assertEquals(id, parseRpcUuid("\"3f2a1b4c-0000-4000-8000-123456789abc\""))
    }

    @Test fun joinedSelectColumnsMatchIos() {
        assertEquals("id, name, country, invite_code, household_groups!inner(group_id)", SupabaseHouseholdRepository.HOUSEHOLDS_COLUMNS)
        assertEquals("household_id, user_id, households!inner(household_groups!inner(group_id))", SupabaseHouseholdRepository.MEMBERS_COLUMNS)
    }

    @Test fun childAndEventSelectColumnsMatchIos() {
        assertEquals(
            "id, household_id, first_name, birthdate, avatar_emoji, avatar_color, avatar_url, is_adult, " +
                "households!inner(household_groups!inner(group_id))",
            SupabaseChildRepository.COLUMNS,
        )
        assertEquals("id, group_id, kind, title, event_date, child_id, created_by", SupabaseEventRepository.COLUMNS)
    }
}

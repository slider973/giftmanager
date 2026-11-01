package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

/** Les jointures `!inner` ajoutent des clés que les modèles ne connaissent pas : elles doivent être ignorées. */
class JoinedRowsDecodingTest {
    @Test fun householdWithJoinKey() {
        val json = """[{"id":"00000000-0000-0000-0000-0000000000c9","name":"Foyer","country":"CH","invite_code":"AB12CD34",
            "household_groups":[{"group_id":"00000000-0000-0000-0000-000000000065"}]}]"""
        val rows = GiftJson.read.decodeFromString<List<Household>>(json)
        assertEquals("Foyer", rows.single().name)
        assertEquals("AB12CD34", rows.single().inviteCode)
    }

    @Test fun householdMemberWithNestedJoin() {
        val json = """[{"household_id":"00000000-0000-0000-0000-0000000000c9","user_id":"00000000-0000-0000-0000-000000000001",
            "households":{"household_groups":[{"group_id":"00000000-0000-0000-0000-000000000065"}]}}]"""
        assertEquals(1, GiftJson.read.decodeFromString<List<HouseholdMember>>(json).size)
    }
}

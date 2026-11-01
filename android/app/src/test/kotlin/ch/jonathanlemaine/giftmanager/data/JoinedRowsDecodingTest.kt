package ch.jonathanlemaine.giftmanager.data

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftJson
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test fun childWithNestedHouseholdJoin() {
        val json = """[{"id":"00000000-0000-0000-0000-000000000191","household_id":"00000000-0000-0000-0000-0000000000c9",
            "first_name":"Léo","birthdate":"2019-05-04","avatar_emoji":"🚀","avatar_color":"pastelBlue","avatar_url":null,"is_adult":false,
            "households":{"household_groups":[{"group_id":"00000000-0000-0000-0000-000000000065"}]}}]"""
        val child = GiftJson.read.decodeFromString<List<Child>>(json).single()
        assertEquals("Léo", child.firstName)
        assertEquals("2019-05-04", child.birthdate.toString())
        assertEquals("pastelBlue", child.avatarColor)
        assertEquals(null, child.avatarUrl)
    }

    @Test fun childUpsertBodyKeepsExplicitNullsAndDefaults() {
        val child = Child(java.util.UUID(0, 1), java.util.UUID(0, 2), "Mamie", birthdate = null, isAdult = true)
        val body = child.toUpsertJson()
        assertEquals(JsonNull, body["birthdate"])
        assertEquals(JsonNull, body["avatar_emoji"])
        assertEquals(JsonNull, body["avatar_color"])
        assertEquals(JsonNull, body["avatar_url"])
        assertEquals("true", body["is_adult"].toString())
        // Même clés que la table `children` : rien de plus, rien de moins.
        assertEquals(
            setOf("id", "household_id", "first_name", "birthdate", "avatar_emoji", "avatar_color", "avatar_url", "is_adult"),
            body.keys,
        )
        assertTrue(Child(java.util.UUID(0, 1), java.util.UUID(0, 2), "Léo").toUpsertJson()["is_adult"].toString() == "false")
    }
}

package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import ch.jonathanlemaine.giftmanager.data.auth.FakeAuthRepository
import kotlinx.coroutines.CoroutineScope

/** Une session câblée sur des fakes, avec un foyer et une famille « prêts » par défaut. */
class SessionFixture(scope: CoroutineScope, auth: AuthState = AuthState.SignedOut) {
    val authRepo = FakeAuthRepository(auth)
    val profiles = FakeProfileRepository()
    val families = FakeFamilyRepository()
    val households = FakeHouseholdRepository()
    val store = FakeCurrentGroupStore()
    val childRepo = FakeChildRepository()
    val eventRepo = FakeEventRepository()
    val birthdayCalls: Int get() = eventRepo.ensureCalls

    init {
        families.onJoin = { groupId ->
            val mine = households.memberships.firstOrNull { it.userId == uuid(1) }?.householdId
            if (mine != null) households.shares += HouseholdGroup(mine, groupId)
        }
    }
    val session = AppSession(authRepo, profiles, families, households, childRepo, eventRepo, store, scope)

    /** Utilisateur 1 dans les familles [groupNumbers], avec son foyer partagé dans chacune. */
    fun seedReady(vararg groupNumbers: Int = intArrayOf(1)) {
        val h = household(1)
        households.all += h
        households.memberships += HouseholdMember(h.id, uuid(1))
        for (n in groupNumbers) {
            val g = group(n)
            families.groups += g
            families.members[g.id] = mutableListOf(uuid(1))
            households.shares += HouseholdGroup(h.id, g.id)
        }
    }
}

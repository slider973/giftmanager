package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.GroupMember
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.core.model.Profile
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.ReservationCounts
import ch.jonathanlemaine.giftmanager.data.ChildRepository
import ch.jonathanlemaine.giftmanager.data.EventRepository
import ch.jonathanlemaine.giftmanager.data.FamilyRepository
import ch.jonathanlemaine.giftmanager.data.HouseholdRepository
import ch.jonathanlemaine.giftmanager.data.ProfileRepository
import ch.jonathanlemaine.giftmanager.data.prefs.CurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.LocalPrefs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

// Fakes à la main des dépôts : de simples listes en mémoire, avec des pannes injectables.

fun uuid(n: Int): UUID = UUID(0, n.toLong())

fun profile(
    id: UUID = uuid(1),
    name: String = "Jonathan",
    onboarded: Boolean = true,
    country: String = "CH",
    currency: String = "CHF",
) = Profile(id, name, country, currency, onboarded)

fun group(n: Int, name: String = "Famille $n", code: String = "CODE0$n") = FamilyGroup(uuid(100 + n), name, code)

fun household(n: Int, name: String = "Foyer $n") = Household(uuid(200 + n), name, "CH", "HH00000$n")

fun testChild(
    n: Int,
    householdN: Int = 1,
    name: String = "Enfant $n",
    adult: Boolean = false,
    birthdate: String? = "2018-03-12",
) = Child(uuid(400 + n), uuid(200 + householdN), name, birthdate?.let(DayDate::parse), isAdult = adult)

fun testEvent(
    n: Int,
    date: String,
    kind: GiftEventKind = GiftEventKind.OTHER,
    childId: UUID? = null,
    createdBy: UUID? = null,
    groupId: UUID? = uuid(101),
) = GiftEvent(uuid(500 + n), if (kind == GiftEventKind.OTHER) groupId else null, kind, "Événement $n", DayDate.parse(date), childId, createdBy)

class FakeProfileRepository(var me: Profile? = profile()) : ProfileRepository {
    val others = mutableListOf<Profile>()
    var failure: Throwable? = null
    var myProfileCalls = 0
    var deleted = false
    var updates = mutableListOf<Profile>()

    override suspend fun myProfile(userId: UUID): Profile {
        myProfileCalls++
        failure?.let { throw it }
        return me ?: error("pas de profil")
    }

    override suspend fun updateProfile(userId: UUID, displayName: String, country: String, currency: String, onboarded: Boolean) {
        failure?.let { throw it }
        me = me!!.copy(displayName = displayName, country = country, currency = currency, onboarded = onboarded)
        updates += me!!
    }

    override suspend fun setBirthdayReminders(userId: UUID, enabled: Boolean) {
        failure?.let { throw it }
        me = me!!.copy(notifyBirthdayReminders = enabled)
    }

    override suspend fun setPurchaseReminders(userId: UUID, enabled: Boolean) {
        failure?.let { throw it }
        me = me!!.copy(notifyPurchaseReminders = enabled)
    }

    override suspend fun profiles(ids: List<UUID>): List<Profile> =
        if (ids.isEmpty()) emptyList() else (listOfNotNull(me) + others).filter { it.id in ids }

    override suspend fun deleteAccount() {
        failure?.let { throw it }
        deleted = true
    }
}

class FakeFamilyRepository : FamilyRepository {
    val groups = mutableListOf<FamilyGroup>()
    val members = mutableMapOf<UUID, MutableList<UUID>>()
    var failure: Throwable? = null
    var me: UUID = uuid(1)
    var myGroupsCalls = 0
    var created: Triple<String, String, String>? = null

    /** Effet de bord serveur de `join_group` : le foyer de l'utilisateur est partagé avec la famille rejointe. */
    var onJoin: (UUID) -> Unit = {}
    var renamed: Pair<UUID, String>? = null

    /** Code d'invitation → famille, pour [joinGroup]. */
    val joinable = mutableMapOf<String, FamilyGroup>()

    override suspend fun myGroups(): List<FamilyGroup> {
        myGroupsCalls++
        failure?.let { throw it }
        return groups.toList()
    }

    override suspend fun createGroup(name: String, householdName: String, country: String): UUID {
        failure?.let { throw it }
        created = Triple(name, householdName, country)
        val g = FamilyGroup(uuid(100 + groups.size + 50), name, "NEW000")
        groups += g
        members[g.id] = mutableListOf(me)
        return g.id
    }

    override suspend fun joinGroup(code: String): UUID {
        failure?.let { throw it }
        val g = joinable[code] ?: error("INVALID_INVITE_CODE")
        if (groups.none { it.id == g.id }) groups += g
        members.getOrPut(g.id) { mutableListOf() }.let { if (me !in it) it += me }
        onJoin(g.id)
        return g.id
    }

    override suspend fun leaveGroup(groupId: UUID, userId: UUID) {
        failure?.let { throw it }
        members[groupId]?.remove(userId)
        if (userId == me) groups.removeAll { it.id == groupId }
    }

    override suspend fun renameGroup(groupId: UUID, name: String) {
        failure?.let { throw it }
        renamed = groupId to name
        val i = groups.indexOfFirst { it.id == groupId }
        groups[i] = groups[i].copy(name = name)
    }

    override suspend fun groupMembers(groupId: UUID): List<GroupMember> =
        members[groupId].orEmpty().map { GroupMember(groupId, it) }
}

class FakeHouseholdRepository : HouseholdRepository {
    /** Foyers existants, avec leur(s) parent(s) et les familles où ils sont partagés. */
    val all = mutableListOf<Household>()
    val memberships = mutableListOf<HouseholdMember>()
    val shares = mutableListOf<HouseholdGroup>()
    var failure: Throwable? = null
    var groupsFailure: Throwable? = null
    var me: UUID = uuid(1)
    var loads = 0
    val calls = mutableListOf<String>()
    val joinable = mutableMapOf<String, Household>()

    override suspend fun households(groupId: UUID): List<Household> {
        loads++
        failure?.let { throw it }
        val ids = shares.filter { it.groupId == groupId }.map { it.householdId }.toSet()
        return all.filter { it.id in ids }
    }

    override suspend fun householdMembers(groupId: UUID): List<HouseholdMember> {
        val ids = shares.filter { it.groupId == groupId }.map { it.householdId }.toSet()
        return memberships.filter { it.householdId in ids }
    }

    override suspend fun ownHousehold(userId: UUID): Household? {
        val membership = memberships.firstOrNull { it.userId == userId } ?: return null
        return all.firstOrNull { it.id == membership.householdId }
    }

    override suspend fun myHouseholdGroups(): List<HouseholdGroup> {
        groupsFailure?.let { throw it }
        val mine = memberships.filter { it.userId == me }.map { it.householdId }.toSet()
        return shares.filter { it.householdId in mine }
    }

    override suspend fun createHousehold(groupId: UUID, name: String, country: String): UUID {
        calls += "create:$name"
        failure?.let { throw it }
        val h = Household(uuid(300 + all.size), name, country, "NEWHH000")
        all += h
        memberships += HouseholdMember(h.id, me)
        shares += HouseholdGroup(h.id, groupId)
        return h.id
    }

    override suspend fun joinHousehold(code: String): UUID {
        calls += "join:$code"
        failure?.let { throw it }
        val h = joinable[code] ?: error("INVALID_INVITE_CODE")
        memberships += HouseholdMember(h.id, me)
        return h.id
    }

    override suspend fun shareHousehold(groupId: UUID): UUID {
        calls += "share"
        failure?.let { throw it }
        val h = memberships.first { it.userId == me }.householdId
        shares += HouseholdGroup(h, groupId)
        return h
    }

    override suspend fun unshareHousehold(groupId: UUID) {
        calls += "unshare:$groupId"
        failure?.let { throw it }
        shares.removeAll { it.groupId == groupId }
    }
}

class FakeChildRepository : ChildRepository {
    /** Enfants renvoyés tels quels (doublons possibles, comme avec plusieurs partages). */
    val all = mutableListOf<Child>()
    val saved = mutableListOf<Child>()
    val deleted = mutableListOf<UUID>()
    var failure: Throwable? = null
    var loads = 0

    override suspend fun children(groupId: UUID): List<Child> {
        loads++
        failure?.let { throw it }
        return all.toList()
    }

    override suspend fun saveChild(child: Child) {
        failure?.let { throw it }
        saved += child
        all.removeAll { it.id == child.id }
        all += child
    }

    override suspend fun deleteChild(childId: UUID) {
        failure?.let { throw it }
        deleted += childId
        all.removeAll { it.id == childId }
    }
}

class FakeEventRepository : EventRepository {
    val all = mutableListOf<GiftEvent>()
    val saved = mutableListOf<GiftEvent>()
    val deleted = mutableListOf<UUID>()
    var failure: Throwable? = null
    var countsFailure: Throwable? = null
    var counts = listOf<ReservationCounts>()
    val countsCalls = mutableListOf<UUID?>()
    var ensureCalls = 0
    var ensureFailure: Throwable? = null

    override suspend fun events(): List<GiftEvent> {
        failure?.let { throw it }
        return all.toList()
    }

    override suspend fun saveEvent(event: GiftEvent) {
        failure?.let { throw it }
        saved += event
        all.removeAll { it.id == event.id }
        all += event
    }

    override suspend fun deleteEvent(eventId: UUID) {
        failure?.let { throw it }
        deleted += eventId
        all.removeAll { it.id == eventId }
    }

    override suspend fun reservationCounts(eventId: UUID?): List<ReservationCounts> {
        countsCalls += eventId
        countsFailure?.let { throw it }
        return counts
    }

    override suspend fun ensureBirthdayEvents(groupId: UUID) {
        ensureCalls++
        ensureFailure?.let { throw it }
    }
}

class FakeCurrentGroupStore(var saved: UUID? = null) : CurrentGroupStore {
    override suspend fun get(): UUID? = saved

    override suspend fun set(groupId: UUID) {
        saved = groupId
    }
}

class FakeLocalPrefs(initial: Boolean = true) : LocalPrefs {
    private val flow = MutableStateFlow(initial)
    override val notifyFamilyNews: Flow<Boolean> = flow

    override suspend fun setNotifyFamilyNews(enabled: Boolean) {
        flow.value = enabled
    }
}

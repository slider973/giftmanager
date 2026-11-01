package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.core.model.Profile
import java.util.UUID

/** Étape de la vie de l'app qui décide de l'écran racine (iOS : `AppState.Phase`). */
enum class SessionPhase { Launching, SignedOut, NeedsProfile, NeedsGroup, Ready }

/**
 * Données de référence de l'utilisateur connecté : profil, familles et foyers.
 * #72 y ajoutera les enfants et les événements ; les dérivés ci-dessous n'en dépendent pas.
 */
data class SessionState(
    val phase: SessionPhase = SessionPhase.Launching,
    val userId: UUID? = null,
    val profile: Profile? = null,
    val groups: List<FamilyGroup> = emptyList(),
    val currentGroup: FamilyGroup? = null,
    val households: List<Household> = emptyList(),
    val householdMembers: List<HouseholdMember> = emptyList(),
    /** Familles avec lesquelles mon foyer est partagé (#60). */
    val householdGroups: List<HouseholdGroup> = emptyList(),
    /** Membres de la famille courante. */
    val members: List<Profile> = emptyList(),
    /** Le chargement initial a échoué alors qu'une session existe : [phase] reste `SignedOut`, on peut réessayer. */
    val loadFailed: Boolean = false,
) {
    val myHousehold: Household?
        get() {
            val me = userId ?: return null
            val membership = householdMembers.firstOrNull { it.userId == me } ?: return null
            return households.firstOrNull { it.id == membership.householdId }
        }

    /** Familles où mon foyer est visible. */
    val familiesSharingMyHousehold: List<FamilyGroup>
        get() {
            val householdId = myHousehold?.id ?: return emptyList()
            val ids = householdGroups.filter { it.householdId == householdId }.map { it.groupId }.toSet()
            return groups.filter { it.id in ids }
        }

    /** Mon foyer existe mais n'est pas encore partagé avec la famille affichée. */
    val canShareHouseholdWithCurrentGroup: Boolean
        get() {
            val householdId = myHousehold?.id ?: return false
            val group = currentGroup ?: return false
            return householdGroups.none { it.householdId == householdId && it.groupId == group.id }
        }

    /** Les parents d'un foyer, dans l'ordre des membres de la famille. */
    fun parents(household: Household): List<Profile> {
        val ids = householdMembers.filter { it.householdId == household.id }.map { it.userId }.toSet()
        return members.filter { it.id in ids }
    }
}

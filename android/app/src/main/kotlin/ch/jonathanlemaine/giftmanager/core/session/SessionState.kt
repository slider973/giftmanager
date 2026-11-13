package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.Household
import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdMember
import ch.jonathanlemaine.giftmanager.core.model.Profile
import java.util.UUID

/** Étape de la vie de l'app qui décide de l'écran racine (iOS : `AppState.Phase`). */
enum class SessionPhase { Launching, SignedOut, NeedsProfile, NeedsGroup, Ready }

/**
 * Données de référence de l'utilisateur connecté : profil, familles, foyers, enfants et événements.
 * Les lectures dérivées des enfants et des événements sont dans `SessionSelectors.kt`.
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
    /**
     * Mon foyer, qu'il soit partagé ou non avec la famille courante (#119). [myHousehold] ne le voit que
     * dans une famille où il est partagé : sans ceci, l'écran de foyer proposait d'en créer un second.
     */
    val ownHousehold: Household? = null,
    /** Membres de la famille courante. */
    val members: List<Profile> = emptyList(),
    /** Enfants et listes d'adultes visibles dans la famille courante, sans doublon. */
    val children: List<Child> = emptyList(),
    /** Événements visibles (Noël, anniversaires, fêtes de la famille), triés par date. */
    val events: List<GiftEvent> = emptyList(),
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
            val householdId = (ownHousehold ?: myHousehold)?.id ?: return false
            val group = currentGroup ?: return false
            return householdGroups.none { it.householdId == householdId && it.groupId == group.id }
        }

    /** Les parents d'un foyer, dans l'ordre des membres de la famille. */
    fun parents(household: Household): List<Profile> {
        val ids = householdMembers.filter { it.householdId == household.id }.map { it.userId }.toSet()
        return members.filter { it.id in ids }
    }
}

package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.Household
import java.time.Clock
import java.util.UUID

// Lectures dérivées de [SessionState] (iOS : extensions de `AppState`). Fonctions pures ; l'horloge est
// injectable pour les tests des événements à venir / passés.

/** Enfants et listes d'adultes de mon foyer. */
val SessionState.myChildren: List<Child>
    get() {
        val id = myHousehold?.id ?: return emptyList()
        return children.filter { it.householdId == id }
    }

/** Enfants des autres foyers de la famille. */
val SessionState.otherChildren: List<Child>
    get() {
        val id = myHousehold?.id ?: return children
        return children.filter { it.householdId != id }
    }

/** Vrai pour les listes de mon foyer : celles dont je ne vois pas les réservations (mode surprise). */
fun SessionState.isParent(child: Child): Boolean = child.householdId == myHousehold?.id

fun SessionState.household(of: Child): Household? = households.firstOrNull { it.id == of.householdId }

/** Enfants puis listes d'adultes du foyer. */
fun SessionState.children(of: Household): List<Child> {
    val members = children.filter { it.householdId == of.id }
    return members.filter { !it.isAdult } + members.filter { it.isAdult }
}

fun SessionState.child(id: UUID?): Child? = id?.let { wanted -> children.firstOrNull { it.id == wanted } }

fun SessionState.event(id: UUID?): GiftEvent? = id?.let { wanted -> events.firstOrNull { it.id == wanted } }

/**
 * Événements de la famille courante. [SessionState.events] contient tout ce que le compte peut voir, toutes
 * familles confondues (la base ne filtre que par droit de lecture, #60) : Noël est global, un anniversaire
 * suit son enfant, une fête de famille appartient à sa famille. Sans ce tri, changer de famille ne changeait
 * pas la liste (#115). Sans famille courante, rien n'est écarté.
 */
val SessionState.groupEvents: List<GiftEvent>
    get() {
        val group = currentGroup ?: return events
        val childIds = children.mapTo(HashSet()) { it.id }
        return events.filter { event ->
            when (event.kind) {
                GiftEventKind.CHRISTMAS -> true
                GiftEventKind.BIRTHDAY -> event.childId in childIds
                GiftEventKind.OTHER -> event.groupId == group.id
            }
        }
    }

fun SessionState.upcomingEvents(clock: Clock = Clock.systemDefaultZone()): List<GiftEvent> =
    groupEvents.filter { !it.isPast(clock) }.sortedBy { it.eventDate }

fun SessionState.pastEvents(clock: Clock = Clock.systemDefaultZone()): List<GiftEvent> =
    groupEvents.filter { it.isPast(clock) }.sortedByDescending { it.eventDate }

/** Événement « courant » : le prochain Noël, sinon le prochain événement. */
fun SessionState.currentEvent(clock: Clock = Clock.systemDefaultZone()): GiftEvent? {
    val upcoming = upcomingEvents(clock)
    return upcoming.firstOrNull { it.kind == GiftEventKind.CHRISTMAS } ?: upcoming.firstOrNull()
}

/**
 * Listes concernées par un événement : celle du fêté pour un anniversaire, toutes pour Noël et les fêtes
 * de famille (listes d'adultes comprises).
 */
fun SessionState.children(event: GiftEvent): List<Child> {
    val childId = event.childId ?: return children
    return children.filter { it.id == childId }
}

/** Même règle que la RLS : l'auteur, ou un parent de l'enfant pour un anniversaire. */
fun SessionState.canDelete(event: GiftEvent): Boolean =
    (event.createdBy != null && event.createdBy == userId) || (child(event.childId)?.let(::isParent) ?: false)

/**
 * Android ne modifie que les événements de type « autre » : Noël est global et les anniversaires sont
 * créés par le serveur (la RLS refuse le reste). Un événement passé est archivé.
 */
fun SessionState.canEdit(event: GiftEvent, clock: Clock = Clock.systemDefaultZone()): Boolean =
    event.kind == GiftEventKind.OTHER && !event.isPast(clock)

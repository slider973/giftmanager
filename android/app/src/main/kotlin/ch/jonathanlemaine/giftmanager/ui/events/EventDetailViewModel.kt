package ch.jonathanlemaine.giftmanager.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.ReservationCounts
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.core.session.SessionState
import ch.jonathanlemaine.giftmanager.core.session.canEdit
import ch.jonathanlemaine.giftmanager.core.session.child
import ch.jonathanlemaine.giftmanager.core.session.children
import ch.jonathanlemaine.giftmanager.core.session.event
import ch.jonathanlemaine.giftmanager.core.session.isParent
import ch.jonathanlemaine.giftmanager.data.EventRepository
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Une liste concernée par l'événement. [counts] est l'indicateur d'équilibre anonyme (#41) : `null` pour mes
 * propres listes (jamais affiché, mode surprise), pour un événement passé, ou tant que le serveur n'a rien
 * renvoyé.
 */
data class EventListEntry(val child: Child, val counts: ReservationCounts?)

/** « Léo fête ses 8 ans » / « Léo a fêté ses 8 ans » (#43). */
data class BirthdayInfo(val childName: String, val age: Int, val isPast: Boolean)

data class EventDetailUiState(
    /** `null` si l'événement a disparu (supprimé par ailleurs) : l'écran se ferme. */
    val event: GiftEvent? = null,
    val isPast: Boolean = false,
    val daysRemaining: Int = 0,
    /** Anniversaire d'un enfant dont on connaît la date de naissance. */
    val birthday: BirthdayInfo? = null,
    val otherKids: List<EventListEntry> = emptyList(),
    val otherAdults: List<EventListEntry> = emptyList(),
    /** Mes enfants et les listes d'adultes de mon foyer (mode surprise). */
    val mine: List<EventListEntry> = emptyList(),
    val canEdit: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: GiftError? = null,
) {
    /** Événement disparu : l'écran doit se fermer. */
    val isClosed: Boolean get() = event == null

    /** Les listes de mon foyer comprennent une liste d'adulte : « Mon foyer » plutôt que « Mes enfants ». */
    val mineHasAdult: Boolean get() = mine.any { it.child.isAdult }

    /** Aucune liste n'est concernée. */
    val isEmpty: Boolean get() = otherKids.isEmpty() && otherAdults.isEmpty() && mine.isEmpty()

    /** Événement passé : les listes s'ouvrent en lecture seule. */
    val readOnly: Boolean get() = isPast
}

/** Fiche d'un événement : listes concernées, enfants puis adultes, et équilibre (iOS : `EventDetailView`). */
class EventDetailViewModel(
    private val eventId: UUID,
    private val session: AppSession,
    private val eventRepo: EventRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
    private data class Local(
        val counts: Map<UUID, ReservationCounts> = emptyMap(),
        val isRefreshing: Boolean = false,
        val error: GiftError? = null,
    )

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<EventDetailUiState> = combine(local, session.state) { l, s -> build(l, s) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, build(local.value, session.state.value))

    init {
        // Compteurs : au départ, puis à chaque changement de cadeaux (réservation, ajout…) ; StateFlow émet d'emblée.
        viewModelScope.launch { session.itemsRevision.collectLatest { loadCounts() } }
    }

    private fun build(l: Local, s: SessionState): EventDetailUiState {
        val event = s.event(eventId) ?: return EventDetailUiState(isRefreshing = l.isRefreshing, error = l.error)
        val isPast = event.isPast(clock)
        // Jamais de compteur pour mes listes ni pour un événement passé.
        fun entry(child: Child) = EventListEntry(child, if (isPast || s.isParent(child)) null else l.counts[child.id])
        val lists = s.children(event)
        val kids = lists.filter { !s.isParent(it) && !it.isAdult }
        val adults = lists.filter { !s.isParent(it) && it.isAdult }
        val mine = lists.filter { s.isParent(it) }
        return EventDetailUiState(
            event = event,
            isPast = isPast,
            daysRemaining = event.daysRemaining(clock),
            birthday = birthday(event, s, isPast),
            otherKids = kids.map(::entry),
            otherAdults = adults.map(::entry),
            mine = mine.map(::entry),
            canEdit = s.canEdit(event, clock),
            isRefreshing = l.isRefreshing,
            error = l.error,
        )
    }

    private fun birthday(event: GiftEvent, s: SessionState, isPast: Boolean): BirthdayInfo? {
        if (event.kind != GiftEventKind.BIRTHDAY) return null
        val child = s.child(event.childId) ?: return null
        val age = child.age(on = event.eventDate.date) ?: return null
        return BirthdayInfo(child.firstName, age, isPast)
    }

    private suspend fun loadCounts() {
        val event = session.state.value.event(eventId) ?: return
        if (event.isPast(clock)) return
        runCatchingCancellable { eventRepo.reservationCounts(eventId) }
            .onSuccess { rows ->
                // Un seul compteur par enfant : le premier gagne (associateBy garde le dernier).
                local.update { it.copy(counts = rows.reversed().associateBy { r -> r.childId }) }
            }
            .onFailure { failure -> local.update { it.copy(error = GiftError.from(failure)) } }
    }

    /** Tirer pour actualiser : recharge la famille, puis les compteurs via [AppSession.itemsChanged]. */
    fun refresh() {
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val failure = session.reloadGroup().exceptionOrNull()
            session.itemsChanged()
            local.update { it.copy(isRefreshing = false, error = failure?.let(GiftError::from) ?: it.error) }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }
}

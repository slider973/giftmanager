package ch.jonathanlemaine.giftmanager.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.GiftEvent
import ch.jonathanlemaine.giftmanager.core.model.GiftEventKind
import ch.jonathanlemaine.giftmanager.core.model.ReservationCounts
import ch.jonathanlemaine.giftmanager.ui.children.ChildRow
import ch.jonathanlemaine.giftmanager.ui.children.ChildRowAvatarSize
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.EventKindTile
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.time.LocalDate
import java.util.UUID

/** Actions de [EventDetailScreen], reliées à [EventDetailViewModel] et à la navigation. */
data class EventDetailActions(
    val onBack: () -> Unit,
    val onEdit: (UUID) -> Unit,
    /** Ouvre la liste d'un enfant dans le contexte de cet événement. */
    val onOpenChild: (childId: UUID) -> Unit,
    val onDismissError: () -> Unit,
    /** Tirer pour actualiser (iOS : `.refreshable`) : [EventDetailViewModel.refresh]. */
    val onRefresh: () -> Unit = {},
)

/**
 * Fiche d'un événement (iOS : `EventDetailView`). Écran poussé dans l'onglet Accueil : la barre d'onglets
 * reste visible. Barre du haut ← « Retour » et « Modifier » (si l'événement est modifiable) ; le titre y
 * apparaît en fondu quand le grand titre passe dessous.
 *
 * Contenu : grand titre, en-tête (vignette, date, compte à rebours ou « Événement passé », anniversaire),
 * encart d'archive si passé, puis les listes — enfants, adultes, puis les miennes (mode surprise). Une liste
 * d'un autre foyer porte l'indicateur d'équilibre anonyme (#41). Tirer vers le bas actualise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(state: EventDetailUiState, actions: EventDetailActions, modifier: Modifier = Modifier) {
    val event = state.event ?: return
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val scrollState = rememberScrollState()
    val titleThreshold = with(LocalDensity.current) { 40.dp.toPx() }
    val titleUnderBar by remember { derivedStateOf { scrollState.value > titleThreshold } }
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
    val refreshState = rememberPullToRefreshState()

    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = event.title,
                titleVisible = titleUnderBar,
                showDivider = scrolled,
            ) {
                if (state.canEdit) TopBarTextAction(stringResource(R.string.event_edit), onClick = { actions.onEdit(event.id) })
            }
        },
    ) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            state = refreshState,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = state.isRefreshing,
                    containerColor = colors.surface,
                    color = colors.primary,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            ScreenColumn(scrollState = scrollState, spacing = spacing.m) {
                ScreenTitle(event.title)
                EventHeader(state, event)
                if (state.isPast) FCNotice(GiftIcons.Archive, stringResource(R.string.event_past_notice))
                Sections(state, actions)
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Vignette + date + « Dans 12 jours » / « Événement passé » + « Léo fête ses 8 ans » : un seul nœud TalkBack. */
@Composable
private fun EventHeader(state: EventDetailUiState, event: GiftEvent) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = spacing.s)
            .semantics(mergeDescendants = true) { },
    ) {
        EventKindTile(kind = event.kind, size = 56.dp.scaledByFont(1.3f))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
            Text(Formatting.dateText(event.eventDate), style = type.headline, color = colors.textPrimary)
            Text(
                text = if (state.isPast) stringResource(R.string.event_past) else Formatting.countdownText(state.daysRemaining),
                style = type.caption,
                color = colors.textSecondary,
            )
            state.birthday?.let { birthday ->
                val age = pluralStringResource(R.plurals.event_age, birthday.age, birthday.age)
                Text(
                    text = stringResource(
                        if (birthday.isPast) R.string.event_birthday_past else R.string.event_birthday_upcoming,
                        birthday.childName,
                        age,
                    ),
                    style = type.captionBold,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun Sections(state: EventDetailUiState, actions: EventDetailActions) {
    val spacing = GiftTheme.spacing
    // Une section qui en suit une autre s'en détache un peu (iOS : `.padding(.top, Spacing.m)`).
    fun gap(after: Boolean) = Modifier.padding(top = if (after) spacing.s else 0.dp)
    if (state.otherKids.isNotEmpty()) {
        SectionHeader(stringResource(R.string.event_section_kids))
        state.otherKids.forEach { ListLink(it, actions) }
    }
    if (state.otherAdults.isNotEmpty()) {
        SectionHeader(stringResource(R.string.event_section_adults), modifier = gap(state.otherKids.isNotEmpty()))
        state.otherAdults.forEach { ListLink(it, actions) }
    }
    if (state.mine.isNotEmpty()) {
        // Mes enfants et les listes d'adultes de mon foyer : mode surprise pour toutes.
        SectionHeader(
            stringResource(if (state.mineHasAdult) R.string.event_section_mine_household else R.string.event_section_mine_kids),
            modifier = gap(state.otherKids.isNotEmpty() || state.otherAdults.isNotEmpty()),
        )
        state.mine.forEach { ListLink(it, actions) }
        FCNotice(
            GiftIcons.EyeSlash,
            stringResource(if (state.mineHasAdult) R.string.event_notice_surprise_household else R.string.event_notice_surprise_kids),
            tone = FCNoticeTone.SURPRISE,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
    if (state.isEmpty) {
        EmptyStateView(
            illustration = Illustration.EMPTY_BOX,
            title = stringResource(R.string.event_empty_title),
            message = stringResource(R.string.event_empty_message),
        )
    }
}

/**
 * Carte d'une liste : [ChildRow] (avatar, prénom, âge ou « Liste d'adulte », foyer), indicateur d'équilibre
 * sous le prénom, chevron. Toute la carte ouvre la liste ; TalkBack la lit d'un bloc (« Zoé, 6 ans · Foyer
 * Martin, Équilibre : 2 cadeaux réservés, 1 acheté, bouton »).
 */
@Composable
private fun ListLink(entry: EventListEntry, actions: EventDetailActions) {
    val child = entry.child
    val spacing = GiftTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .fcClickable(
                onClick = { actions.onOpenChild(child.id) },
                onClickLabel = stringResource(R.string.event_open_list),
                shape = GiftTheme.radius.cardShape,
                pressedScale = 0.98f,
            )
            .fcCard(padding = spacing.m)
            .semantics(mergeDescendants = true) { },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
            ChildRow(child, householdName = entry.householdName)
            entry.counts?.let { Balance(it) }
        }
        Icon(
            imageVector = GiftIcons.ChevronRight,
            contentDescription = null,
            tint = GiftTheme.colors.textSecondary,
            modifier = Modifier.size(GiftTheme.type.callout.glyphSize(1.4f)),
        )
    }
}

/**
 * Indicateur d'équilibre discret, aligné sur le prénom : 🎁 « 2 réservés · 1 acheté » en `textSecondary` ;
 * rien de prévu → ⏳ « Rien de prévu pour l'instant » en `potFg` medium (l'« ambre doux » d'iOS : jamais
 * `accentAmber` en texte, cf. DESIGN.md), pour qu'aucun enfant ne soit oublié. Le sens passe aussi par
 * l'icône et le texte, jamais par la couleur seule.
 */
@Composable
private fun Balance(counts: ReservationCounts) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val empty = counts.total == 0
    val tint = if (empty) colors.potFg else colors.textSecondary
    val description = stringResource(R.string.event_balance_a11y, counts.accessibilityText)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = Modifier
            .padding(start = ChildRowAvatarSize + GiftTheme.spacing.m)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Icon(
            imageVector = if (empty) GiftIcons.Hourglass else GiftIcons.Gift,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(type.caption.glyphSize(1.2f)),
        )
        Text(
            text = counts.summaryText,
            style = type.caption.tabular().copy(fontWeight = if (empty) FontWeight.Medium else FontWeight.Normal),
            color = tint,
        )
    }
}

// region Aperçus

private fun previewId(seed: String): UUID = UUID.nameUUIDFromBytes(seed.toByteArray())

private val previewZoe = Child(previewId("zoe"), previewId("h2"), "Zoé", DayDate(LocalDate.of(2019, 4, 2)), "🦄", "pastelPink")
private val previewNoe = Child(previewId("noe"), previewId("h2"), "Noé", DayDate(LocalDate.of(2016, 9, 14)), "🚀", "pastelBlue")
private val previewMamie = Child(previewId("mamie"), previewId("h3"), "Mamie Jo", avatarEmoji = "🌸", avatarColor = "pastelLavender", isAdult = true)
private val previewLeo = Child(previewId("leo"), previewId("h1"), "Léo", DayDate(LocalDate.of(2018, 3, 12)), "🦊", "pastelPeach")

private val previewEvent = GiftEvent(previewId("e1"), previewId("g"), GiftEventKind.CHRISTMAS, "Noël 2026", DayDate(LocalDate.of(2026, 12, 25)))

private val previewState = EventDetailUiState(
    event = previewEvent,
    daysRemaining = 54,
    otherKids = listOf(
        EventListEntry(previewZoe, ReservationCounts(previewZoe.id, 2, 1, 0), "Claire & Paul"),
        EventListEntry(previewNoe, ReservationCounts(previewNoe.id, 0, 0, 0), "Claire & Paul"),
    ),
    otherAdults = listOf(EventListEntry(previewMamie, ReservationCounts(previewMamie.id, 0, 0, 1), "Mamie et Papi")),
    mine = listOf(EventListEntry(previewLeo, null, "Foyer de Jonathan")),
    canEdit = true,
)

private val previewActions = EventDetailActions({}, {}, {}, {})

@GiftPreviews
@Composable
private fun EventDetailUpcomingPreview() = ScreenPreviewFrame(height = 1100.dp) { EventDetailScreen(previewState, previewActions) }

@GiftPreviews
@Composable
private fun EventDetailPastBirthdayPreview() = ScreenPreviewFrame {
    EventDetailScreen(
        EventDetailUiState(
            event = GiftEvent(previewId("e2"), previewId("g"), GiftEventKind.BIRTHDAY, "Anniversaire de Zoé", DayDate(LocalDate.of(2026, 4, 2)), previewZoe.id),
            isPast = true,
            birthday = BirthdayInfo("Zoé", 7, isPast = true),
            otherKids = listOf(EventListEntry(previewZoe, null, "Claire & Paul")),
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun EventDetailEmptyPreview() = ScreenPreviewFrame {
    EventDetailScreen(
        EventDetailUiState(
            event = GiftEvent(previewId("e3"), previewId("g"), GiftEventKind.OTHER, "Fête de fin d'année", DayDate(LocalDate.of(2026, 11, 2))),
            daysRemaining = 1,
            canEdit = true,
        ),
        previewActions,
    )
}

// endregion

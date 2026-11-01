package ch.jonathanlemaine.giftmanager.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.EventKindTile
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.util.UUID

/** Actions de [EventDetailScreen], reliées à [EventDetailViewModel] et à la navigation. */
data class EventDetailActions(
    val onBack: () -> Unit,
    val onEdit: (UUID) -> Unit,
    /** Ouvre la liste d'un enfant dans le contexte de cet événement. */
    val onOpenChild: (childId: UUID) -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Câblage minimal de la fiche d'un événement (iOS : `EventDetailView`), à remplacer par l'écran de
 * l'`ui-designer`. Tirer pour actualiser (`EventDetailViewModel.refresh`) reste à brancher sur l'écran final.
 */
@Composable
fun EventDetailScreen(state: EventDetailUiState, actions: EventDetailActions, modifier: Modifier = Modifier) {
    val event = state.event ?: return
    val spacing = GiftTheme.spacing
    val colors = GiftTheme.colors
    ScreenColumn(modifier = modifier, spacing = spacing.m) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextLinkButton(text = stringResource(R.string.back), onClick = actions.onBack)
            if (state.canEdit) TextLinkButton(text = stringResource(R.string.event_edit), onClick = { actions.onEdit(event.id) })
        }
        ScreenTitle(event.title)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalAlignment = Alignment.CenterVertically) {
            EventKindTile(kind = event.kind)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(Formatting.dateText(event.eventDate), style = GiftTheme.type.headline, color = colors.textPrimary)
                Text(
                    text = if (state.isPast) stringResource(R.string.event_past) else Formatting.countdownText(state.daysRemaining),
                    style = GiftTheme.type.caption,
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
                        style = GiftTheme.type.captionBold,
                        color = colors.textPrimary,
                    )
                }
            }
        }
        if (state.isPast) FCNotice(GiftIcons.Archive, stringResource(R.string.event_past_notice))
        if (state.otherKids.isNotEmpty()) {
            SectionHeader(stringResource(R.string.event_section_kids))
            state.otherKids.forEach { ListLink(it, actions) }
        }
        if (state.otherAdults.isNotEmpty()) {
            SectionHeader(stringResource(R.string.event_section_adults))
            state.otherAdults.forEach { ListLink(it, actions) }
        }
        if (state.mine.isNotEmpty()) {
            SectionHeader(stringResource(if (state.mineHasAdult) R.string.event_section_mine_household else R.string.event_section_mine_kids))
            state.mine.forEach { ListLink(it, actions) }
            FCNotice(
                GiftIcons.EyeSlash,
                stringResource(if (state.mineHasAdult) R.string.event_notice_surprise_household else R.string.event_notice_surprise_kids),
                tone = FCNoticeTone.SURPRISE,
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
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Une liste, avec son indicateur d'équilibre anonyme quand le serveur en fournit un. */
@Composable
private fun ListLink(entry: EventListEntry, actions: EventDetailActions) {
    val child = entry.child
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .fcCard()
            .fcClickable(onClick = { actions.onOpenChild(child.id) }, shape = GiftTheme.radius.cardShape, pressedScale = 0.98f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
            ChildAvatar(name = child.firstName, emoji = child.avatarEmoji, colorName = child.avatarColor, photoUrl = child.avatarUrl)
            Text(child.firstName, style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        }
        entry.counts?.let { counts ->
            val description = stringResource(R.string.event_balance_a11y, counts.accessibilityText)
            Text(
                text = counts.summaryText,
                style = GiftTheme.type.caption,
                color = GiftTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 56.dp).clearAndSetSemantics { contentDescription = description },
            )
        }
    }
}

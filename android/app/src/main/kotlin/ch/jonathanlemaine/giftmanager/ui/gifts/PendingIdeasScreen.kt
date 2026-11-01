package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Rappels des idées en attente. L'écran définitif (ui-designer) garde cette surface. */
data class PendingIdeasActions(
    val onBack: () -> Unit,
    val onAccept: (PendingIdea) -> Unit,
    val onOwnedAlready: (PendingIdea) -> Unit,
    /** Refus, avec un mot facultatif pour l'auteur. */
    val onReject: (PendingIdea, String?) -> Unit,
    val onRefresh: () -> Unit,
    val onDismissError: () -> Unit,
)

/** Idées en attente de verdict (câblage minimal) : une carte par idée, avec Accepter / Il l'a déjà / Refuser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingIdeasScreen(state: PendingIdeasUiState, actions: PendingIdeasActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    var rejecting by remember { mutableStateOf<PendingIdea?>(null) }
    var note by remember { mutableStateOf("") }
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = stringResource(R.string.pending_ideas_title),
            )
        },
    ) {
        PullToRefreshBox(isRefreshing = false, onRefresh = actions.onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(spacing.m),
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xl),
            ) {
                if (state.hasLoaded && state.ideas.isEmpty()) {
                    item {
                        EmptyStateView(
                            Illustration.MASCOT_THINKING,
                            stringResource(R.string.pending_ideas_empty_title),
                            stringResource(R.string.pending_ideas_empty_message),
                        )
                    }
                }
                items(state.ideas, key = { it.id }) { idea ->
                    IdeaCard(idea, working = state.workingId == idea.id, blocked = state.workingId != null, actions) {
                        rejecting = idea
                        note = ""
                    }
                }
            }
        }
    }
    rejecting?.let { idea ->
        GiftAlertDialog(
            title = stringResource(R.string.pending_ideas_reject_title),
            message = stringResource(R.string.pending_ideas_reject_message),
            confirmLabel = stringResource(R.string.pending_ideas_reject),
            onConfirm = {
                actions.onReject(idea, note)
                rejecting = null
            },
            onDismiss = { rejecting = null },
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        ) {
            FCTextField(
                label = stringResource(R.string.pending_ideas_reject_note),
                value = note,
                onValueChange = { note = it },
                isLabelHidden = true,
                placeholder = stringResource(R.string.pending_ideas_reject_note),
            )
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun IdeaCard(idea: PendingIdea, working: Boolean, blocked: Boolean, actions: PendingIdeasActions, onReject: () -> Unit) {
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            RemoteImage(url = idea.imageUrl, placeholderSeed = idea.title, modifier = Modifier.size(64.dp))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(idea.title, style = GiftTheme.type.headline)
                Text(
                    stringResource(R.string.pending_ideas_by, idea.author, idea.childName),
                    style = GiftTheme.type.caption,
                    color = GiftTheme.colors.textSecondary,
                )
                idea.notes?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = GiftTheme.type.caption, color = GiftTheme.colors.textSecondary, maxLines = 3)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            PrimaryButton(
                text = stringResource(R.string.pending_ideas_accept),
                onClick = { actions.onAccept(idea) },
                isLoading = working,
                enabled = !blocked,
            )
            SecondaryButton(
                text = stringResource(R.string.pending_ideas_owned_already),
                onClick = { actions.onOwnedAlready(idea) },
                enabled = !blocked,
            )
        }
        TextLinkButton(stringResource(R.string.pending_ideas_reject), onReject, enabled = !blocked)
        FCNotice(GiftIcons.EyeSlash, stringResource(R.string.pending_ideas_never_know), tone = FCNoticeTone.SURPRISE)
    }
}

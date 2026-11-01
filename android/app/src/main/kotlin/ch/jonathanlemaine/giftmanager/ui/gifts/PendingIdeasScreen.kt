package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.PendingIdea
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.time.Instant
import kotlinx.coroutines.delay

/** Rappels des idées en attente. */
data class PendingIdeasActions(
    val onBack: () -> Unit,
    val onAccept: (PendingIdea) -> Unit,
    val onOwnedAlready: (PendingIdea) -> Unit,
    /** Refus, avec un mot facultatif pour l'auteur. */
    val onReject: (PendingIdea, String?) -> Unit,
    val onRefresh: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * « Propositions » (#57, iOS : `PendingIdeasView`) : une carte par idée proposée pour mes enfants — vignette,
 * titre, « Mamie propose ce cadeau pour Léo », notes —, puis Accepter, « Il l'a déjà », Refuser (avec un mot
 * facultatif) et le rappel « Tu ne sauras jamais qui offre ce cadeau ». Pendant un verdict, les autres cartes
 * sont bloquées. Tirer pour actualiser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingIdeasScreen(state: PendingIdeasUiState, actions: PendingIdeasActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    var rejecting by remember { mutableStateOf<PendingIdea?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var refreshing by remember { mutableStateOf(false) }
    // Le ViewModel n'expose pas d'état « actualisation » (une liste identique n'émet rien) : l'indicateur
    // s'efface à la nouvelle liste, ou au plus tard après un court instant.
    LaunchedEffect(state.ideas, state.error) { refreshing = false }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            delay(900)
            refreshing = false
        }
    }
    val refreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()

    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = stringResource(R.string.pending_ideas_title),
                showDivider = scrolled,
            )
        },
    ) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                actions.onRefresh()
            },
            state = refreshState,
            contentAlignment = Alignment.TopCenter,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = refreshState,
                    isRefreshing = refreshing,
                    containerColor = colors.surface,
                    color = colors.primary,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.m),
                contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.s, bottom = spacing.xxl + bottomInset),
                modifier = Modifier.fillMaxSize(),
            ) {
                val column = Modifier.widthIn(max = ScreenContentMaxWidth).fillMaxWidth()
                when {
                    !state.hasLoaded -> item(key = "loading") {
                        GiftLoadingView(size = 72.dp, label = stringResource(R.string.pending_ideas_loading), modifier = column.padding(top = spacing.xxl))
                    }
                    state.ideas.isEmpty() -> item(key = "empty") {
                        EmptyStateView(
                            illustration = Illustration.MASCOT_THINKING,
                            title = stringResource(R.string.pending_ideas_empty_title),
                            message = stringResource(R.string.pending_ideas_empty_message),
                            modifier = column,
                        )
                    }
                }
                items(state.ideas, key = { it.id }) { idea ->
                    IdeaCard(
                        idea = idea,
                        working = state.workingId == idea.id,
                        blocked = state.workingId != null,
                        actions = actions,
                        onReject = {
                            rejecting = idea
                            note = ""
                        },
                        modifier = column.animateItem(fadeInSpec = null, placementSpec = if (GiftTheme.reduceMotion) null else androidx.compose.animation.core.spring(stiffness = 500f)),
                    )
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
                actions.onReject(idea, note.trim().ifEmpty { null })
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
                placeholder = stringResource(R.string.pending_ideas_reject_note),
                isLabelHidden = true,
                singleLine = false,
            )
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/**
 * Carte d'une idée : titre (titre TalkBack) et auteur lus d'un bloc, puis les trois verdicts. « Accepter » et
 * « Il l'a déjà » côte à côte, empilés aux grandes polices ; « Refuser » en lien, à l'écart.
 */
@Composable
private fun IdeaCard(
    idea: PendingIdea,
    working: Boolean,
    blocked: Boolean,
    actions: PendingIdeasActions,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val stacked = LocalDensity.current.fontScale >= 1.3f
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = modifier.fcCard()) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m), modifier = Modifier.semantics(mergeDescendants = true) { }) {
            RemoteImage(
                url = idea.imageUrl,
                placeholderSeed = idea.title,
                modifier = Modifier.size(64.dp).clip(GiftTheme.radius.thumbShape).clearAndSetSemantics { },
            )
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
                Text(idea.title, style = type.headline, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.pending_ideas_by, idea.author, idea.childName), style = type.caption, color = colors.textSecondary)
                idea.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = type.caption, color = colors.textSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val accept: @Composable (Modifier) -> Unit = { m ->
            PrimaryButton(
                text = stringResource(R.string.pending_ideas_accept),
                onClick = { actions.onAccept(idea) },
                leadingIcon = GiftIcons.Checkmark,
                isLoading = working,
                enabled = !blocked,
                modifier = m,
            )
        }
        val owned: @Composable (Modifier) -> Unit = { m ->
            SecondaryButton(
                text = stringResource(R.string.pending_ideas_owned_already),
                onClick = { actions.onOwnedAlready(idea) },
                leadingIcon = GiftIcons.Verified,
                enabled = !blocked,
                modifier = m,
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                accept(Modifier.fillMaxWidth())
                owned(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                accept(Modifier.weight(1f))
                owned(Modifier.weight(1f))
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextLinkButton(stringResource(R.string.pending_ideas_reject), onReject, enabled = !blocked)
        }
        PrivacyCaptionEye(stringResource(R.string.pending_ideas_never_know))
    }
}

/** Rappel « Tu ne sauras jamais qui offre ce cadeau » : œil barré + légende, aligné à gauche comme sur iOS. */
@Composable
private fun PrivacyCaptionEye(text: String) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        androidx.compose.material3.Icon(
            GiftIcons.EyeSlash,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(with(LocalDensity.current) { (type.caption.fontSize * 1.15f).toDp() }),
        )
        Text(text, style = type.caption, color = colors.textSecondary)
    }
}

// region Aperçus

private val previewActions = PendingIdeasActions({}, {}, {}, { _, _ -> }, {}, {})

private val previewIdeas = listOf(
    PendingIdea(
        GiftPreviewData.id("p1"), GiftPreviewData.leo.id, "Léo", "Atelier poterie pour enfants",
        notes = "Le samedi matin, à Lausanne. Il adore modeler.", authorName = "Mamie Jo", createdAt = Instant.EPOCH,
    ),
    PendingIdea(GiftPreviewData.id("p2"), GiftPreviewData.leo.id, "Léo", "Trottinette Micro Maxi", createdAt = Instant.EPOCH),
)

@GiftPreviews
@Composable
private fun PendingIdeasPreview() = ScreenPreviewFrame(height = 1000.dp) {
    PendingIdeasScreen(PendingIdeasUiState(ideas = previewIdeas, hasLoaded = true, workingId = previewIdeas.first().id), previewActions)
}

@GiftPreviews
@Composable
private fun PendingIdeasEmptyPreview() = ScreenPreviewFrame {
    PendingIdeasScreen(PendingIdeasUiState(hasLoaded = true), previewActions)
}

// endregion

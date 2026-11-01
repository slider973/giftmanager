package ch.jonathanlemaine.giftmanager.ui.childmode

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeItem
import ch.jonathanlemaine.giftmanager.core.childmode.ExitHold
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Écran de câblage du mode enfant (#75) : fonctionnel, sans travail de design. Le `ui-designer` le reprend.

/** Rappels du mode enfant. */
data class ChildModeActions(
    val onToggleFavorite: (UUID) -> Unit,
    /** L'anneau « Quitter » est plein : demande la vérification de l'adulte. */
    val onUnlock: () -> Unit,
    val onDismissSaveFailed: () -> Unit,
)

/**
 * Mode enfant (iOS : `ChildModeView`) : en-tête « Les envies de Léa », compteur de cœurs, grille de souhaits (photo,
 * titre, cœur) et bouton « Quitter » à maintenir. Aucun prix, statut, réservation, cagnotte, idée ni action de gestion.
 */
@Composable
fun ChildModeScreen(state: ChildModeUiState, isAuthenticationAvailable: Boolean, actions: ChildModeActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    var exitHint by remember { mutableStateOf(false) }
    // Les messages s'effacent seuls.
    LaunchedEffect(exitHint) {
        if (exitHint) {
            delay(HINT_MILLIS)
            exitHint = false
        }
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            delay(HINT_MILLIS)
            actions.onDismissSaveFailed()
        }
    }
    Column(modifier = modifier.fcScreenBackground().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.s),
        ) {
            val hearts = state.favoriteCount
            val heartsText = if (hearts > 1) stringResource(R.string.child_mode_hearts_many, hearts) else stringResource(R.string.child_mode_hearts_one, hearts)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = heartsText },
            ) {
                Icon(GiftIcons.Heart, contentDescription = null, tint = colors.heart)
                Text("$hearts", style = GiftTheme.type.headline, color = colors.textPrimary)
            }
            ExitHoldButton(
                isUnlocking = state.isAuthenticating,
                isAuthenticationAvailable = isAuthenticationAvailable,
                onTooShort = { exitHint = true },
                onUnlock = actions.onUnlock,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isLoading -> GiftLoadingView(modifier = Modifier.fillMaxSize())
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(spacing.l),
                    verticalArrangement = Arrangement.spacedBy(spacing.l),
                    modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xl),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                            Text(
                                stringResource(R.string.child_mode_title, state.child?.firstName.orEmpty()),
                                style = GiftTheme.type.largeTitle,
                                color = colors.textPrimary,
                            )
                            Text(stringResource(R.string.child_mode_subtitle), style = GiftTheme.type.title, color = colors.textSecondary)
                        }
                    }
                    if (state.items.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateView(
                                illustration = Illustration.MASCOT_SLEEPING,
                                title = stringResource(R.string.child_mode_empty_title),
                                message = stringResource(R.string.child_mode_empty_message),
                            )
                        }
                    }
                    items(state.items, key = { it.id }) { item -> ChildModeCard(item) { actions.onToggleFavorite(item.id) } }
                }
            }
            val hint = when {
                state.saveFailed -> R.string.child_mode_save_failed
                exitHint -> R.string.child_mode_exit_hint
                else -> null
            }
            if (hint != null) {
                Text(
                    stringResource(hint),
                    style = GiftTheme.type.callout,
                    color = colors.textPrimary,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(spacing.xl).fcCard(),
                )
            }
        }
    }
}

/** Une envie : photo, titre, cœur. Toute la carte bascule le cœur. */
@Composable
private fun ChildModeCard(item: ChildModeItem, onToggle: () -> Unit) {
    val colors = GiftTheme.colors
    val label = stringResource(if (item.isFavorite) R.string.child_mode_heart_remove else R.string.child_mode_heart_add, item.title)
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier
            .fcCard()
            .fcClickable(onClick = onToggle, shape = GiftTheme.radius.cardShape)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        RemoteImage(item.imageUrl, placeholderSeed = item.title, modifier = Modifier.fillMaxWidth().height(140.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.title, style = GiftTheme.type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Icon(
                if (item.isFavorite) GiftIcons.Heart else GiftIcons.HeartOutline,
                contentDescription = null,
                tint = colors.heart,
            )
        }
    }
}

/**
 * « Quitter » (iOS : `ChildModeExitButton`) : il faut **maintenir** [ExitHold.HOLD_MILLIS] ms. Le pourcentage
 * rempli est affiché ; relâché trop tôt, il se vide et [onTooShort] explique. Une fois plein, [onUnlock] demande la
 * vérification de l'adulte. Pour TalkBack, l'activation directe passe par la vérification quand elle est possible.
 */
@Composable
private fun ExitHoldButton(
    isUnlocking: Boolean,
    isAuthenticationAvailable: Boolean,
    onTooShort: () -> Unit,
    onUnlock: () -> Unit,
) {
    val colors = GiftTheme.colors
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val description = stringResource(R.string.child_mode_exit_description)
    LaunchedEffect(isUnlocking) {
        if (!isUnlocking) {
            progress = 0f
            holding = false
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier
            .height(48.dp)
            .fcCard(padding = 8.dp)
            .pointerInput(isUnlocking) {
                if (isUnlocking) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holding = true
                        val hold = scope.launch {
                            val start = withFrameMillis { it }
                            while (true) {
                                val elapsed = withFrameMillis { it } - start
                                progress = ExitHold.progress(elapsed)
                                if (ExitHold.isComplete(elapsed)) {
                                    onUnlock()
                                    break
                                }
                            }
                        }
                        tryAwaitRelease()
                        holding = false
                        if (hold.isActive) {
                            hold.cancel()
                            progress = 0f
                            onTooShort()
                        }
                    },
                )
            }
            .semantics(mergeDescendants = true) {
                contentDescription = description
                onClick(label = description) {
                    if (isAuthenticationAvailable) onUnlock() else onTooShort()
                    true
                }
            },
    ) {
        Icon(GiftIcons.Lock, contentDescription = null, tint = colors.primary)
        Text(
            stringResource(if (holding || isUnlocking) R.string.child_mode_exit_holding else R.string.child_mode_exit) +
                if (holding || isUnlocking) " ${(progress * 100).toInt()} %" else "",
            style = GiftTheme.type.captionBold,
            color = colors.textPrimary,
        )
    }
}

private const val HINT_MILLIS = 3500L

/** Entrée « Mode enfant » en tête de la liste d'un de mes enfants (iOS : `ChildModeEntryCard`). */
@Composable
fun ChildModeEntryCard(childName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = modifier
            .fcCard()
            .fcClickable(onClick = onClick, shape = GiftTheme.radius.cardShape)
            .semantics(mergeDescendants = true) { },
    ) {
        Text(stringResource(R.string.child_mode_entry_title), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        Text(stringResource(R.string.child_mode_entry_message, childName), style = GiftTheme.type.caption, color = GiftTheme.colors.textSecondary)
    }
}

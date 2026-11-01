package ch.jonathanlemaine.giftmanager.ui.childmode

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.childmode.ChildModeItem
import ch.jonathanlemaine.giftmanager.core.childmode.ExitHold
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftPreviewData
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Rappels du mode enfant. */
data class ChildModeActions(
    val onToggleFavorite: (UUID) -> Unit,
    /** L'anneau « Quitter » est plein : demande la vérification de l'adulte. */
    val onUnlock: () -> Unit,
    val onDismissSaveFailed: () -> Unit,
)

/** Rayon des grandes cartes : plus rond que `Radius.card`, registre jouet (iOS : 28). */
private val ToyCardShape = RoundedCornerShape(28.dp)

/** Durée d'affichage du bandeau d'astuce. */
private const val HINT_MILLIS = 3500L

/** À partir de cette échelle de police, une seule colonne et pas de mascotte (tailles d'accessibilité iOS). */
private const val LARGE_FONT_SCALE = 1.5f

/** Titres « jouet » en Nunito : équivalents de `.title3` / `.headline` arrondis d'iOS. */
private val toyTitle: TextStyle @Composable get() = GiftTheme.type.title.copy(fontSize = 20.sp, lineHeight = 25.sp)
private val toyCounter: TextStyle @Composable get() = GiftTheme.type.title.copy(fontSize = 17.sp, lineHeight = 22.sp)

private enum class ChildModeHint { EXIT, SAVE_FAILED }

/**
 * Mode enfant (iOS : `ChildModeView`) : sur le téléphone du parent, l'enfant parcourt ses envies en grand et pose des
 * cœurs « très envie ». Barre fixe (compteur de cœurs, « Quitter » à maintenir), en-tête « Les envies de Léa » avec la
 * loutre, grille de grandes cartes photo (2 colonnes, 1 aux grandes polices, 3 ou 4 sur grand écran). Toute la carte
 * bascule le cœur. **Aucun** prix, statut, réservation, cagnotte, idée ni action de gestion : l'état n'en porte pas.
 * Bandeau d'astuce 3,5 s (« Pour quitter… », échec d'enregistrement), annoncé par TalkBack.
 */
@Composable
fun ChildModeScreen(state: ChildModeUiState, isAuthenticationAvailable: Boolean, actions: ChildModeActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    val largeFont = LocalDensity.current.fontScale >= LARGE_FONT_SCALE
    // Gerbe de cœurs : compteur par souhait, rejouée à chaque cœur posé.
    val bursts = remember { mutableStateMapOf<UUID, Int>() }
    var exitHintId by remember { mutableIntStateOf(0) }
    var exitHint by remember { mutableStateOf(false) }
    LaunchedEffect(exitHintId) {
        if (exitHintId > 0) {
            exitHint = true
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
    val hint = when {
        state.saveFailed -> ChildModeHint.SAVE_FAILED
        exitHint -> ChildModeHint.EXIT
        else -> null
    }

    Box(modifier = modifier.fcScreenBackground().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                favoriteCount = state.favoriteCount,
                isUnlocking = state.isAuthenticating,
                isAuthenticationAvailable = isAuthenticationAvailable,
                onTooShort = { exitHintId++ },
                onUnlock = actions.onUnlock,
            )
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val columns = when {
                    largeFont -> 1
                    maxWidth >= 840.dp -> 4
                    maxWidth >= 600.dp -> 3
                    else -> 2
                }
                when {
                    state.isLoading -> GiftLoadingView(modifier = Modifier.fillMaxSize().padding(spacing.xxl))
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        horizontalArrangement = Arrangement.spacedBy(spacing.l),
                        verticalArrangement = Arrangement.spacedBy(spacing.l),
                        contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.s, bottom = spacing.xxl * 2),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                            Header(state, showMascot = !largeFont, modifier = Modifier.padding(bottom = spacing.s))
                        }
                        if (state.items.isEmpty()) {
                            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                                if (state.error != null) {
                                    EmptyStateView(
                                        illustration = Illustration.MASCOT_THINKING,
                                        title = stringResource(R.string.child_mode_error_title),
                                        message = stringResource(R.string.child_mode_error_message),
                                    )
                                } else {
                                    EmptyStateView(
                                        illustration = Illustration.EMPTY_BOX,
                                        title = stringResource(R.string.child_mode_empty_title),
                                        message = stringResource(R.string.child_mode_empty_message),
                                    )
                                }
                            }
                        }
                        items(state.items, key = { it.id }) { item ->
                            ToyCard(
                                item = item,
                                burst = bursts[item.id],
                                largeFont = largeFont,
                                onToggle = {
                                    haptics.performHapticFeedback(if (item.isFavorite) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                                    if (!item.isFavorite && !reduceMotion) bursts[item.id] = (bursts[item.id] ?: 0) + 1
                                    actions.onToggleFavorite(item.id)
                                },
                            )
                        }
                    }
                }
            }
        }
        HintBanner(
            hint = hint,
            onDismiss = {
                exitHint = false
                if (state.saveFailed) actions.onDismissSaveFailed()
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// region Barre du haut

@Composable
private fun TopBar(
    favoriteCount: Int,
    isUnlocking: Boolean,
    isAuthenticationAvailable: Boolean,
    onTooShort: () -> Unit,
    onUnlock: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(GiftTheme.colors.background)
            .padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.s),
    ) {
        HeartCounter(favoriteCount)
        Spacer(Modifier.weight(1f))
        ChildModeExitButton(
            isUnlocking = isUnlocking,
            isAuthenticationAvailable = isAuthenticationAvailable,
            onTooShort = onTooShort,
            onUnlock = onUnlock,
        )
    }
}

/** Compteur « ♥ 3 » en capsule `surface` ; le cœur rebondit à chaque changement. TalkBack : « 3 cœurs posés ». */
@Composable
private fun HeartCounter(count: Int) {
    val colors = GiftTheme.colors
    val spoken = if (count > 1) stringResource(R.string.child_mode_hearts_many, count) else stringResource(R.string.child_mode_hearts_one, count)
    val bounce = rememberBounce(count)
    val style = toyCounter.copy(fontWeight = FontWeight.ExtraBold).tabular()
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = HitTarget.minimum)
            .background(colors.surface, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.m)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(
            GiftIcons.Heart,
            contentDescription = null,
            tint = colors.heart,
            modifier = Modifier
                .size(style.glyphSize(1.1f))
                .graphicsLayer {
                    scaleX = bounce.value
                    scaleY = bounce.value
                },
        )
        Text(count.toString(), style = style, color = colors.textPrimary)
    }
}

/**
 * Échelle de rebond, rejouée quand [trigger] change (pas au premier affichage). Rien si animations réduites.
 */
@Composable
private fun rememberBounce(trigger: Any): Animatable<Float, *> {
    val scale = remember { Animatable(1f) }
    val reduceMotion = GiftTheme.reduceMotion
    var previous by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == previous) return@LaunchedEffect
        previous = trigger
        if (reduceMotion) return@LaunchedEffect
        scale.snapTo(0.7f)
        scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
    }
    return scale
}

/**
 * « Quitter » du mode enfant (iOS : `ChildModeExitButton`) : capsule `surface` avec anneau de progression et
 * cadenas. Il faut **maintenir** [ExitHold.HOLD_MILLIS] ms : l'anneau `primary` se remplit (« Maintiens… »), un
 * retour haptique marque la fin, puis [onUnlock] demande la vérification de l'adulte. Relâché ou glissé hors du
 * bouton trop tôt : l'anneau retombe et [onTooShort] affiche l'astuce.
 *
 * Le temps est compté sur l'horloge d'images, pas par une animation : « Supprimer les animations » (échelle 0)
 * n'abrège pas l'appui. TalkBack : bouton « Quitter le mode enfant », activation et action personnalisée
 * directes vers la vérification si l'appareil en a une ; sinon, l'astuce (l'appui maintenu reste obligatoire).
 */
@Composable
internal fun ChildModeExitButton(
    isUnlocking: Boolean,
    isAuthenticationAvailable: Boolean,
    onTooShort: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    val unlocking by rememberUpdatedState(isUnlocking)
    val unlock by rememberUpdatedState(onUnlock)
    val tooShort by rememberUpdatedState(onTooShort)
    val description = stringResource(R.string.child_mode_exit_description)
    val ringSize = 28.dp.scaledByFont()

    fun fallBack() {
        scope.launch { if (reduceMotion) progress.snapTo(0f) else progress.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(isUnlocking) {
        if (isUnlocking) progress.snapTo(1f) else if (!holding && progress.value > 0f) fallBack()
    }
    val accessibleAction = {
        if (isAuthenticationAvailable) onUnlock() else onTooShort()
        true
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = HitTarget.minimum)
            .dropShadow(CapsuleShape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)))
            .background(colors.surface, CapsuleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    if (unlocking) {
                        waitForUpOrCancellation()
                        return@awaitEachGesture
                    }
                    holding = true
                    var completed = false
                    val hold = scope.launch {
                        progress.snapTo(0f)
                        val start = withFrameMillis { it }
                        while (true) {
                            val elapsed = withFrameMillis { it } - start
                            progress.snapTo(ExitHold.progress(elapsed))
                            if (ExitHold.isComplete(elapsed)) break
                        }
                        completed = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        unlock()
                    }
                    // `null` : doigt sorti du bouton ou geste repris par le défilement, comme un relâchement.
                    waitForUpOrCancellation()
                    holding = false
                    if (!completed) {
                        hold.cancel()
                        fallBack()
                        tooShort()
                    } else if (!unlocking) {
                        fallBack()
                    }
                }
            }
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick(label = null, action = accessibleAction)
                customActions = listOf(CustomAccessibilityAction(description, accessibleAction))
            }
            .padding(start = spacing.s, end = spacing.m, top = spacing.xs, bottom = spacing.xs),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            val track = colors.separator
            val fill = colors.primary
            Canvas(modifier = Modifier.size(ringSize)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                drawArc(
                    fill,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Icon(GiftIcons.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(ringSize * 0.5f))
        }
        Crossfade(
            targetState = holding || isUnlocking,
            animationSpec = if (reduceMotion) snap() else tween(150),
            label = "exitLabel",
        ) { active ->
            Text(
                stringResource(if (active) R.string.child_mode_exit_holding else R.string.child_mode_exit),
                style = GiftTheme.type.captionBold,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

// endregion

// region En-tête

/** Avatar 56 dp, « Les envies de Léa » (titre TalkBack), consigne arrondie, loutre amoureuse à droite. */
@Composable
private fun Header(state: ChildModeUiState, showMascot: Boolean, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val child = state.child
    val name = child?.firstName.orEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s), modifier = Modifier.weight(1f)) {
            if (child != null) {
                ChildAvatar(name = name, emoji = child.avatarEmoji, colorName = child.avatarColor, size = 56.dp, photoUrl = child.avatarUrl)
            }
            Text(
                stringResource(R.string.child_mode_title, name),
                style = GiftTheme.type.largeTitle,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.child_mode_subtitle),
                style = toyTitle.copy(fontWeight = FontWeight.Medium),
                color = colors.textSecondary,
            )
        }
        if (showMascot) {
            Image(
                painterResource(Illustration.MASCOT_LOVE.res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.widthIn(max = 112.dp).fillMaxWidth(0.32f).aspectRatio(1f),
            )
        }
    }
}

// endregion

// region Carte

/**
 * Grande carte d'une envie : photo carrée, titre arrondi sur deux lignes réservées (toutes les cartes d'une rangée ont
 * la même hauteur ; libre aux grandes polices), cœur de 52 dp en haut à droite. Posé : disque `surface` + cœur plein
 * `heart` qui rebondit, contour `heart` de 3 dp, gerbe de 6 cœurs. **Toute la carte** est la cible ; TalkBack lit
 * le titre, « Très envie » si le cœur est posé, et l'action (« poser un cœur » / « retirer le cœur »).
 */
@Composable
private fun ToyCard(item: ChildModeItem, burst: Int?, largeFont: Boolean, onToggle: () -> Unit) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    val outline by animateColorAsState(
        if (item.isFavorite) colors.heart else colors.heart.copy(alpha = 0f),
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "toyOutline",
    )
    val favoriteState = stringResource(R.string.child_mode_heart_state)
    val actionLabel = stringResource(if (item.isFavorite) R.string.child_mode_heart_remove else R.string.child_mode_heart_add)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fcClickable(onClick = onToggle, onClickLabel = actionLabel, shape = ToyCardShape, pressedScale = 0.94f)
            .clearAndSetSemantics {
                contentDescription = item.title
                selected = item.isFavorite
                if (item.isFavorite) stateDescription = favoriteState
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dropShadow(ToyCardShape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp)))
                .clip(ToyCardShape)
                .background(colors.surface)
                .border(3.dp, outline, ToyCardShape),
        ) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(colors.surface)) {
                RemoteImage(
                    url = item.imageUrl,
                    placeholderSeed = item.title,
                    contentScale = if (item.imageUrl == null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(if (item.imageUrl == null) 0.dp else spacing.s),
                )
            }
            Text(
                item.title,
                style = toyTitle,
                color = colors.textPrimary,
                minLines = if (largeFont) 1 else 2,
                maxLines = if (largeFont) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = spacing.m, end = spacing.m, top = spacing.s, bottom = spacing.m),
            )
        }
        HeartBadge(item.isFavorite, burst, Modifier.align(Alignment.TopEnd).padding(spacing.s))
    }
}

@Composable
private fun HeartBadge(isFavorite: Boolean, burst: Int?, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val side = 52.dp.scaledByFont()
    val bounce = rememberBounce(isFavorite)
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(side)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(side)
                .dropShadow(CircleShape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.08f), offset = DpOffset(0.dp, 2.dp)))
                .background(colors.surface, CircleShape),
        ) {
            Icon(
                if (isFavorite) GiftIcons.Heart else GiftIcons.HeartOutline,
                contentDescription = null,
                tint = if (isFavorite) colors.heart else colors.textSecondary,
                modifier = Modifier
                    .size(side * 0.5f)
                    .graphicsLayer {
                        scaleX = bounce.value
                        scaleY = bounce.value
                    },
            )
        }
        if (burst != null) key(burst) { HeartBurst() }
    }
}

/** Petite gerbe : 6 cœurs `heart` qui s'écartent de 48 dp en 0,7 s puis s'effacent. Décorative. */
@Composable
private fun HeartBurst() {
    val heart = GiftTheme.colors.heart
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    if (progress.value >= 1f) return
    val distance = with(LocalDensity.current) { 48.dp.toPx() }
    repeat(6) { index ->
        val angle = index / 6.0 * 2 * PI - PI / 2
        Icon(
            GiftIcons.Heart,
            contentDescription = null,
            tint = heart,
            modifier = Modifier
                .size(if (index % 2 == 0) 14.dp else 10.dp)
                .graphicsLayer {
                    val p = progress.value
                    translationX = (cos(angle) * distance * p).toFloat()
                    translationY = (sin(angle) * distance * p).toFloat()
                    val scale = 0.4f + 0.6f * p
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - p
                },
        )
    }
}

// endregion

// region Bandeau

/**
 * Bandeau d'astuce en bas (`primary` / `onPrimary`, rayon `card`) : glisse depuis le bas (fondu seul si animations
 * réduites), annoncé par TalkBack ; un toucher le ferme.
 */
@Composable
private fun HintBanner(hint: ChildModeHint?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val reduceMotion = GiftTheme.reduceMotion
    // Garde le dernier message pendant la sortie.
    var shown by remember { mutableStateOf(hint) }
    if (hint != null) shown = hint
    AnimatedVisibility(
        visible = hint != null,
        enter = if (reduceMotion) fadeIn(snap()) else slideInVertically(spring(dampingRatio = 0.8f, stiffness = 400f)) { it } + fadeIn(),
        exit = if (reduceMotion) fadeOut(snap()) else slideOutVertically(tween(200)) { it / 2 } + fadeOut(tween(200)),
        modifier = modifier,
    ) {
        val current = shown ?: return@AnimatedVisibility
        val text = stringResource(if (current == ChildModeHint.EXIT) R.string.child_mode_exit_hint else R.string.child_mode_save_failed)
        val dismiss = stringResource(R.string.child_mode_hint_dismiss)
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = spacing.xl, vertical = spacing.l)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .fcClickable(onClick = onDismiss, onClickLabel = dismiss, shape = GiftTheme.radius.cardShape, pressedScale = 1f)
                .background(colors.primary, GiftTheme.radius.cardShape)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = spacing.l, vertical = spacing.m),
        ) {
            Icon(
                if (current == ChildModeHint.EXIT) GiftIcons.Lock else GiftIcons.Warning,
                contentDescription = null,
                tint = colors.onPrimary,
            )
            Text(
                text,
                style = GiftTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// endregion

/**
 * Entrée « Mode enfant » en tête de la liste d'un de mes enfants (iOS : `ChildModeEntryCard`) : pastille rose avec la
 * loutre amoureuse, « Mode enfant », « Léa choisit ses envies en grand, sans prix ni statut. », chevron. Toute la carte
 * est le bouton.
 */
@Composable
fun ChildModeEntryCard(childName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val mascot = 52.dp.scaledByFont()
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fcClickable(onClick = onClick, onClickLabel = stringResource(R.string.child_mode_entry_action), shape = GiftTheme.radius.cardShape)
            .fcCard(padding = spacing.m)
            .semantics(mergeDescendants = true) { },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(mascot).background(colors.pastelPink, CircleShape),
        ) {
            Image(
                painterResource(Illustration.MASCOT_LOVE.res),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(spacing.xs),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.child_mode_entry_title), style = type.headline, color = colors.textPrimary)
            Text(stringResource(R.string.child_mode_entry_message, childName), style = type.caption, color = colors.textSecondary)
        }
        Icon(GiftIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary)
    }
}

// region Aperçus

private object ChildModePreviewData {
    private fun wish(title: String, favorite: Boolean = false) =
        ChildModeItem(GiftPreviewData.id(title), title, imageUrl = null, isFavorite = favorite)

    val state = ChildModeUiState(
        isLoading = false,
        child = GiftPreviewData.leo,
        items = listOf(
            wish("LEGO Technic McLaren F1", favorite = true),
            wish("Casque Sony WH-1000XM5"),
            wish("Nintendo Switch OLED"),
            wish("Maillot PSG 2025 avec son nom au dos"),
        ),
    )
}

private val previewActions = ChildModeActions({}, {}, {})

@GiftPreviews
@Composable
private fun ChildModeScreenPreview() = ScreenPreviewFrame(height = 900.dp) {
    ChildModeScreen(ChildModePreviewData.state, isAuthenticationAvailable = true, actions = previewActions)
}

@GiftPreviews
@Composable
private fun ChildModeSaveFailedPreview() = ScreenPreviewFrame {
    ChildModeScreen(ChildModePreviewData.state.copy(saveFailed = true), isAuthenticationAvailable = true, actions = previewActions)
}

@GiftPreviews
@Composable
private fun ChildModeEmptyPreview() = ScreenPreviewFrame {
    ChildModeScreen(ChildModeUiState(isLoading = false, child = GiftPreviewData.leo), isAuthenticationAvailable = false, actions = previewActions)
}

@GiftPreviews
@Composable
private fun ChildModeErrorPreview() = ScreenPreviewFrame {
    ChildModeScreen(
        ChildModeUiState(isLoading = false, child = GiftPreviewData.leo, error = GiftError.Network),
        isAuthenticationAvailable = false,
        actions = previewActions,
    )
}

@GiftPreviews
@Composable
private fun ChildModePartsPreview() = PreviewFrame {
    ChildModeEntryCard(childName = "Léo", onClick = {})
    ChildModeExitButton(isUnlocking = false, isAuthenticationAvailable = true, onTooShort = {}, onUnlock = {})
    ChildModeExitButton(isUnlocking = true, isAuthenticationAvailable = true, onTooShort = {}, onUnlock = {})
}

// endregion

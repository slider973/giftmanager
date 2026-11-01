package ch.jonathanlemaine.giftmanager.ui.santa

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.santa.SantaScript
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.BlockReason
import ch.jonathanlemaine.giftmanager.core.santa.SantaSession.Stage
import ch.jonathanlemaine.giftmanager.core.santa.SantaWishes
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.CountryFlag
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.gifts.openInBrowser
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/** Rappels de la session du Père Noël. */
data class SantaActions(
    val onClose: () -> Unit,
    val onSkipSpeaking: () -> Unit,
    val onReplay: () -> Unit,
    val onSubmitAnswer: (String) -> Unit,
    val onSkipAnswer: () -> Unit,
    val onToggleActivity: (String) -> Unit,
    val onApprove: (SantaSession.Suggestion) -> Unit,
    val onReject: (SantaSession.Suggestion) -> Unit,
    val onFinish: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Session du Père Noël, menée par le parent avec l'enfant à côté (iOS : `SantaSessionView` + `SantaSuggestionsView`).
 *
 * Plein écran, ✕ « Fermer ». Un rendu par étape :
 * - **dialogue** : médaillon de la loutre-lutin (ondes `primary` pendant la voix, figées si animations réduites),
 *   réplique dans une bulle (titre TalkBack, région live : la nouvelle réplique est lue), puis « Passer » /
 *   « Continuer » et « Réécouter », ou la zone de réponse du parent (aide, champ ou choix multiples, « Valider »,
 *   « Passer » si la question est facultative). Voix indisponible : rappel « Lis ce texte à voix haute », sans bloquer ;
 * - **pistes** : « Ce que Léo a demandé », une carte par boutique (« Voir la boutique », « Écarter », « Retenir ») ;
 * - **bilan** : mascotte qui fête, « 2 idées enregistrées », « Terminer » ;
 * - **impossible** : `mascot_thinking` et la raison, « Fermer ».
 */
@Composable
fun SantaScreen(state: SantaUiState, actions: SantaActions, modifier: Modifier = Modifier) {
    val reduceMotion = GiftTheme.reduceMotion
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.CLOSE,
                onNavigate = actions.onClose,
                title = stringResource(R.string.santa_title),
                navigationLabel = stringResource(R.string.santa_close),
            )
        },
    ) {
        AnimatedContent(
            targetState = state.stage.family(),
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120))
            },
            label = "santaStage",
            modifier = Modifier.fillMaxSize(),
        ) { family ->
            when (family) {
                StageFamily.Loading -> Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    GiftLoadingView(size = 96.dp)
                }
                StageFamily.Dialogue -> Dialogue(state, actions)
                StageFamily.Suggestions -> Suggestions(state, actions)
                StageFamily.Done -> Done(state.savedCount, actions.onClose)
                StageFamily.Blocked -> Blocked((state.stage as? Stage.Blocked)?.reason, actions.onClose)
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/** Familles d'étapes : on ne fond qu'en changeant de famille, pas à chaque réplique. */
private enum class StageFamily { Loading, Dialogue, Suggestions, Done, Blocked }

private fun Stage.family(): StageFamily = when (this) {
    Stage.NotStarted -> StageFamily.Loading
    is Stage.Speaking, is Stage.AwaitingAnswer -> StageFamily.Dialogue
    Stage.ReviewingSuggestions -> StageFamily.Suggestions
    Stage.Done -> StageFamily.Done
    is Stage.Blocked -> StageFamily.Blocked
}

private fun BlockReason.messageRes(): Int = when (this) {
    BlockReason.ChildMode -> R.string.santa_blocked_child_mode
    BlockReason.EmptyScript -> R.string.santa_blocked_empty_script
    BlockReason.NoStore -> R.string.santa_blocked_no_store
    BlockReason.NoWish -> R.string.santa_blocked_no_wish
}

// region Dialogue

@Composable
private fun Dialogue(state: SantaUiState, actions: SantaActions) {
    val line = state.currentLine ?: return
    val awaiting = state.stage is Stage.AwaitingAnswer
    val spacing = GiftTheme.spacing
    val scroll = rememberScrollState()
    // Nouvelle réplique : on la montre depuis le haut, même si le parent avait défilé jusqu'aux choix.
    LaunchedEffect(line.id) { scroll.scrollTo(0) }

    ScreenColumn(spacing = spacing.l, horizontalAlignment = Alignment.CenterHorizontally, scrollState = scroll) {
        SantaMedallion(speaking = state.isPlaying)
        StatusLine(awaiting = awaiting, playing = state.isPlaying, childName = state.childName)
        SpeechBubble(text = line.texte, lineKey = line.id)
        if (state.audioUnavailable) {
            // L'audio manque : le parent lit la réplique lui-même plutôt que de voir la session s'arrêter.
            FCNotice(icon = GiftIcons.Speaker, text = stringResource(R.string.santa_audio_unavailable))
        }
        if (awaiting) {
            AnswerArea(state, line, actions)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
            ) {
                PrimaryButton(
                    text = stringResource(if (state.isPlaying) R.string.santa_skip_speaking else R.string.santa_continue),
                    onClick = actions.onSkipSpeaking,
                    trailingIcon = GiftIcons.ChevronRight,
                )
                if (!state.audioUnavailable) {
                    LinkAction(GiftIcons.Replay, stringResource(R.string.santa_replay), actions.onReplay)
                }
            }
        }
    }
}

/**
 * Le « Père Noël » à l'écran : loutre au cadeau sur un disque `pastelMint` (le pastel de Noël). Pendant la voix,
 * deux ondes `primary` s'en échappent ; animations réduites : une onde fixe dit la même chose sans bouger.
 */
@Composable
private fun SantaMedallion(speaking: Boolean, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val disc = 80.dp.scaledByFont(1.25f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(disc * 1.5f).clearAndSetSemantics { },
    ) {
        if (speaking) SoundRings(animated = !GiftTheme.reduceMotion, discFraction = 1f / 1.5f)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(disc).clip(CircleShape).background(colors.pastelMint),
        ) {
            Image(
                painter = painterResource(Illustration.MASCOT_GIFT.res),
                contentDescription = null,
                modifier = Modifier.size(disc * 0.8f),
            )
        }
    }
}

/** Ondes qui partent du bord du disque ([discFraction] du diamètre) jusqu'au bord de la zone. Lues au dessin. */
@Composable
private fun SoundRings(animated: Boolean, discFraction: Float) {
    val color = GiftTheme.colors.primary
    val progress = if (animated) {
        val transition = rememberInfiniteTransition(label = "santaRings")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "santaRingsProgress",
        )
    } else {
        null
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val outer = size.minDimension / 2
        val inner = outer * discFraction
        val stroke = Stroke(width = 2.dp.toPx())
        val phases = progress?.value?.let { listOf(it, (it + 0.5f) % 1f) } ?: listOf(0.45f)
        phases.forEach { t ->
            val eased = FastOutSlowInEasing.transform(t)
            drawCircle(
                color = color,
                radius = inner + (outer - inner - stroke.width) * eased,
                alpha = if (progress == null) 0.3f else 0.45f * (1f - t),
                style = stroke,
            )
        }
    }
}

/** « Le Père Noël parle… » pendant la réplique (ou sa réécoute), « À Léo de répondre » quand c'est à l'enfant. */
@Composable
private fun StatusLine(awaiting: Boolean, playing: Boolean, childName: String) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        val santaTalks = !awaiting || playing
        if (santaTalks) {
            Icon(
                imageVector = GiftIcons.Speaker,
                contentDescription = null,
                tint = if (playing) colors.primary else colors.textSecondary,
                modifier = Modifier.size(type.captionBold.glyphSize(1.3f)),
            )
        }
        Text(
            text = if (santaTalks) stringResource(R.string.santa_status_speaking) else stringResource(R.string.santa_status_answer, childName),
            style = type.captionBold,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Bulle de la réplique : carte `surface` à pointe tournée vers le médaillon, texte arrondi `title` centré. Un seul
 * nœud stable (titre + région live) : TalkBack lit chaque nouvelle réplique sans qu'on déplace son focus. Fondu
 * d'entrée à chaque réplique, sauf animations réduites.
 */
@Composable
private fun SpeechBubble(text: String, lineKey: String) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val reduceMotion = GiftTheme.reduceMotion
    val appear = remember { Animatable(1f) }
    LaunchedEffect(lineKey) {
        if (reduceMotion) return@LaunchedEffect
        appear.snapTo(0f)
        appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    val tail = 12.dp
    Box(modifier = Modifier.fillMaxWidth().padding(top = tail)) {
        Box(modifier = Modifier.fillMaxWidth().fcCard(padding = GiftTheme.spacing.xl)) {
            Text(
                text = text,
                style = type.title.copy(lineHeight = type.title.fontSize * 1.3f),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = appear.value
                        translationY = (1f - appear.value) * 6.dp.toPx()
                    }
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
        // Pointe dessinée par-dessus la carte : elle masque le filet d'ombre de son bord haut.
        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -tail + 1.dp)
                .size(width = tail * 2, height = tail),
        ) {
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2, 0f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, colors.surface)
        }
    }
}

/** Zone du parent : aide, champ ou choix multiples, « Valider », et « Réécouter » / « Passer ». */
@Composable
private fun AnswerArea(state: SantaUiState, line: SantaScript.Line, actions: SantaActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val focusManager = LocalFocusManager.current
    val choices = line.choix.orEmpty()
    var answer by rememberSaveable(line.id) { mutableStateOf("") }
    // Même règle qu'iOS : un choix d'activité au moins (sauf question facultative), sinon un texte non vide.
    val canSubmit = when {
        choices.isNotEmpty() -> state.wishes.activites.isNotEmpty() || line.facultatif
        line.facultatif -> true
        else -> answer.isNotBlank()
    }
    val submit = {
        focusManager.clearFocus()
        actions.onSubmitAnswer(answer)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = Modifier.fillMaxWidth().padding(top = spacing.s),
    ) {
        line.aideParent?.let {
            Text(it, style = type.body.copy(lineHeight = type.body.lineHeight * 1.08f), color = colors.textSecondary)
        }
        if (choices.isNotEmpty()) {
            ChoiceGrid(choices, selected = state.wishes.activites, onToggle = actions.onToggleActivity)
        } else {
            FCTextField(
                label = stringResource(R.string.santa_answer_label),
                value = answer,
                onValueChange = { answer = it },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() else focusManager.clearFocus() }),
            )
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PrimaryButton(
            text = stringResource(R.string.santa_answer_send),
            onClick = submit,
            leadingIcon = GiftIcons.Checkmark,
            enabled = canSubmit,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            if (!state.audioUnavailable) LinkAction(GiftIcons.Replay, stringResource(R.string.santa_replay), actions.onReplay)
            if (line.facultatif) {
                LinkAction(
                    icon = GiftIcons.ArrowForward,
                    text = stringResource(R.string.santa_answer_skip),
                    onClick = {
                        focusManager.clearFocus()
                        actions.onSkipAnswer()
                    },
                )
            }
        }
    }
}

/**
 * Choix multiples (l'enfant montre, le parent touche) : grille de tuiles de largeur égale, 140 dp minimum par
 * colonne (grandit avec la police : une seule colonne aux tailles d'accessibilité). Chaque tuile est une case à
 * cocher TalkBack ; cochée = `primary` + coche, jamais la couleur seule.
 */
@Composable
private fun ChoiceGrid(choices: List<SantaScript.Choice>, selected: Set<String>, onToggle: (String) -> Unit) {
    val gap = GiftTheme.spacing.s
    val minTile = 140.dp.scaledByFont(2f)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = ((maxWidth + gap) / (minTile + gap)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            choices.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEach { choice ->
                        ChoiceTile(
                            label = choice.libelle,
                            isSelected = choice.cle in selected,
                            onToggle = { onToggle(choice.cle) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceTile(label: String, isSelected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val shape = GiftTheme.radius.thumbShape
    val foreground = if (isSelected) colors.onPrimary else colors.textPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = modifier
            .fcClickable(onClick = onToggle, role = Role.Checkbox, shape = shape)
            .semantics { toggleableState = ToggleableState(isSelected) }
            .heightIn(min = HitTarget.minimum.scaledByFont())
            .background(if (isSelected) colors.primary else colors.surface, shape)
            .then(if (isSelected) Modifier else Modifier.border(1.dp, colors.separator, shape))
            .padding(horizontal = GiftTheme.spacing.m, vertical = GiftTheme.spacing.s),
    ) {
        Icon(
            imageVector = if (isSelected) GiftIcons.Check else GiftIcons.CircleOutline,
            contentDescription = null,
            tint = if (isSelected) colors.onPrimary else colors.textSecondary,
            modifier = Modifier.size(type.callout.glyphSize(1.2f)),
        )
        Text(
            text = label,
            style = type.callout.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = foreground,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Action secondaire en lien `primary` avec icône (« Réécouter », « Passer ») : cible ≥ 48 dp. */
@Composable
private fun LinkAction(icon: ImageVector, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val type = GiftTheme.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        modifier = modifier
            .fcClickable(onClick = onClick, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
            .heightIn(min = HitTarget.minimum)
            .padding(horizontal = GiftTheme.spacing.s),
    ) {
        Icon(icon, contentDescription = null, tint = GiftTheme.colors.primary, modifier = Modifier.size(type.callout.glyphSize(1.2f)))
        Text(text, style = type.callout.copy(fontWeight = FontWeight.Medium), color = GiftTheme.colors.primary)
    }
}

// endregion

// region Pistes

@Composable
private fun Suggestions(state: SantaUiState, actions: SantaActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val context = LocalContext.current
    ScreenColumn(spacing = spacing.l) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(R.string.santa_suggestions_title, state.childName),
                style = type.title,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            if (state.wishes.enviePrincipale.isNotBlank()) {
                Text(stringResource(R.string.santa_suggestions_wish, state.wishes.enviePrincipale), style = type.body, color = colors.textSecondary)
            }
            Text(
                text = stringResource(R.string.santa_suggestions_hint),
                style = type.caption,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        state.suggestions.forEach { suggestion ->
            androidx.compose.runtime.key(suggestion.id) {
                SuggestionCard(
                    suggestion = suggestion,
                    childName = state.childName,
                    isSaving = state.savingId == suggestion.id,
                    isBusy = state.savingId != null,
                    onOpen = { openInBrowser(context, suggestion.url) },
                    onReject = { actions.onReject(suggestion) },
                    onApprove = { actions.onApprove(suggestion) },
                )
            }
        }
        OutlinePill(
            text = stringResource(R.string.santa_finish),
            onClick = actions.onFinish,
            modifier = Modifier.fillMaxWidth().padding(top = spacing.m),
        )
    }
}

/**
 * Une piste : boutique + drapeau et requête (un nœud TalkBack), lien « Voir la boutique », puis la décision
 * « Écarter » / « Retenir » côte à côte (empilées dès 150 % de police). Pendant un enregistrement, toutes les
 * « Retenir » sont désactivées ; celle en cours tourne.
 */
@Composable
private fun SuggestionCard(
    suggestion: SantaSession.Suggestion,
    childName: String,
    isSaving: Boolean,
    isBusy: Boolean,
    onOpen: () -> Unit,
    onReject: () -> Unit,
    onApprove: () -> Unit,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val stacked = LocalDensity.current.fontScale >= 1.5f
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = Modifier.fillMaxWidth().fcCard(padding = spacing.l),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.semantics(mergeDescendants = true) { },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(suggestion.storeName, style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f, fill = false))
                CountryFlag(suggestion.store.country, style = type.headline)
            }
            Text(suggestion.query, style = type.body, color = colors.textSecondary)
        }
        LinkAction(
            icon = GiftIcons.OpenExternal,
            text = stringResource(R.string.santa_suggestion_open),
            onClick = onOpen,
            modifier = Modifier.offset(x = -spacing.s),
        )
        val reject: @Composable (Modifier) -> Unit = { m ->
            OutlinePill(
                text = stringResource(R.string.santa_suggestion_reject),
                icon = GiftIcons.Close,
                onClick = onReject,
                contentDescription = stringResource(R.string.santa_suggestion_reject_a11y),
                modifier = m,
            )
        }
        val approve: @Composable (Modifier) -> Unit = { m ->
            FilledPill(
                text = stringResource(R.string.santa_suggestion_approve),
                icon = GiftIcons.Checkmark,
                onClick = onApprove,
                isLoading = isSaving,
                enabled = !isBusy,
                contentDescription = stringResource(R.string.santa_suggestion_approve_a11y, childName),
                modifier = m,
            )
        }
        if (stacked) {
            approve(Modifier.fillMaxWidth())
            reject(Modifier.fillMaxWidth())
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                reject(Modifier.weight(1f).fillMaxHeight())
                approve(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Pilule bordée `separator`, texte `primary` (iOS : `.bordered`). */
@Composable
private fun OutlinePill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s, Alignment.CenterHorizontally),
        modifier = modifier
            .fcClickable(onClick = onClick)
            .heightIn(min = HitTarget.minimum)
            .border(1.dp, colors.separator, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.l, vertical = GiftTheme.spacing.s)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = colors.primary, modifier = Modifier.size(type.callout.glyphSize(1.1f))) }
        // Libellé visible remplacé, pour TalkBack, par [contentDescription] quand il est fourni (« Écarter cette piste »).
        Text(
            text = text,
            style = type.callout.copy(fontWeight = FontWeight.Medium),
            color = colors.primary,
            textAlign = TextAlign.Center,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics { } else Modifier,
        )
    }
}

/** Pilule pleine `primary` compacte (« Retenir »), avec indicateur pendant l'enregistrement. */
@Composable
private fun FilledPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    isLoading: Boolean,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val loading = stringResource(R.string.loading)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fcClickable(onClick = onClick, enabled = enabled)
            .alpha(if (enabled || isLoading) 1f else 0.45f)
            .heightIn(min = HitTarget.minimum)
            .background(colors.primary, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.l, vertical = GiftTheme.spacing.s)
            .semantics {
                this.contentDescription = contentDescription
                if (isLoading) stateDescription = loading
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
            modifier = Modifier.alpha(if (isLoading) 0f else 1f).clearAndSetSemantics { },
        ) {
            Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(type.callout.glyphSize(1.1f)))
            Text(text, style = type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.onPrimary, textAlign = TextAlign.Center)
        }
        if (isLoading) CircularProgressIndicator(color = colors.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
}

// endregion

// region Bilan et impossibilité

@Composable
private fun Done(savedCount: Int, onClose: () -> Unit) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    ScreenColumn(spacing = spacing.l, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(spacing.xxl))
        Image(
            painter = painterResource(if (savedCount > 0) Illustration.MASCOT_CELEBRATE.res else Illustration.MASCOT_GIFT.res),
            contentDescription = null,
            modifier = Modifier.size(minOf(180.dp.scaledByFont(1.45f), 260.dp)),
        )
        Text(
            text = if (savedCount > 0) pluralStringResource(R.plurals.santa_done_saved, savedCount, savedCount) else stringResource(R.string.santa_done_none),
            style = GiftTheme.type.title,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        PrimaryButton(
            text = stringResource(R.string.santa_done_action),
            onClick = onClose,
            modifier = Modifier.widthIn(max = 320.dp).padding(top = spacing.s),
        )
    }
}

@Composable
private fun Blocked(reason: BlockReason?, onClose: () -> Unit) {
    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyStateView(
            illustration = Illustration.MASCOT_THINKING,
            title = stringResource(R.string.santa_blocked_title),
            message = reason?.let { stringResource(it.messageRes()) }.orEmpty(),
            actionLabel = stringResource(R.string.santa_close),
            onAction = onClose,
        )
    }
}

// endregion

// region Aperçus

private val previewLines = listOf(
    SantaScript.Line(
        id = "accueil",
        texte = "Ho ho ho ! Bonjour mon petit ! C'est le Père Noël qui te parle, depuis mon atelier tout là-haut dans le Grand Nord.",
    ),
    SantaScript.Line(
        id = "question_envie",
        texte = "S'il y avait un cadeau, un seul, qui te ferait vraiment très plaisir cette année, ce serait quoi ?",
        attendReponse = true,
        champ = "envie_principale",
        aideParent = "Écris ce que l'enfant demande, avec ses mots à lui.",
    ),
    SantaScript.Line(
        id = "question_activite",
        texte = "Est-ce que tu préfères construire des choses, jouer dehors, dessiner, ou écouter des histoires ?",
        attendReponse = true,
        champ = "activite",
        choix = listOf(
            SantaScript.Choice("construire", "Construire, bricoler"),
            SantaScript.Choice("dehors", "Jouer dehors, bouger"),
            SantaScript.Choice("creatif", "Dessiner, créer"),
            SantaScript.Choice("histoires", "Histoires, livres"),
            SantaScript.Choice("vehicules", "Voitures, trains, avions"),
        ),
        aideParent = "Choisis ce qui correspond le mieux. Tu peux en sélectionner plusieurs.",
        facultatif = true,
    ),
)

private val previewActions = SantaActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

@Composable
private fun SantaPreview(state: SantaUiState) = ScreenPreviewFrame {
    SantaScreen(state.copy(childName = "Léo"), previewActions)
}

@GiftPreviews
@Composable
private fun SantaSpeakingPreview() =
    SantaPreview(SantaUiState(stage = Stage.Speaking(0), currentLine = previewLines[0], isPlaying = true))

@GiftPreviews
@Composable
private fun SantaSpeakingNoAudioPreview() =
    SantaPreview(SantaUiState(stage = Stage.Speaking(0), currentLine = previewLines[0], audioUnavailable = true))

@GiftPreviews
@Composable
private fun SantaAnswerTextPreview() =
    SantaPreview(SantaUiState(stage = Stage.AwaitingAnswer(1), currentLine = previewLines[1]))

@GiftPreviews
@Composable
private fun SantaAnswerChoicesPreview() = SantaPreview(
    SantaUiState(
        stage = Stage.AwaitingAnswer(2),
        currentLine = previewLines[2],
        wishes = SantaWishes(activites = setOf("dehors", "vehicules")),
    ),
)

@GiftPreviews
@Composable
private fun SantaSuggestionsPreview() {
    val galaxus = StoreCatalog.SearchableStore("galaxus.ch", "Galaxus", "CH", "https://www.galaxus.ch/fr/search?q={q}")
    val fcw = StoreCatalog.SearchableStore("franz-carl-weber.ch", "Franz Carl Weber", "CH", "https://www.franz-carl-weber.ch/fr/search?q={q}")
    val first = SantaSession.Suggestion(query = "trottinette rouge", store = fcw, url = "https://www.franz-carl-weber.ch")
    SantaPreview(
        SantaUiState(
            stage = Stage.ReviewingSuggestions,
            wishes = SantaWishes(enviePrincipale = "une trottinette", preferences = "rouge"),
            suggestions = listOf(
                first,
                SantaSession.Suggestion(query = "trottinette rouge", store = galaxus, url = "https://www.galaxus.ch"),
            ),
            savingId = first.id,
        ),
    )
}

@GiftPreviews
@Composable
private fun SantaDonePreview() = SantaPreview(SantaUiState(stage = Stage.Done, savedCount = 2))

@GiftPreviews
@Composable
private fun SantaBlockedPreview() = SantaPreview(SantaUiState(stage = Stage.Blocked(BlockReason.NoWish)))

// endregion

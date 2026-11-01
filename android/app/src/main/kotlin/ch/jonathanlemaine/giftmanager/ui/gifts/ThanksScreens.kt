package ch.jonathanlemaine.giftmanager.ui.gifts

import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.core.gifts.ThanksLogic
import ch.jonathanlemaine.giftmanager.core.model.ThanksNote
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rappels de la rédaction d'un merci. */
data class ThanksComposerActions(
    val onClose: () -> Unit,
    val onMessageChange: (String) -> Unit,
    /** Photo choisie dans la galerie ; le redimensionnement en JPEG est fait par l'appelant. */
    val onPhotoPicked: (Uri) -> Unit,
    val onPhotoRemoved: () -> Unit,
    val onSend: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Rédaction d'un merci (iOS : `ThanksComposerView`), formulaire plein écran fermé par ✕ « Annuler ».
 *
 * En-tête du cadeau (vignette, titre, « Reçu par Léa »), rappel de confidentialité, message prérempli avec compteur
 * « n / 1000 », photo facultative (Photo Picker, sans autorisation), erreur d'envoi **dans** le formulaire, puis
 * « Envoyer le merci ». Une fois parti : « Merci envoyé ! » (fondu, retour haptique) et « Terminer ».
 * Le contenu défile aux grandes polices et remonte au-dessus du clavier.
 */
@Composable
fun ThanksComposerScreen(state: ThanksComposerUiState, actions: ThanksComposerActions, modifier: Modifier = Modifier) {
    val reduceMotion = GiftTheme.reduceMotion
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.sent) { if (state.sent) haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.CLOSE,
                onNavigate = actions.onClose,
                title = stringResource(R.string.thanks_composer_title),
            )
        },
    ) {
        val phase = when {
            state.isLoading -> ComposerPhase.LOADING
            state.sent -> ComposerPhase.SENT
            state.item == null -> ComposerPhase.FAILED
            else -> ComposerPhase.FORM
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                if (reduceMotion) fadeIn(snap()) togetherWith fadeOut(snap())
                else fadeIn(tween(250)) togetherWith fadeOut(tween(150))
            },
            label = "thanksComposer",
        ) { current ->
            when (current) {
                ComposerPhase.LOADING -> GiftLoadingView(modifier = Modifier.fillMaxSize().padding(GiftTheme.spacing.xxl))
                ComposerPhase.SENT -> SentState(state.item?.title.orEmpty(), actions.onClose)
                ComposerPhase.FAILED -> ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyStateView(
                        illustration = Illustration.MASCOT_THINKING,
                        title = stringResource(R.string.load_failed_title),
                        message = stringResource(state.error?.messageRes ?: R.string.load_failed_message),
                        actionLabel = stringResource(R.string.add_sheet_close),
                        onAction = actions.onClose,
                    )
                }
                ComposerPhase.FORM -> ComposerForm(state, actions)
            }
        }
    }
    // L'échec d'envoi s'affiche dans le formulaire (comme iOS) ; seul un échec de chargement n'a pas de place à l'écran,
    // et il est déjà dit par l'état d'échec : aucune alerte en plus.
}

private enum class ComposerPhase { LOADING, FORM, SENT, FAILED }

@Composable
private fun ComposerForm(state: ThanksComposerUiState, actions: ThanksComposerActions) {
    val spacing = GiftTheme.spacing
    val item = state.item ?: return
    ScreenColumn(spacing = spacing.xl) {
        GiftHeader(item.title, item.imageUrl, state.childName)
        FCNotice(GiftIcons.Lock, stringResource(R.string.thanks_privacy_notice), tone = FCNoticeTone.SURPRISE)
        MessageField(state.message, state.length, state.isTooLong, actions.onMessageChange)
        PhotoSection(state.photo, actions.onPhotoPicked, actions.onPhotoRemoved)
        state.error?.let { error ->
            FCNotice(
                GiftIcons.Warning,
                stringResource(error.messageRes),
                tone = FCNoticeTone.WARNING,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        PrimaryButton(
            text = stringResource(R.string.thanks_send),
            onClick = actions.onSend,
            leadingIcon = GiftIcons.Send,
            isLoading = state.isSending,
            enabled = state.canSend,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Vignette 56 dp, titre (2 lignes) et « Reçu par Léa » : un seul nœud TalkBack. */
@Composable
private fun GiftHeader(title: String, imageUrl: String?, childName: String) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
    ) {
        RemoteImage(
            url = imageUrl,
            placeholderSeed = title,
            modifier = Modifier.size(56.dp).clip(GiftTheme.radius.thumbShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), modifier = Modifier.weight(1f)) {
            Text(title, style = type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (childName.isNotEmpty()) {
                Text(stringResource(R.string.thanks_received_by, childName), style = type.caption, color = colors.textSecondary)
            }
        }
    }
}

/**
 * « Ton message » : champ multiligne `surface` (≥ 140 dp, grandit avec la police), bordure `primary` au focus,
 * compteur « n / 1000 » tabulaire en dessous, `takenFg` semi-gras au-delà de la limite et lu en phrase par TalkBack.
 */
@Composable
private fun MessageField(message: String, length: Int, isTooLong: Boolean, onChange: (String) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            isTooLong -> colors.takenFg
            focused -> colors.primary
            else -> colors.separator
        },
        tween(150),
        label = "messageBorder",
    )
    val label = stringResource(R.string.thanks_message_label)
    val spokenCounter = stringResource(R.string.thanks_counter_spoken, length, ThanksLogic.MAX_LENGTH)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(label, style = type.captionBold, color = colors.textSecondary, modifier = Modifier.clearAndSetSemantics { })
        BasicTextField(
            value = message,
            onValueChange = onChange,
            textStyle = type.body.copy(color = colors.textPrimary, lineHeight = type.body.lineHeight * 1.15f),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            interactionSource = source,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp.scaledByFont())
                .background(colors.surface, shape)
                .border(if (focused || isTooLong) 1.5.dp else 1.dp, borderColor, shape)
                .padding(horizontal = spacing.m, vertical = spacing.m)
                .semantics { contentDescription = label },
        )
        Text(
            text = stringResource(R.string.thanks_counter, length, ThanksLogic.MAX_LENGTH),
            style = (if (isTooLong) type.captionBold else type.caption).tabular(),
            color = if (isTooLong) colors.takenFg else colors.textSecondary,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = spokenCounter },
        )
    }
}

/**
 * « Photo (facultatif) » : grand bouton pointillé « Ajouter une photo » qui ouvre le Photo Picker ; une fois choisie,
 * aperçu de 200 dp et croix « Retirer la photo » (cible 48 dp sur pastille `surface`).
 */
@Composable
private fun PhotoSection(photo: ByteArray?, onPicked: (Uri) -> Unit, onRemove: () -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(
            stringResource(R.string.thanks_photo_label),
            style = type.captionBold,
            color = colors.textSecondary,
            modifier = Modifier.clearAndSetSemantics { },
        )
        if (photo != null) {
            val image = rememberDecodedPhoto(photo)
            val attached = stringResource(R.string.thanks_photo_attached)
            Box(
                contentAlignment = Alignment.TopEnd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(GiftTheme.radius.cardShape)
                    .background(colors.pastelPeach),
            ) {
                image?.let {
                    Image(
                        bitmap = it,
                        contentDescription = attached,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                val remove = stringResource(R.string.thanks_photo_remove)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(spacing.xs)
                        .size(HitTarget.minimum)
                        .fcClickable(onClick = onRemove, shape = CircleShape, pressedScale = 0.9f)
                        .semantics { contentDescription = remove },
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(34.dp).background(colors.surface, CircleShape),
                    ) {
                        Icon(GiftIcons.Close, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            val shape = GiftTheme.radius.fieldShape
            val dash = colors.separator
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .fcClickable(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        shape = shape,
                    )
                    .heightIn(min = 72.dp)
                    .background(colors.surface, shape)
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        drawRoundRect(
                            color = dash,
                            topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(14.dp.toPx()),
                            style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                        )
                    }
                    .padding(horizontal = spacing.l, vertical = spacing.m),
            ) {
                Icon(GiftIcons.Camera, contentDescription = null, tint = colors.primary, modifier = Modifier.size(type.callout.glyphSize(1.3f)))
                Text(
                    stringResource(R.string.thanks_photo_add),
                    style = type.callout.copy(fontWeight = FontWeight.Medium),
                    color = colors.primary,
                )
            }
        }
    }
}

/** Décode la photo jointe (JPEG déjà redimensionné) hors du fil principal. */
@Composable
private fun rememberDecodedPhoto(bytes: ByteArray): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, bytes) {
        value = withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    return image
}

/** « Merci envoyé ! » : loutre amoureuse, message, « Terminer » ; annoncé par TalkBack. */
@Composable
private fun SentState(title: String, onDone: () -> Unit) {
    ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
        EmptyStateView(
            illustration = Illustration.MASCOT_LOVE,
            title = stringResource(R.string.thanks_sent_title),
            message = stringResource(R.string.thanks_sent_message, title),
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        )
        PrimaryButton(
            stringResource(R.string.thanks_done),
            onDone,
            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
        )
    }
}

/** Rappels de la boîte des remerciements. */
data class ThanksInboxActions(
    val onBack: () -> Unit,
    val onSetRevealed: (ThanksNote, Boolean) -> Unit,
    val onDismissError: () -> Unit,
    /** Tirer pour actualiser. */
    val onRefresh: () -> Unit = {},
)

/**
 * Remerciements reçus pour les cadeaux que j'ai offerts (iOS : `ThanksInboxView`). Une carte par merci : avatar,
 * « De Claire » (ou « Des parents de Léa »), « Pour « titre » · il y a 2 heures », message sélectionnable, photo,
 * puis l'interrupteur « Me faire connaître » (toute la ligne bascule). Tirer pour actualiser ; vide : loutre endormie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThanksInboxScreen(state: ThanksInboxUiState, actions: ThanksInboxActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 } }
    val refreshState = rememberPullToRefreshState()
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    val column = Modifier.widthIn(max = ScreenContentMaxWidth).fillMaxWidth()
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = stringResource(R.string.thanks_inbox_title),
                showDivider = scrolled,
            )
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
            LazyColumn(
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.l),
                contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.s, bottom = spacing.xxl + bottomInset),
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    !state.hasLoaded -> item(key = "loading") {
                        GiftLoadingView(size = 72.dp, label = stringResource(R.string.thanks_inbox_loading), modifier = column.padding(vertical = spacing.xxl))
                    }
                    state.isEmpty -> item(key = "empty") {
                        EmptyStateView(
                            illustration = Illustration.MASCOT_SLEEPING,
                            title = stringResource(R.string.thanks_inbox_empty_title),
                            message = stringResource(R.string.thanks_inbox_empty_message),
                            modifier = column,
                        )
                    }
                }
                items(state.notes, key = { it.id }) { note ->
                    ThanksCard(
                        note = note,
                        pending = note.id in state.pending,
                        onSetRevealed = { actions.onSetRevealed(note, it) },
                        modifier = column,
                    )
                }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
internal fun ThanksCard(note: ThanksNote, pending: Boolean, onSetRevealed: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val sender = note.senderName
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = modifier.fcCard()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { },
        ) {
            ChildAvatar(name = sender ?: note.childName, emoji = null, colorName = null, size = 40.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                Text(
                    if (sender != null) stringResource(R.string.thanks_from, sender) else stringResource(R.string.thanks_from_parents, note.childName),
                    style = type.headline,
                    color = colors.textPrimary,
                )
                Text(thanksSubtitle(note), style = type.caption, color = colors.textSecondary)
            }
        }
        SelectionContainer {
            Text(
                note.message,
                style = type.callout.copy(lineHeight = type.callout.lineHeight * 1.15f),
                color = colors.textPrimary,
            )
        }
        note.photoUrl?.let { url ->
            RemoteImage(
                url = url,
                placeholderSeed = note.title,
                contentDescription = stringResource(R.string.thanks_photo_description),
                modifier = Modifier.fillMaxWidth().height(220.dp).clip(GiftTheme.radius.thumbShape),
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.separator)
        RevealRow(revealed = note.revealed, childName = note.childName, enabled = !pending, onChange = onSetRevealed)
    }
}

/** « Pour « Lego » · il y a 2 heures » ; la date relative suit la langue de l'appareil. */
@Composable
private fun thanksSubtitle(note: ThanksNote): String {
    val forGift = stringResource(R.string.thanks_for_gift, note.title)
    val date = note.createdDate ?: return forGift
    val relative = DateUtils.getRelativeTimeSpanString(date.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
    return "$forGift · $relative"
}

/**
 * « Me faire connaître » : toute la ligne bascule (`Role.Switch`), sous-titre selon l'état. Désactivée pendant
 * l'enregistrement. Interrupteur `secondary` comme le `.tint` iOS.
 */
@Composable
private fun RevealRow(revealed: Boolean, childName: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val source = remember { MutableInteractionSource() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HitTarget.minimum)
            .fcPressable(source, GiftTheme.radius.fieldShape, pressedScale = 1f)
            .toggleable(
                value = revealed,
                enabled = enabled,
                interactionSource = source,
                indication = null,
                role = Role.Switch,
                onValueChange = onChange,
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.thanks_reveal),
                style = type.callout.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
            )
            Text(
                if (revealed) stringResource(R.string.thanks_reveal_on, childName) else stringResource(R.string.thanks_reveal_off),
                style = type.caption,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = revealed,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onPrimary,
                checkedTrackColor = colors.secondary,
                checkedBorderColor = colors.secondary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.background,
                uncheckedBorderColor = colors.textSecondary,
                disabledCheckedThumbColor = colors.onPrimary,
                disabledCheckedTrackColor = colors.secondary.copy(alpha = 0.5f),
                disabledCheckedBorderColor = colors.secondary.copy(alpha = 0f),
                disabledUncheckedThumbColor = colors.textSecondary.copy(alpha = 0.5f),
                disabledUncheckedTrackColor = colors.background,
                disabledUncheckedBorderColor = colors.textSecondary.copy(alpha = 0.5f),
            ),
        )
    }
}

// region Aperçus

private object ThanksPreviewData {
    val item = GiftPreviewData.detailItem(status = null).copy(owned = true)
    val composer = ThanksComposerUiState(
        isLoading = false,
        item = item,
        childName = "Léo",
        message = ThanksLogic.defaultMessage(item.title),
    )
    val notes = listOf(
        ThanksNote(
            id = GiftPreviewData.id("t1"),
            itemId = GiftPreviewData.id("lego"),
            title = "LEGO Technic McLaren F1",
            childName = "Léo",
            senderName = "Claire",
            message = "Merci beaucoup pour « LEGO Technic McLaren F1 » ! Léo l'a monté en un après-midi et ne la lâche plus.",
            createdAt = "2026-10-31T16:20:00Z",
            revealed = false,
        ),
        ThanksNote(
            id = GiftPreviewData.id("t2"),
            itemId = GiftPreviewData.id("velo"),
            title = "Vélo 20 pouces Woom 4",
            childName = "Zoé",
            message = "Zoé a fait son premier tour du quartier sans les petites roues. Merci !",
            photoUrl = "https://example.com/photo.jpg",
            createdAt = "2026-10-20T09:00:00Z",
            revealed = true,
        ),
    )
}

private val composerPreviewActions = ThanksComposerActions({}, {}, {}, {}, {}, {})
private val inboxPreviewActions = ThanksInboxActions({}, { _, _ -> }, {})

@GiftPreviews
@Composable
private fun ThanksComposerPreview() = ScreenPreviewFrame(height = 1000.dp) {
    ThanksComposerScreen(ThanksPreviewData.composer, composerPreviewActions)
}

@GiftPreviews
@Composable
private fun ThanksComposerErrorPreview() = ScreenPreviewFrame(height = 1100.dp) {
    ThanksComposerScreen(
        ThanksPreviewData.composer.copy(message = "a".repeat(1012), error = GiftError.Network),
        composerPreviewActions,
    )
}

@GiftPreviews
@Composable
private fun ThanksComposerSentPreview() = ScreenPreviewFrame {
    ThanksComposerScreen(ThanksPreviewData.composer.copy(sent = true), composerPreviewActions)
}

@GiftPreviews
@Composable
private fun ThanksInboxPreview() = ScreenPreviewFrame(height = 1100.dp) {
    ThanksInboxScreen(
        ThanksInboxUiState(notes = ThanksPreviewData.notes, hasLoaded = true, pending = setOf(GiftPreviewData.id("t2"))),
        inboxPreviewActions,
    )
}

@GiftPreviews
@Composable
private fun ThanksInboxEmptyPreview() = ScreenPreviewFrame {
    ThanksInboxScreen(ThanksInboxUiState(hasLoaded = true), inboxPreviewActions)
}

@GiftPreviews
@Composable
private fun ThanksInboxLoadingPreview() = ScreenPreviewFrame {
    ThanksInboxScreen(ThanksInboxUiState(), inboxPreviewActions)
}

// endregion

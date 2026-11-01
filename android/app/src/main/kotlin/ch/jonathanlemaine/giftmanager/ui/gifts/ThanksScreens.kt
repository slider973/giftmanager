package ch.jonathanlemaine.giftmanager.ui.gifts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.gifts.ThanksLogic
import ch.jonathanlemaine.giftmanager.core.model.ThanksNote
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

// Écrans de câblage (#75) : fonctionnels et accessibles, mais sans travail de design. Le `ui-designer` les reprend.

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

/** Rédaction d'un merci (iOS : `ThanksComposerView`) : message prérempli, photo facultative, puis « Merci envoyé ! ». */
@Composable
fun ThanksComposerScreen(state: ThanksComposerUiState, actions: ThanksComposerActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(actions.onPhotoPicked) }
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
        when {
            state.isLoading -> GiftLoadingView(modifier = Modifier.fillMaxSize())
            state.sent -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                EmptyStateView(
                    illustration = Illustration.MASCOT_LOVE,
                    title = stringResource(R.string.thanks_sent_title),
                    message = stringResource(R.string.thanks_sent_message, state.item?.title.orEmpty()),
                )
                PrimaryButton(stringResource(R.string.thanks_done), actions.onClose)
            }
            else -> ScreenColumn {
                Text(state.item?.title.orEmpty(), style = GiftTheme.type.headline, color = colors.textPrimary)
                Text(stringResource(R.string.thanks_received_by, state.childName), style = GiftTheme.type.caption, color = colors.textSecondary)
                FCNotice(GiftIcons.Lock, stringResource(R.string.thanks_privacy_notice), tone = FCNoticeTone.SURPRISE)
                Text(stringResource(R.string.thanks_message_label), style = GiftTheme.type.captionBold, color = colors.textSecondary)
                BasicTextField(
                    value = state.message,
                    onValueChange = actions.onMessageChange,
                    textStyle = GiftTheme.type.body.copy(color = colors.textPrimary),
                    modifier = Modifier.fillMaxWidth().height(140.dp).fcCard(),
                )
                Text(
                    stringResource(R.string.thanks_counter, state.length, ThanksLogic.MAX_LENGTH),
                    style = GiftTheme.type.caption,
                    color = if (state.isTooLong) colors.takenFg else colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.thanks_photo_label), style = GiftTheme.type.captionBold, color = colors.textSecondary)
                if (state.photo != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.thanks_photo_attached), style = GiftTheme.type.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        TextLinkButton(stringResource(R.string.thanks_photo_remove), actions.onPhotoRemoved)
                    }
                } else {
                    TextLinkButton(
                        stringResource(R.string.thanks_photo_add),
                        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.thanks_send),
                    onClick = actions.onSend,
                    leadingIcon = GiftIcons.Mail,
                    isLoading = state.isSending,
                    enabled = state.canSend,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    GiftErrorDialog(state.error.takeUnless { state.isClosed }, actions.onDismissError)
}

/** Rappels de la boîte des remerciements. */
data class ThanksInboxActions(
    val onBack: () -> Unit,
    val onSetRevealed: (ThanksNote, Boolean) -> Unit,
    val onDismissError: () -> Unit,
)

/** Remerciements reçus pour les cadeaux que j'ai offerts (iOS : `ThanksInboxView`), avec « Me faire connaître ». */
@Composable
fun ThanksInboxScreen(state: ThanksInboxUiState, actions: ThanksInboxActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    TopBarScreen(
        modifier = modifier,
        topBar = {
            GiftTopBar(
                navigation = TopBarNavigation.BACK,
                onNavigate = actions.onBack,
                title = stringResource(R.string.thanks_inbox_title),
            )
        },
    ) {
        when {
            !state.hasLoaded -> GiftLoadingView(label = stringResource(R.string.thanks_inbox_loading), modifier = Modifier.fillMaxSize())
            state.isEmpty -> EmptyStateView(
                illustration = Illustration.MASCOT_SLEEPING,
                title = stringResource(R.string.thanks_inbox_empty_title),
                message = stringResource(R.string.thanks_inbox_empty_message),
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
                modifier = Modifier.fillMaxSize().padding(GiftTheme.spacing.xl),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s), modifier = Modifier.fillMaxWidth().fcCard()) {
                        Text(
                            note.senderName?.let { stringResource(R.string.thanks_from, it) }
                                ?: stringResource(R.string.thanks_from_parents, note.childName),
                            style = GiftTheme.type.headline,
                            color = colors.textPrimary,
                        )
                        Text(stringResource(R.string.thanks_for_gift, note.title), style = GiftTheme.type.caption, color = colors.textSecondary)
                        Text(note.message, style = GiftTheme.type.callout, color = colors.textPrimary)
                        note.photoUrl?.let { RemoteImage(it, placeholderSeed = note.title, modifier = Modifier.fillMaxWidth().height(220.dp)) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.thanks_reveal), style = GiftTheme.type.callout, color = colors.textPrimary)
                                Text(
                                    if (note.revealed) stringResource(R.string.thanks_reveal_on, note.childName)
                                    else stringResource(R.string.thanks_reveal_off),
                                    style = GiftTheme.type.caption,
                                    color = colors.textSecondary,
                                )
                            }
                            Switch(
                                checked = note.revealed,
                                onCheckedChange = { actions.onSetRevealed(note, it) },
                                enabled = note.id !in state.pending,
                            )
                        }
                    }
                }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

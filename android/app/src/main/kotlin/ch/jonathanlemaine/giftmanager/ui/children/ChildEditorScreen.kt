package ch.jonathanlemaine.giftmanager.ui.children

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.AvatarPalette
import ch.jonathanlemaine.giftmanager.ui.common.DateField
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.SettingsActionRow
import ch.jonathanlemaine.giftmanager.ui.common.SettingsDivider
import ch.jonathanlemaine.giftmanager.ui.common.SettingsSection
import ch.jonathanlemaine.giftmanager.ui.common.ToggleRow
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import java.time.LocalDate

/** Actions de [ChildEditorScreen], reliées à [ChildEditorViewModel]. */
data class ChildEditorActions(
    val onFirstNameChange: (String) -> Unit,
    val onHasBirthdateChange: (Boolean) -> Unit,
    val onBirthdateChange: (LocalDate) -> Unit,
    val onEmojiChange: (String) -> Unit,
    val onColorChange: (String) -> Unit,
    val onSave: () -> Unit,
    val onRequestDelete: () -> Unit,
    val onCancelDelete: () -> Unit,
    val onDelete: () -> Unit,
    val onDismissError: () -> Unit,
    val onClose: () -> Unit,
)

/** Colonnes de la grille d'émojis au plus (iOS : 6). */
private const val EMOJI_COLUMNS = 6

/** Diamètre d'une pastille de couleur (iOS : 36 pt), dans une cible de 48 dp. */
private val SwatchSize = 36.dp

/**
 * Ajout, modification et suppression d'un enfant ou d'une liste d'adulte (iOS : `ChildEditorView`).
 *
 * Formulaire plein écran (sans barre d'onglets) : ✕ « Annuler » + titre (« Nouvel enfant », « Nouvelle liste
 * d'adulte » ou le prénom enregistré). Avatar 96 dp mis à jour en direct, prénom, carte « Date de naissance »
 * (interrupteur + date bornée à aujourd'hui), carte « Avatar » (émojis, couleurs), CTA, puis suppression
 * isolée dans sa propre carte avec confirmation.
 */
@Composable
fun ChildEditorScreen(state: ChildEditorUiState, actions: ChildEditorActions, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    val focusManager = LocalFocusManager.current
    val title = state.savedFirstName
        ?: stringResource(if (state.isAdult) R.string.child_editor_title_new_adult else R.string.child_editor_title_new)
    TopBarScreen(
        modifier = modifier,
        topBar = { GiftTopBar(TopBarNavigation.CLOSE, onNavigate = actions.onClose, title = title) },
    ) {
        ScreenColumn {
            AvatarPreview(state)
            FCTextField(
                label = stringResource(if (state.isAdult) R.string.child_name_adult else R.string.child_name),
                value = state.firstName,
                onValueChange = actions.onFirstNameChange,
                leadingIcon = GiftIcons.Person,
                placeholder = stringResource(if (state.isAdult) R.string.child_name_prompt_adult else R.string.child_name_prompt),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.semantics { contentType = ContentType.PersonFirstName },
            )
            if (state.isAdult) {
                FCNotice(GiftIcons.EyeSlash, stringResource(R.string.child_adult_notice), tone = FCNoticeTone.SURPRISE)
            }
            BirthdateCard(state, actions)
            AvatarCard(state, actions)
            PrimaryButton(
                text = stringResource(
                    when {
                        state.isEditing -> R.string.child_save_edit
                        state.isAdult -> R.string.child_save_create_adult
                        else -> R.string.child_save_create
                    },
                ),
                onClick = {
                    focusManager.clearFocus()
                    actions.onSave()
                },
                leadingIcon = GiftIcons.Checkmark,
                isLoading = state.isSaving,
                enabled = state.canSave,
            )
            if (state.isEditing) {
                // Action destructive à l'écart du CTA, dans sa propre carte (comme « Supprimer mon compte »).
                SettingsSection(title = null, modifier = Modifier.padding(top = spacing.m)) {
                    SettingsActionRow(
                        text = stringResource(R.string.child_delete, state.savedFirstName.orEmpty()),
                        onClick = actions.onRequestDelete,
                        icon = GiftIcons.Delete,
                        color = GiftTheme.colors.takenFg,
                    )
                }
            }
        }
    }
    if (state.confirmDelete) {
        GiftAlertDialog(
            title = stringResource(R.string.child_delete_title, state.savedFirstName.orEmpty()),
            message = stringResource(R.string.child_delete_message),
            confirmLabel = stringResource(if (state.isAdult) R.string.child_delete_confirm_adult else R.string.child_delete_confirm_child),
            onConfirm = actions.onDelete,
            onDismiss = actions.onCancelDelete,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

/**
 * Avatar 96 dp qui suit la saisie (initiale du prénom tant qu'aucun émoji, couleur choisie). Petit rebond à
 * chaque changement d'émoji ou de couleur (fondu seul si animations réduites). Décoratif pour TalkBack : les
 * choix sont annoncés sur les contrôles eux-mêmes.
 */
@Composable
private fun AvatarPreview(state: ChildEditorUiState) {
    val reduceMotion = GiftTheme.reduceMotion
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().padding(top = GiftTheme.spacing.s).clearAndSetSemantics { },
    ) {
        AnimatedContent(
            targetState = state.emoji to state.colorName,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    (scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.85f) + fadeIn(tween(120))) togetherWith
                        fadeOut(tween(90))
                } using SizeTransform(clip = false)
            },
            label = "avatar",
        ) { (emoji, colorName) ->
            ChildAvatar(
                name = state.firstName.ifBlank { "?" },
                emoji = emoji,
                colorName = colorName,
                size = 96.dp,
                modifier = Modifier.dropShadow(CircleShape, Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.06f), offset = DpOffset(0.dp, 2.dp))),
            )
        }
    }
}

/** Carte « Date de naissance » : toute la ligne bascule ; la date apparaît dessous quand elle est active. */
@Composable
private fun BirthdateCard(state: ChildEditorUiState, actions: ChildEditorActions) {
    val reduceMotion = GiftTheme.reduceMotion
    SettingsSection(title = null) {
        ToggleRow(
            icon = GiftIcons.Cake,
            label = stringResource(R.string.child_birthdate_toggle),
            checked = state.hasBirthdate,
            onCheckedChange = actions.onHasBirthdateChange,
        )
        AnimatedVisibility(
            visible = state.hasBirthdate,
            enter = if (reduceMotion) fadeIn(snap()) else fadeIn(tween(200)) + expandVertically(tween(200)),
            exit = if (reduceMotion) fadeOut(snap()) else fadeOut(tween(120)) + shrinkVertically(tween(200)),
        ) {
            Column {
                SettingsDivider(inset = false)
                DateField(
                    label = stringResource(if (state.isAdult) R.string.child_birthdate_label_adult else R.string.child_birthdate_label),
                    date = state.birthdate,
                    onDateChange = actions.onBirthdateChange,
                    max = state.maxBirthdate,
                    modifier = Modifier.padding(GiftTheme.spacing.l),
                )
            }
        }
    }
}

/** Carte « Avatar » : grille d'émojis puis rangée de couleurs, deux groupes de boutons radio pour TalkBack. */
@Composable
private fun AvatarCard(state: ChildEditorUiState, actions: ChildEditorActions) {
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = Modifier.fillMaxWidth().fcCard()) {
        Text(
            text = stringResource(R.string.child_avatar_title),
            style = GiftTheme.type.headline,
            color = GiftTheme.colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        EmojiGrid(selected = state.emoji, colorName = state.colorName, onSelect = actions.onEmojiChange)
        Spacer(Modifier.height(spacing.xs))
        ColorSwatches(selected = state.colorName, onSelect = actions.onColorChange)
    }
}

/**
 * Grille d'émojis (iOS : 6 colonnes). Le nombre de colonnes s'adapte pour garder des cibles d'au moins
 * 48 dp sur les écrans étroits. Émoji choisi : disque de la couleur choisie + anneau `primary` de 2 dp.
 */
@Composable
private fun EmojiGrid(selected: String?, colorName: String, onSelect: (String) -> Unit) {
    val colors = GiftTheme.colors
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = (maxWidth / HitTarget.minimum).toInt().coerceIn(1, EMOJI_COLUMNS)
        Column(
            verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
            modifier = Modifier.selectableGroup(),
        ) {
            AvatarPalette.emojis.chunked(columns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { emoji ->
                        val isSelected = emoji == selected
                        val description = stringResource(R.string.child_avatar_a11y, emoji)
                        val source = remember { MutableInteractionSource() }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(HitTarget.minimum)
                                .selectable(
                                    selected = isSelected,
                                    interactionSource = source,
                                    indication = null,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(emoji) },
                                )
                                .clearAndSetSemantics {
                                    contentDescription = description
                                    this.selected = isSelected
                                },
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(HitTarget.minimum)
                                    .fcPressable(source, CircleShape, pressedScale = 0.9f)
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .background(colors.pastel(colorName) ?: colors.pastelMint, CircleShape)
                                                .border(2.dp, colors.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                Text(text = emoji, style = TextStyle(fontSize = fixedSp(26.dp)))
                            }
                        }
                    }
                    // Dernière rangée incomplète : cases vides pour garder la grille alignée.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Pastilles de couleur (rose, menthe, bleu, pêche, lavande) : 36 dp dans 48 dp, anneau `primary` autour du choix. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatches(selected: String, onSelect: (String) -> Unit) {
    val colors = GiftTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier.selectableGroup(),
    ) {
        AvatarPalette.colors.forEach { name ->
            val isSelected = name == selected
            val description = stringResource(R.string.child_color_a11y, colorLabel(name))
            val source = remember { MutableInteractionSource() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HitTarget.minimum)
                    .fcPressable(source, CircleShape, pressedScale = 0.9f)
                    .selectable(
                        selected = isSelected,
                        interactionSource = source,
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(name) },
                    )
                    .clearAndSetSemantics {
                        contentDescription = description
                        this.selected = isSelected
                    },
            ) {
                if (isSelected) {
                    Box(Modifier.size(SwatchSize + 8.dp).border(2.5.dp, colors.primary, CircleShape))
                }
                Box(
                    Modifier
                        .size(SwatchSize)
                        .background(colors.pastel(name) ?: colors.pastelMint, CircleShape)
                        .border(1.dp, colors.separator, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun colorLabel(name: String): String = stringResource(
    when (name) {
        "pastelPink" -> R.string.child_color_pink
        "pastelMint" -> R.string.child_color_mint
        "pastelBlue" -> R.string.child_color_blue
        "pastelPeach" -> R.string.child_color_peach
        else -> R.string.child_color_lavender
    },
)

/** Émoji calé sur une forme de taille fixe : il ne suit pas l'échelle de police (la cible reste 48 dp). */
@Composable
private fun fixedSp(size: Dp) = with(LocalDensity.current) { size.toSp() }

// region Aperçus

private val previewActions = ChildEditorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
private val previewToday = LocalDate.of(2026, 11, 1)

@GiftPreviews
@Composable
private fun ChildEditorCreatePreview() = ScreenPreviewFrame(height = 1100.dp) {
    ChildEditorScreen(
        ChildEditorUiState(firstName = "Léo", birthdate = LocalDate.of(2018, 3, 12), maxBirthdate = previewToday, emoji = "🦊", colorName = "pastelPeach"),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun ChildEditorAdultPreview() = ScreenPreviewFrame(height = 1100.dp) {
    ChildEditorScreen(
        ChildEditorUiState(isAdult = true, hasBirthdate = false, emoji = null, colorName = "pastelLavender", maxBirthdate = previewToday),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun ChildEditorEditPreview() = ScreenPreviewFrame(height = 1200.dp) {
    ChildEditorScreen(
        ChildEditorUiState(
            isEditing = true,
            savedFirstName = "Emma",
            firstName = "Emma",
            birthdate = LocalDate.of(2020, 6, 2),
            maxBirthdate = previewToday,
            emoji = "🦄",
            colorName = "pastelPink",
            isSaving = true,
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun ChildEditorDeletePreview() = ScreenPreviewFrame {
    ChildEditorScreen(
        ChildEditorUiState(isEditing = true, savedFirstName = "Emma", firstName = "Emma", maxBirthdate = previewToday, confirmDelete = true),
        previewActions,
    )
}

// endregion

package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PotProgressCard
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal

/** Rappels de la feuille de participation à une cagnotte. */
data class JoinPotActions(
    val onDismiss: () -> Unit,
    val onAmountChange: (String) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onSuggestion: (BigDecimal) -> Unit,
    val onSave: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Feuille « Participer à une cagnotte » / « Ma part » (#35, iOS : `JoinPotView`) : le cadeau, la progression,
 * le montant en grand avec sa devise (imposée par une cagnotte ouverte, sinon au choix), des suggestions en
 * pastilles (« Le reste : 40 CHF »), le rappel de confidentialité et le bouton. L'erreur s'affiche dans la
 * feuille, sous le montant, plutôt qu'en alerte par-dessus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinPotSheet(state: JoinPotUiState, actions: JoinPotActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        JoinPotContent(state, actions)
    }
}

/** Contenu de la feuille, séparé pour les aperçus. */
@Composable
internal fun JoinPotContent(state: JoinPotUiState, actions: JoinPotActions) {
    val spacing = GiftTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (state.isUpdate) R.string.join_pot_title_update else R.string.join_pot_title_new),
                style = GiftTheme.type.title,
                color = GiftTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TopBarTextAction(stringResource(R.string.cancel), onClick = actions.onDismiss)
        }
        val item = state.item
        if (item == null) {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GiftLoadingView(size = 56.dp) }
            }
            state.error?.let { FCNotice(GiftIcons.Warning, stringResource(it.messageRes), tone = FCNoticeTone.WARNING) }
            return@Column
        }
        GiftHeader(item.title, item.imageUrl, state.childName)
        state.progress?.let { progress ->
            PotProgressCard(progress, myShareText = item.myContribution?.let { Money.format(it, progress.currency) })
        }
        AmountSection(state, actions)
        FCNotice(
            GiftIcons.EyeSlash,
            state.childName?.let { stringResource(R.string.join_pot_privacy, it) } ?: stringResource(R.string.join_pot_privacy_unknown_child),
            tone = FCNoticeTone.SURPRISE,
        )
        PrimaryButton(
            text = stringResource(if (state.isUpdate) R.string.join_pot_submit_update else R.string.join_pot_submit_new),
            onClick = actions.onSave,
            leadingIcon = GiftIcons.Group,
            isLoading = state.isSaving,
            enabled = state.canSave,
        )
    }
}

/** Vignette 56 dp, titre et « Pour Léo » : un seul nœud TalkBack. */
@Composable
private fun GiftHeader(title: String, imageUrl: String?, childName: String?) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        RemoteImage(
            url = imageUrl,
            placeholderSeed = title,
            modifier = Modifier.size(56.dp).clip(GiftTheme.radius.thumbShape).clearAndSetSemantics { },
        )
        Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), modifier = Modifier.weight(1f)) {
            Text(title, style = type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            childName?.let { Text(stringResource(R.string.join_pot_for_child, it), style = type.caption, color = colors.textSecondary) }
        }
    }
}

/**
 * Montant en `title` à chiffres tabulaires dans un champ `surface` (bordure `primary` au focus, `takenFg` si
 * illisible), devise à droite. Dessous : erreur de saisie, ou rappel de la devise imposée ; erreur d'envoi ;
 * suggestions.
 */
@Composable
private fun AmountSection(state: JoinPotUiState, actions: JoinPotActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val label = stringResource(if (state.isUpdate) R.string.join_pot_amount_label_update else R.string.join_pot_amount_label_new)
    val invalidText = stringResource(R.string.join_pot_invalid)
    val emptyText = stringResource(R.string.join_pot_amount_empty)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val inPreview = LocalInspectionMode.current
    LaunchedEffect(Unit) { if (!state.isUpdate && !inPreview) focusRequester.requestFocus() }
    val borderColor = when {
        state.showsInvalid -> colors.takenFg
        focused -> colors.primary
        else -> colors.separator
    }
    val shape = GiftTheme.radius.fieldShape

    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        FieldLabel(label, Modifier.clearAndSetSemantics { })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .background(colors.surface, shape)
                .border(if (focused || state.showsInvalid) 1.5.dp else 1.dp, borderColor, shape)
                .padding(start = spacing.l, end = spacing.s),
        ) {
            val textStyle = type.title.tabular().copy(color = colors.textPrimary)
            BasicTextField(
                value = state.amountText,
                onValueChange = actions.onAmountChange,
                textStyle = textStyle,
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                interactionSource = source,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = label
                        stateDescription = if (state.amountText.isEmpty()) emptyText else "${state.amountText} ${state.currency}"
                        if (state.showsInvalid) error(invalidText)
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.amountText.isEmpty()) {
                            Text(stringResource(R.string.join_pot_amount_hint), style = textStyle, color = colors.textSecondary)
                        }
                        inner()
                    }
                },
            )
            CurrencyControl(state, actions)
        }
        AnimatedVisibility(
            visible = state.showsInvalid,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(100)),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(GiftIcons.Error, contentDescription = null, tint = colors.takenFg, modifier = Modifier.size(type.caption.glyphSize()))
                Text(invalidText, style = type.caption, color = colors.takenFg)
            }
        }
        if (!state.showsInvalid) {
            state.lockedCurrency?.let {
                Text(stringResource(R.string.join_pot_locked_currency, it), style = type.caption, color = colors.textSecondary)
            }
        }
        state.error?.let { FCNotice(GiftIcons.Warning, stringResource(it.messageRes), tone = FCNoticeTone.WARNING) }
        Suggestions(state, actions, onPicked = { focusManager.clearFocus() })
    }
}

/** Devise : texte `textSecondary` si la cagnotte l'impose, sinon pastille `primary` + ▾ qui ouvre un menu. */
@Composable
private fun CurrencyControl(state: JoinPotUiState, actions: JoinPotActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val locked = state.lockedCurrency
    if (locked != null) {
        Text(
            text = locked,
            style = type.headline,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = GiftTheme.spacing.m).clearAndSetSemantics { },
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.join_pot_currency)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
            modifier = Modifier
                .heightIn(min = HitTarget.minimum)
                .fcClickable(onClick = { open = true }, role = Role.DropdownList, shape = CapsuleShape)
                .background(colors.background, CapsuleShape)
                .padding(horizontal = GiftTheme.spacing.m)
                .semantics {
                    contentDescription = label
                    stateDescription = state.currency
                },
        ) {
            Text(state.currency, style = type.headline, color = colors.primary)
            Icon(GiftIcons.DropDown, contentDescription = null, tint = colors.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = GiftTheme.radius.fieldShape, containerColor = colors.surface) {
            state.currencies.forEach { code ->
                val isSelected = code == state.currency
                DropdownMenuItem(
                    text = {
                        Text(code, style = if (isSelected) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout, color = colors.textPrimary)
                    },
                    trailingIcon = if (isSelected) {
                        { Icon(GiftIcons.Checkmark, contentDescription = null, tint = colors.secondary) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        actions.onCurrencyChange(code)
                    },
                    modifier = Modifier.heightIn(min = HitTarget.minimum).semantics { selected = isSelected },
                )
            }
        }
    }
}

/**
 * Montants suggérés en pastilles (`surface` bordée ; choisie : `primary` pleine). Celle qui complète la
 * cagnotte se lit « Le reste : 40 CHF ». Défilement horizontal si elles ne tiennent pas.
 */
@Composable
private fun Suggestions(state: JoinPotUiState, actions: JoinPotActions, onPicked: () -> Unit) {
    if (state.suggestions.isEmpty()) return
    val colors = GiftTheme.colors
    val currency = state.lockedCurrency ?: state.currency
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
    ) {
        state.suggestions.forEach { value ->
            val text = Money.format(value, currency).orEmpty()
            val isRest = state.remainingForMe?.compareTo(value) == 0
            val isSelected = state.amount?.compareTo(value) == 0
            val shown = if (isRest) stringResource(R.string.join_pot_rest, text) else text
            val spoken = if (isRest) stringResource(R.string.join_pot_rest_a11y, text) else text
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = HitTarget.minimum)
                    .fcClickable(onClick = { actions.onSuggestion(value); onPicked() }, shape = CapsuleShape, pressedScale = 0.95f)
                    .background(if (isSelected) colors.primary else colors.surface, CapsuleShape)
                    .then(if (isSelected) Modifier else Modifier.border(1.dp, colors.separator, CapsuleShape))
                    .padding(horizontal = GiftTheme.spacing.l)
                    .semantics {
                        contentDescription = spoken
                        selected = isSelected
                    },
            ) {
                Text(
                    text = shown,
                    style = GiftTheme.type.callout.tabular().copy(fontWeight = FontWeight.Medium),
                    color = if (isSelected) colors.onPrimary else colors.textPrimary,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        }
    }
}

// region Aperçus

private val previewActions = JoinPotActions({}, {}, {}, {}, {}, {})

private val previewItem = GiftPreviewData.detailItem(ItemStatus.POT)

@GiftPreviews
@Composable
private fun JoinPotNewPreview() = GiftManagerTheme {
    Column(Modifier.background(GiftTheme.colors.background).padding(top = GiftTheme.spacing.l)) {
        JoinPotContent(
            JoinPotUiState(
                isLoading = false,
                item = previewItem,
                childName = "Léo",
                progress = PotProgress(BigDecimal("120"), "CHF", BigDecimal("199"), 3),
                amountText = "79",
                amount = BigDecimal("79"),
                currency = "CHF",
                lockedCurrency = "CHF",
                suggestions = listOf(BigDecimal("20"), BigDecimal("30"), BigDecimal("50"), BigDecimal("79")),
                remainingForMe = BigDecimal("79"),
            ),
            previewActions,
        )
    }
}

@GiftPreviews
@Composable
private fun JoinPotInvalidPreview() = GiftManagerTheme {
    Column(Modifier.background(GiftTheme.colors.background).padding(top = GiftTheme.spacing.l)) {
        JoinPotContent(
            JoinPotUiState(
                isLoading = false,
                item = GiftPreviewData.detailItem(ItemStatus.AVAILABLE),
                childName = "Léo",
                amountText = "abc",
                showsInvalid = true,
                currency = "EUR",
                suggestions = listOf(BigDecimal("20"), BigDecimal("30"), BigDecimal("50")),
            ),
            previewActions,
        )
    }
}

// endregion

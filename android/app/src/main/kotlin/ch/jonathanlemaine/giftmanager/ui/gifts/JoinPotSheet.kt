package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.FCTextField
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PotProgressCard
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.math.BigDecimal

/** Rappels de la feuille de participation à une cagnotte. L'écran définitif (ui-designer) garde cette surface. */
data class JoinPotActions(
    val onDismiss: () -> Unit,
    val onAmountChange: (String) -> Unit,
    val onCurrencyChange: (String) -> Unit,
    val onSuggestion: (BigDecimal) -> Unit,
    val onSave: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Feuille « Participer à une cagnotte » (câblage minimal) : progression, montant, devise (verrouillée par une
 * cagnotte existante), suggestions et rappel de confidentialité.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinPotSheet(state: JoinPotUiState, actions: JoinPotActions) {
    val spacing = GiftTheme.spacing
    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl, vertical = spacing.l),
        ) {
            Text(
                text = stringResource(if (state.isUpdate) R.string.join_pot_title_update else R.string.join_pot_title_new),
                style = GiftTheme.type.title,
                modifier = Modifier.semantics { heading() },
            )
            state.item?.let { Text(it.title, style = GiftTheme.type.headline) }
            state.childName?.let { Text(stringResource(R.string.join_pot_for_child, it), color = GiftTheme.colors.textSecondary) }
            state.progress?.let { progress ->
                PotProgressCard(
                    progress,
                    myShareText = state.item?.myContribution?.let { Money.format(it, progress.currency) },
                )
            }
            FCTextField(
                label = stringResource(if (state.isUpdate) R.string.join_pot_amount_label_update else R.string.join_pot_amount_label_new),
                value = state.amountText,
                onValueChange = actions.onAmountChange,
                placeholder = stringResource(R.string.join_pot_amount_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (state.showsInvalid) {
                FCNotice(GiftIcons.Warning, stringResource(R.string.join_pot_invalid), tone = FCNoticeTone.WARNING)
            }
            CurrencyRow(state, actions)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                state.suggestions.forEach { value ->
                    val text = Money.format(value, state.currency).orEmpty()
                    val isRest = state.remainingForMe?.compareTo(value) == 0
                    TextLinkButton(
                        text = if (isRest) stringResource(R.string.join_pot_rest, text) else text,
                        onClick = { actions.onSuggestion(value) },
                    )
                }
            }
            FCNotice(
                GiftIcons.EyeSlash,
                state.childName?.let { stringResource(R.string.join_pot_privacy, it) }
                    ?: stringResource(R.string.join_pot_privacy_unknown_child),
                tone = FCNoticeTone.SURPRISE,
            )
            PrimaryButton(
                text = stringResource(if (state.isUpdate) R.string.join_pot_submit_update else R.string.join_pot_submit_new),
                onClick = actions.onSave,
                isLoading = state.isSaving,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

@Composable
private fun CurrencyRow(state: JoinPotUiState, actions: JoinPotActions) {
    val locked = state.lockedCurrency
    if (locked != null) {
        Text(stringResource(R.string.join_pot_locked_currency, locked), color = GiftTheme.colors.textSecondary)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        Text(stringResource(R.string.join_pot_currency), color = GiftTheme.colors.textSecondary)
        state.currencies.forEach { code ->
            TextLinkButton(
                text = if (code == state.currency) "[$code]" else code,
                onClick = { actions.onCurrencyChange(code) },
            )
        }
    }
}

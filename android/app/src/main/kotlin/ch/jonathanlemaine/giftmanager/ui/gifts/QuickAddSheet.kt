package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Feuille « + » (câblage minimal) : choisir la liste où ajouter. Mes enfants reçoivent des envies, ceux des autres
 * foyers des idées (invisibles pour leurs parents).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(state: QuickAddUiState, onPick: (QuickAddTarget) -> Unit, onDismiss: () -> Unit) {
    val spacing = GiftTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl, vertical = spacing.l),
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = GiftTheme.type.title,
                modifier = Modifier.semantics { heading() },
            )
            if (state.isEmpty) Text(stringResource(R.string.add_sheet_empty), color = GiftTheme.colors.textSecondary)
            if (state.myLists.isNotEmpty()) {
                Text(stringResource(R.string.add_sheet_my_lists), style = GiftTheme.type.headline)
                state.myLists.forEach { target -> TextLinkButton(target.child.firstName, { onPick(target) }) }
            }
            if (state.otherLists.isNotEmpty()) {
                Text(stringResource(R.string.add_sheet_other_lists), style = GiftTheme.type.headline)
                state.otherLists.forEach { target -> TextLinkButton(target.child.firstName, { onPick(target) }) }
                FCNotice(GiftIcons.EyeSlash, stringResource(R.string.add_sheet_notice), tone = FCNoticeTone.SURPRISE)
            }
        }
    }
}

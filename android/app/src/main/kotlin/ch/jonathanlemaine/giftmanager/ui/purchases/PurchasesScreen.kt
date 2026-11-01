package ch.jonathanlemaine.giftmanager.ui.purchases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.ui.budgets.BudgetsUiState
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.ApproxPriceText
import ch.jonathanlemaine.giftmanager.ui.components.IndicativeRateNote
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.gifts.openInBrowser
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import java.util.UUID

/** Actions de [PurchasesScreen], reliées à [PurchasesViewModel] et à `BudgetsViewModel`. */
data class PurchasesActions(
    val onOpenItem: (UUID) -> Unit,
    val onTogglePurchased: (MyReservation) -> Unit,
    val onCancelReservation: (MyReservation) -> Unit,
    val onRequestLeave: (MyContribution) -> Unit,
    val onConfirmLeave: () -> Unit,
    val onCancelLeave: () -> Unit,
    val onNewBudget: () -> Unit,
    val onEditBudget: (Budget) -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Câblage minimal de « Mes achats » (iOS : `MyPurchasesView` + `BudgetsSection`) : toutes les données des ViewModel
 * sont affichées, sans mise en forme définitive — l'écran final revient au `ui-designer`.
 */
@Composable
fun PurchasesScreen(state: PurchasesUiState, budgets: BudgetsUiState, actions: PurchasesActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val type = GiftTheme.type
    ScreenColumn(modifier = modifier) {
        ScreenTitle(stringResource(R.string.tab_purchases))
        if (!state.hasLoaded) Text(stringResource(R.string.purchases_loading), style = type.caption)
        if (state.isEmpty) {
            Text(stringResource(R.string.purchases_empty_title), style = type.headline)
            Text(stringResource(R.string.purchases_empty_message), style = type.body)
        } else if (state.hasLoaded) {
            Text("${state.remainingCount} ${state.summaryCountLabel}", style = type.headline)
            state.totals.forEach { total -> Text(Money.format(total.amount, total.currency).orEmpty(), style = type.callout) }
            state.convertedTotalText?.let { Text(it, style = type.captionBold) }
        }

        SectionHeader(
            title = stringResource(R.string.budgets_title),
            actionLabel = stringResource(R.string.budgets_add),
            onAction = actions.onNewBudget,
        )
        budgets.rows.forEach { row ->
            Column(modifier = Modifier.fillMaxWidth()) {
                TextLinkButton(row.budget.scopeTitle, { actions.onEditBudget(row.budget) })
                Text(row.gaugeText, style = type.callout)
                Text(row.statusText, style = type.caption)
                row.approxText?.let { ApproxPriceText(it, style = type.caption) }
            }
        }
        budgets.indicativeTotal?.let { Text(it.text, style = type.captionBold) }

        state.groups.forEach { group ->
            Text(group.title, style = type.headline)
            group.children.forEach { child ->
                Text(stringResource(R.string.purchases_for_child, child.name), style = type.captionBold)
                child.items.forEach { reservation ->
                    val link = state.bestLink(reservation.itemId)
                    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), modifier = Modifier.padding(bottom = GiftTheme.spacing.s)) {
                        TextLinkButton(reservation.title, { actions.onOpenItem(reservation.itemId) })
                        Text(listOfNotNull(link?.priceText, link?.store).joinToString(" · "), style = type.caption)
                        state.approxText(link)?.let { ApproxPriceText(it, style = type.caption) }
                        val toggle = if (reservation.status == ReservationState.PURCHASED) R.string.purchases_unmark_purchased else R.string.purchases_mark_purchased
                        TextLinkButton(stringResource(toggle), { actions.onTogglePurchased(reservation) }, enabled = !state.isWorking)
                        TextLinkButton(stringResource(R.string.purchases_cancel_reservation), { actions.onCancelReservation(reservation) }, enabled = !state.isWorking)
                        link?.let { TextLinkButton(it.store ?: it.url, { openInBrowser(context, it.url) }) }
                    }
                }
            }
        }

        if (state.contributions.isNotEmpty()) {
            SectionHeader(title = stringResource(R.string.purchases_pots))
            state.contributions.forEach { contribution ->
                Text("${contribution.title} · ${Money.format(contribution.amount, contribution.currency)}", style = type.body)
                state.approxText(contribution)?.let { ApproxPriceText(it, style = type.caption) }
                TextLinkButton(stringResource(R.string.purchases_leave_pot), { actions.onRequestLeave(contribution) }, enabled = !state.isWorking)
            }
        }
        if (state.hasConversion || budgets.hasConversion) IndicativeRateNote(state.rateDateText ?: budgets.rateDateText)
    }

    state.leaving?.let { contribution ->
        GiftAlertDialog(
            title = stringResource(R.string.purchases_leave_title),
            message = stringResource(R.string.purchases_leave_message),
            confirmLabel = stringResource(R.string.purchases_leave_confirm, contribution.title),
            onConfirm = actions.onConfirmLeave,
            onDismiss = actions.onCancelLeave,
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error ?: budgets.error, actions.onDismissError)
}

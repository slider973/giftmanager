package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.StatusBadge
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Rappels de la fiche cadeau. La cagnotte (participer, modifier sa part) est branchée par `onJoinPot`. */
data class GiftDetailActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onReserve: () -> Unit,
    val onCancelReservation: () -> Unit,
    val onSetPurchased: (Boolean) -> Unit,
    val onToggleOwned: () -> Unit,
    val onMarkReceived: () -> Unit,
    val onDelete: () -> Unit,
    val onJoinPot: () -> Unit,
    val onLeavePot: () -> Unit,
    val onDismissError: () -> Unit,
)

/**
 * Câblage minimal de la fiche cadeau. Tout ce qui touche à la réservation vient de `state.reservation` : en vue
 * parent (`status == null`), ni badge, ni « Je l'offre », ni cagnotte — seulement la notice « Mode surprise ».
 */
@Composable
fun GiftDetailScreen(state: GiftDetailUiState, actions: GiftDetailActions, modifier: Modifier = Modifier) {
    val item = state.item
    TopBarScreen(
        modifier = modifier,
        topBar = { GiftTopBar(TopBarNavigation.BACK, onNavigate = actions.onBack, title = item?.title) },
    ) {
        ScreenColumn {
            if (item != null) {
                state.reservation?.let { reservation ->
                    reservation.badge?.let { StatusBadge(it) }
                    item.notes?.takeIf { it.isNotEmpty() }?.let { Text(it, color = GiftTheme.colors.textSecondary) }
                    state.links.forEach { link ->
                        Text("${link.store ?: link.url} ${link.priceText.orEmpty()}".trim(), color = GiftTheme.colors.textPrimary)
                    }
                    if (reservation.surpriseNotice) {
                        FCNotice(GiftIcons.EyeSlash, stringResource(R.string.gift_detail_surprise), tone = FCNoticeTone.SURPRISE)
                    }
                    if (reservation.canReserve) {
                        SecondaryButton(stringResource(R.string.gift_detail_reserve), actions.onReserve, isLoading = state.isWorking)
                    }
                    if (reservation.canStartPot || reservation.showPot) {
                        TextLinkButton(stringResource(R.string.gift_detail_join_pot), actions.onJoinPot)
                    }
                    if (reservation.showPot && item.isInMyPot) {
                        state.participants.forEach { p ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(if (p.isMe) stringResource(R.string.gift_detail_me) else p.displayName)
                                Text(" ${p.amountText}")
                            }
                        }
                        TextLinkButton(stringResource(R.string.gift_detail_leave_pot), actions.onLeavePot)
                    }
                    if (reservation.canMarkPurchased) {
                        PrimaryButton(stringResource(R.string.gift_detail_purchased), { actions.onSetPurchased(true) })
                    }
                    if (reservation.canUnmarkPurchased) {
                        TextLinkButton(stringResource(R.string.gift_detail_unpurchased), { actions.onSetPurchased(false) })
                    }
                    if (reservation.canCancelReservation) {
                        TextLinkButton(stringResource(R.string.gift_detail_cancel_reservation), actions.onCancelReservation)
                    }
                    if (reservation.isTaken) Text(stringResource(R.string.gift_detail_taken))
                    if (reservation.isOwnedByChild) Text(stringResource(R.string.gift_detail_owned_by_child))
                }
                if (state.canParentReserve) {
                    TextLinkButton(stringResource(R.string.gift_detail_reserve_myself), actions.onReserve)
                }
                if (state.canMarkReceived) {
                    SecondaryButton(stringResource(R.string.gift_detail_received), actions.onMarkReceived)
                }
                if (state.canToggleOwned) {
                    TextLinkButton(
                        stringResource(if (item.owned) R.string.gifts_back_to_list else R.string.gifts_already_owned),
                        actions.onToggleOwned,
                    )
                }
                if (state.canEdit) {
                    TextLinkButton(stringResource(R.string.gift_detail_edit), actions.onEdit)
                    TextLinkButton(stringResource(R.string.gifts_delete), actions.onDelete)
                }
            }
        }
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

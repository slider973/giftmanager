package ch.jonathanlemaine.giftmanager.ui.purchases

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.model.DayDate
import ch.jonathanlemaine.giftmanager.core.model.ItemLink
import ch.jonathanlemaine.giftmanager.core.model.MyContribution
import ch.jonathanlemaine.giftmanager.core.model.MyReservation
import ch.jonathanlemaine.giftmanager.core.model.ReceiptRules
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.purchases.PurchasesLogic
import ch.jonathanlemaine.giftmanager.ui.budgets.BudgetPreviewData
import ch.jonathanlemaine.giftmanager.ui.budgets.BudgetsSection
import ch.jonathanlemaine.giftmanager.ui.budgets.BudgetsUiState
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.ApproxPriceText
import ch.jonathanlemaine.giftmanager.ui.components.CountryFlag
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.IndicativeRateNote
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.bleed
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.fcPressable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftActionsMenu
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftMenuEntry
import ch.jonathanlemaine.giftmanager.ui.gifts.RoundIconButton
import ch.jonathanlemaine.giftmanager.ui.gifts.openInBrowser
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal
import java.time.LocalDate
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
    /** Ouvre la boîte des remerciements reçus (#75). */
    val onOpenThanks: () -> Unit = {},
    /** Tirer pour actualiser : achats et budgets. */
    val onRefresh: () -> Unit = {},
)

/**
 * « Mes achats » (iOS : `MyPurchasesView` + `BudgetsSection`, #12 / #35 / #40).
 *
 * Grand titre, puis : carte de résumé (cadeaux à acheter, totaux par devise jamais mélangés, total converti
 * indicatif), section « Budgets », mes réservations groupées par événement puis par enfant, et mes cagnottes avec
 * ma part. Tirer pour actualiser. Chargement : loutre au paquet ; rien de prévu : loutre endormie, titre et budgets
 * restent visibles (un budget se prépare avant toute réservation).
 *
 * Actions d'une réservation (iOS : balayage « Annuler ») : bascule « acheté » ronde, « Ouvrir la boutique », menu
 * « Plus d'actions » et actions TalkBack. Annuler une réservation et quitter une cagnotte demandent confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(state: PurchasesUiState, budgets: BudgetsUiState, actions: PurchasesActions, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    val refreshState = rememberPullToRefreshState()
    val bottomInset = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
    var pendingCancel by remember { mutableStateOf<MyReservation?>(null) }
    val column = Modifier.widthIn(max = ScreenContentMaxWidth).fillMaxWidth()

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
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
            contentPadding = PaddingValues(start = spacing.xl, end = spacing.xl, top = spacing.l, bottom = spacing.xxl + bottomInset),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = column) {
                    ScreenTitle(stringResource(R.string.tab_purchases), modifier = Modifier.weight(1f))
                    // Câblage minimal (#75) : l'entrée de la boîte de remerciements, reprise par le ui-designer.
                    IconButton(onClick = actions.onOpenThanks) {
                        Icon(GiftIcons.Mail, contentDescription = stringResource(R.string.thanks_open_inbox), tint = colors.primary)
                    }
                }
            }
            if (state.thanksCount > 0) {
                item(key = "thanks-banner") {
                    val label = if (state.thanksCount > 1) stringResource(R.string.thanks_banner_many, state.thanksCount)
                    else stringResource(R.string.thanks_banner_one)
                    Text(
                        label,
                        style = GiftTheme.type.headline,
                        color = colors.textPrimary,
                        modifier = column.fcCard().fcClickable(onClick = actions.onOpenThanks, shape = GiftTheme.radius.cardShape),
                    )
                }
            }
            when {
                !state.hasLoaded -> item(key = "loading") {
                    GiftLoadingView(size = 72.dp, label = stringResource(R.string.purchases_loading), modifier = column.padding(vertical = spacing.xxl))
                }
                state.isEmpty -> item(key = "empty") {
                    EmptyStateView(
                        illustration = Illustration.MASCOT_SLEEPING,
                        title = stringResource(R.string.purchases_empty_title),
                        message = stringResource(R.string.purchases_empty_message),
                        modifier = column,
                    )
                }
                else -> item(key = "summary") { SummaryCard(state, column) }
            }
            if (state.hasLoaded) {
                item(key = "budgets") {
                    BudgetsSection(
                        state = budgets,
                        onNewBudget = actions.onNewBudget,
                        onEditBudget = actions.onEditBudget,
                        modifier = column.padding(top = spacing.s),
                    )
                }
            }
            reservationGroups(state, actions, column, onRequestCancel = { pendingCancel = it })
            pots(state, actions, column)
            if (state.hasLoaded && (state.hasConversion || budgets.hasConversion)) {
                item(key = "rate-note") {
                    IndicativeRateNote(state.rateDateText ?: budgets.rateDateText, modifier = column.padding(top = spacing.s))
                }
            }
        }
    }

    pendingCancel?.let { reservation ->
        GiftAlertDialog(
            title = stringResource(R.string.purchases_cancel_title),
            message = stringResource(R.string.purchases_cancel_message, reservation.title),
            confirmLabel = stringResource(R.string.purchases_cancel_reservation),
            onConfirm = {
                pendingCancel = null
                actions.onCancelReservation(reservation)
            },
            onDismiss = { pendingCancel = null },
            dismissLabel = stringResource(R.string.purchases_keep),
            destructive = true,
        )
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

// region Résumé

/**
 * « 3 cadeaux à acheter │ CHF 120.00 + 45,00 € · Budget estimé · ≈ 163 CHF au total · indicatif ». Un seul nœud
 * TalkBack ; le total converti a sa phrase parlée. Aux grandes polices, les deux blocs s'empilent.
 */
@Composable
private fun SummaryCard(state: PurchasesUiState, modifier: Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val count = if (state.reservations.isEmpty()) state.contributions.size else state.remainingCount
    val totals = state.totals.mapNotNull { Money.format(it.amount, it.currency) }
    val totalsText = if (totals.isEmpty()) stringResource(R.string.purchases_summary_none) else totals.joinToString(stringResource(R.string.purchases_total_separator))
    val totalsSpoken = if (totals.isEmpty()) stringResource(R.string.purchases_summary_none_spoken) else totals.joinToString(", ")
    val budgetLabel = stringResource(R.string.purchases_summary_budget)
    val spoken = listOfNotNull("$count ${state.summaryCountLabel}", "$budgetLabel : $totalsSpoken", state.convertedTotalSpokenText).joinToString(", ")
    // Comme `GiftCard` : pile verticale dès 150 % de police.
    val stacked = LocalDensity.current.fontScale >= 1.5f

    val countBlock: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(count.toString(), style = type.title.tabular(), color = colors.textPrimary)
            Text(state.summaryCountLabel, style = type.caption, color = colors.textSecondary)
        }
    }
    val totalsBlock: @Composable (Modifier) -> Unit = { blockModifier ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = blockModifier) {
            Text(totalsText, style = type.headline.tabular(), color = colors.textPrimary)
            Text(budgetLabel, style = type.caption, color = colors.textSecondary)
            state.convertedTotalText?.let { Text(it, style = type.captionBold.tabular(), color = colors.textSecondary) }
        }
    }
    val card = modifier.fcCard(padding = spacing.l).clearAndSetSemantics { contentDescription = spoken }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.m), modifier = card) {
            countBlock()
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.separator))
            totalsBlock(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.l), verticalAlignment = Alignment.CenterVertically, modifier = card) {
            countBlock()
            Box(Modifier.width(1.dp).height(36.dp).background(colors.separator))
            totalsBlock(Modifier.weight(1f))
        }
    }
}

// endregion

// region Réservations

private fun LazyListScope.reservationGroups(
    state: PurchasesUiState,
    actions: PurchasesActions,
    column: Modifier,
    onRequestCancel: (MyReservation) -> Unit,
) {
    state.groups.forEach { group ->
        item(key = "event-${group.id}") { EventHeader(group, column) }
        group.children.forEach { child ->
            item(key = "child-${group.id}-${child.childId}") {
                Text(
                    text = stringResource(R.string.purchases_for_child, child.name),
                    style = GiftTheme.type.captionBold,
                    color = GiftTheme.colors.textSecondary,
                    modifier = column.padding(start = GiftTheme.spacing.xs).semantics { heading() },
                )
            }
            items(child.items, key = { "reservation-${group.id}-${it.itemId}" }) { reservation ->
                ReservationCard(
                    reservation = reservation,
                    link = state.bestLink(reservation.itemId),
                    approxText = state.approxText(state.bestLink(reservation.itemId)),
                    enabled = !state.isWorking,
                    onOpen = { actions.onOpenItem(reservation.itemId) },
                    onTogglePurchased = { actions.onTogglePurchased(reservation) },
                    onRequestCancel = { onRequestCancel(reservation) },
                    modifier = column,
                )
            }
        }
    }
}

/** Titre de l'événement (`headline`) et sa date (`caption`) : un seul titre TalkBack. */
@Composable
private fun EventHeader(group: PurchasesLogic.EventGroup, modifier: Modifier) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .padding(top = GiftTheme.spacing.m)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(group.title, style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
        group.date?.let { Text(Formatting.dateText(it), style = type.caption, color = colors.textSecondary) }
    }
}

/**
 * Carte d'une réservation : vignette 56 dp, titre (2 lignes), drapeau + prix + prix converti + boutique, « Acheté »
 * (coche `availableFg`) et l'avis « déjà possédé ». Toute la carte ouvre la fiche ; à droite, la bascule ronde
 * « acheté » (case à cocher TalkBack) ; en bas à droite, « Ouvrir la boutique » et « Plus d'actions ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReservationCard(
    reservation: MyReservation,
    link: ItemLink?,
    approxText: String?,
    onOpen: () -> Unit,
    onTogglePurchased: () -> Unit,
    onRequestCancel: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val context = LocalContext.current
    val purchased = reservation.status == ReservationState.PURCHASED
    var menuOpen by remember { mutableStateOf(false) }

    val openStoreLabel = stringResource(R.string.purchases_open_store)
    val cancelLabel = stringResource(R.string.purchases_cancel_reservation)
    val openStore: (() -> Unit)? = link?.let { { openInBrowser(context, it.url) } }
    val entries = listOfNotNull(
        openStore?.let { GiftMenuEntry(openStoreLabel, GiftIcons.OpenExternal, colors.primary, onClick = it) },
        GiftMenuEntry(cancelLabel, GiftIcons.RemoveCircle, colors.takenFg, destructive = true, onClick = onRequestCancel),
    )
    val a11yActions = entries.map { entry -> CustomAccessibilityAction(entry.label) { entry.onClick(); true } }

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
        modifier = modifier
            .fcClickable(onClick = onOpen, onClickLabel = stringResource(R.string.gift_open), shape = GiftTheme.radius.cardShape, pressedScale = 0.98f)
            .fcCard(padding = spacing.m)
            .semantics { if (enabled) customActions = a11yActions },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.m), verticalAlignment = Alignment.Top) {
            Thumbnail(reservation.imageUrl, reservation.title)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
                Text(reservation.title, style = type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                PriceLine(link, approxText)
            }
            PurchasedToggle(
                title = reservation.title,
                purchased = purchased,
                enabled = enabled,
                onToggle = onTogglePurchased,
                modifier = Modifier.bleed(top = spacing.s, end = spacing.s),
            )
        }
        if (reservation.owned) OwnedNotice(reservation.eventDate)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                if (purchased) PurchasedLabel()
            }
            openStore?.let {
                RoundIconButton(
                    icon = GiftIcons.OpenExternal,
                    label = stringResource(R.string.purchases_open_store_item, reservation.title),
                    onClick = it,
                )
            }
            Box(modifier = Modifier.bleed(end = spacing.s)) {
                RoundIconButton(
                    icon = GiftIcons.More,
                    label = stringResource(R.string.purchases_more_actions, reservation.title),
                    onClick = { menuOpen = true },
                    tint = colors.textSecondary,
                    enabled = enabled,
                )
                GiftActionsMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, entries = entries)
            }
        }
    }
}

/** Vignette 56 dp (grandit un peu avec la police), placeholder pastel ; décorative. */
@Composable
private fun Thumbnail(url: String?, seed: String) {
    RemoteImage(
        url = url,
        placeholderSeed = seed,
        contentDescription = null,
        modifier = Modifier.size(56.dp.scaledByFont(1.3f)).clip(GiftTheme.radius.thumbShape),
    )
}

/** 🇨🇭 CHF 129.00 ≈ 137 € · Galaxus — passe à la ligne aux grandes polices. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceLine(link: ItemLink?, approxText: String?) {
    if (link == null) return
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    val price = link.priceText
    val store = link.store
    if (price == null && store == null && link.country == null) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        link.country?.let { CountryFlag(it, style = type.caption) }
        price?.let { Text(it, style = type.caption.tabular(), color = colors.textSecondary) }
        approxText?.let { ApproxPriceText(it, style = type.caption) }
        store?.let { Text(if (price != null) "· $it" else it, style = type.caption, color = colors.textSecondary) }
    }
}

/**
 * Bascule ronde « acheté » (iOS : `circle` / `checkmark.circle.fill`) : cercle `textSecondary` puis disque coché
 * `availableFg`, en fondu (instantané si animations réduites), retour haptique. Cible 48 dp, rôle case à cocher :
 * « Acheté : Lego, case à cocher, cochée ».
 */
@Composable
private fun PurchasedToggle(title: String, purchased: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.purchases_purchased_toggle, title)
    val source = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(HitTarget.minimum)
            .fcPressable(source, CircleShape, pressedScale = 0.85f)
            .toggleable(
                value = purchased,
                enabled = enabled,
                role = Role.Checkbox,
                interactionSource = source,
                indication = null,
                onValueChange = { on ->
                    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onToggle()
                },
            )
            .semantics { contentDescription = label },
    ) {
        Crossfade(
            targetState = purchased,
            animationSpec = if (GiftTheme.reduceMotion) snap() else tween(200),
            label = "purchasedToggle",
        ) { on ->
            Icon(
                imageVector = if (on) GiftIcons.Check else GiftIcons.CircleOutline,
                contentDescription = null,
                tint = if (on) colors.availableFg else colors.textSecondary,
                modifier = Modifier.size(28.dp.scaledByFont(1.3f)),
            )
        }
    }
}

/** « ✓ Acheté » en `availableFg` semi-gras. */
@Composable
private fun PurchasedLabel() {
    val type = GiftTheme.type
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(GiftIcons.Checkmark, contentDescription = null, tint = GiftTheme.colors.availableFg, modifier = Modifier.size(type.captionBold.glyphSize()))
        Text(stringResource(R.string.purchases_purchased), style = type.captionBold, color = GiftTheme.colors.availableFg)
    }
}

/** Cadeau noté possédé : « Bien reçu ! » si la fête a eu lieu, sinon alerte de doublon (iOS : `ownedNotice`). */
@Composable
private fun OwnedNotice(eventDate: DayDate?) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    if (ReceiptRules.isReceived(owned = true, eventDate = eventDate)) {
        Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(GiftIcons.Gift, contentDescription = null, tint = colors.availableFg, modifier = Modifier.size(type.captionBold.glyphSize()))
            Text(stringResource(R.string.purchases_received), style = type.captionBold, color = colors.availableFg)
        }
    } else {
        FCNotice(GiftIcons.Warning, stringResource(R.string.purchases_owned_warning), tone = FCNoticeTone.WARNING)
    }
}

// endregion

// region Cagnottes

private fun LazyListScope.pots(state: PurchasesUiState, actions: PurchasesActions, column: Modifier) {
    if (state.contributions.isEmpty()) return
    item(key = "pots-header") { PotsHeader(state.contributions.size, column) }
    items(state.contributions, key = { "pot-${it.itemId}" }) { contribution ->
        PotCard(
            contribution = contribution,
            approxText = state.approxText(contribution),
            enabled = !state.isWorking,
            onOpen = { actions.onOpenItem(contribution.itemId) },
            onRequestLeave = { actions.onRequestLeave(contribution) },
            modifier = column,
        )
    }
}

/** « Cagnottes » + « 2 participations » : un seul titre TalkBack. */
@Composable
private fun PotsHeader(count: Int, modifier: Modifier) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
            .padding(top = GiftTheme.spacing.m)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(stringResource(R.string.purchases_pots), style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.purchases_pots_count, count, count), style = type.caption, color = colors.textSecondary)
    }
}

/**
 * Carte d'une participation : vignette, titre, « Pour Léo · Noël 2026 », pastille « Ma part : 40,00 € »
 * (`potBg` / `potFg`), prix converti, « 120,00 € réunis · 3 participants », avis « déjà possédé ». Toucher ouvre
 * la fiche (on y modifie sa part) ; « Quitter la cagnotte » par le menu ⋮ et en action TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PotCard(
    contribution: MyContribution,
    approxText: String?,
    onOpen: () -> Unit,
    onRequestLeave: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    var menuOpen by remember { mutableStateOf(false) }
    val share = Money.format(contribution.amount, contribution.currency).orEmpty()
    val total = Money.format(contribution.potTotal, contribution.currency)
    val participants = contribution.potCount ?: 1
    val leaveLabel = stringResource(R.string.purchases_leave_pot)
    val entries = listOf(GiftMenuEntry(leaveLabel, GiftIcons.SignOut, colors.takenFg, destructive = true, onClick = onRequestLeave))

    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.Top,
        modifier = modifier
            .fcClickable(onClick = onOpen, onClickLabel = stringResource(R.string.gift_open), shape = GiftTheme.radius.cardShape, pressedScale = 0.98f)
            .fcCard(padding = spacing.m)
            .semantics { if (enabled) customActions = listOf(CustomAccessibilityAction(leaveLabel) { onRequestLeave(); true }) },
    ) {
        Thumbnail(contribution.imageUrl, contribution.title)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.weight(1f)) {
            Text(contribution.title, style = type.headline, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(stringResource(R.string.purchases_for_child, contribution.childName), contribution.eventTitle).joinToString(" · "),
                style = type.caption,
                color = colors.textSecondary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.purchases_pot_my_share, share),
                    style = type.captionBold.tabular(),
                    color = colors.potFg,
                    modifier = Modifier.background(colors.potBg, CapsuleShape).padding(horizontal = spacing.s, vertical = 2.dp),
                )
                approxText?.let { ApproxPriceText(it, style = type.caption) }
                total?.let {
                    Text(
                        text = pluralStringResource(R.plurals.purchases_pot_collected, participants, participants, it),
                        style = type.caption.tabular(),
                        color = colors.textSecondary,
                    )
                }
            }
            if (contribution.owned) Box(Modifier.padding(top = spacing.xs)) { OwnedNotice(contribution.eventDate) }
        }
        Box(modifier = Modifier.bleed(top = spacing.s, end = spacing.s)) {
            RoundIconButton(
                icon = GiftIcons.More,
                label = stringResource(R.string.purchases_more_actions, contribution.title),
                onClick = { menuOpen = true },
                tint = colors.textSecondary,
                enabled = enabled,
            )
            GiftActionsMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, entries = entries)
        }
    }
}

// endregion

// region Aperçus

private object PurchasesPreviewData {
    private fun id(seed: String) = UUID.nameUUIDFromBytes(seed.toByteArray())
    private val christmas = DayDate(LocalDate.now().plusDays(40))
    private val past = DayDate(LocalDate.now().minusDays(10))

    fun reservation(title: String, status: ReservationState = ReservationState.RESERVED, owned: Boolean = false, eventDate: DayDate? = christmas, child: String = "Léo") =
        MyReservation(
            itemId = id(title),
            title = title,
            status = status,
            owned = owned,
            childId = id(child),
            childName = child,
            eventId = id("noel"),
            eventTitle = "Noël 2026",
            eventDate = eventDate,
        )

    fun link(title: String, store: String, country: String, price: Int, currency: String) =
        ItemLink(id("$title-link"), id(title), "https://example.com", store, country, BigDecimal(price), currency)

    val toBuy = reservation("Lego Technic Bugatti")
    val bought = reservation("Trottinette lumineuse", ReservationState.PURCHASED, child = "Zoé")
    val owned = reservation("Puzzle 1000 pièces", owned = true)
    val received = reservation("Livre « Le Petit Prince »", ReservationState.PURCHASED, owned = true, eventDate = past)
    val reservations = listOf(toBuy, bought, owned)
    val links = mapOf(
        toBuy.itemId to listOf(link(toBuy.title, "Galaxus", "CH", 129, "CHF")),
        bought.itemId to listOf(link(bought.title, "Fnac", "FR", 59, "EUR")),
        owned.itemId to listOf(link(owned.title, "Manor", "CH", 25, "CHF")),
    )
    val pot = MyContribution(
        itemId = id("pot"),
        title = "Vélo d'équilibre",
        amount = BigDecimal(40),
        currency = "EUR",
        owned = false,
        potTotal = BigDecimal(120),
        potCount = 3,
        childId = id("Zoé"),
        childName = "Zoé",
        eventTitle = "Noël 2026",
    )
    val state = PurchasesUiState(reservations = reservations, contributions = listOf(pot), links = links, preferredCountry = "CH", hasLoaded = true)
}

private val previewActions = PurchasesActions({}, {}, {}, {}, {}, {}, {}, {}, {})

@GiftPreviews
@Composable
private fun ReservationCardsPreview() = PreviewFrame {
    val data = PurchasesPreviewData
    ReservationCard(data.toBuy, data.links[data.toBuy.itemId]?.first(), approxText = "≈ 137 €", onOpen = {}, onTogglePurchased = {}, onRequestCancel = {})
    ReservationCard(data.bought, data.links[data.bought.itemId]?.first(), approxText = null, onOpen = {}, onTogglePurchased = {}, onRequestCancel = {})
    ReservationCard(data.owned, data.links[data.owned.itemId]?.first(), approxText = null, onOpen = {}, onTogglePurchased = {}, onRequestCancel = {})
    ReservationCard(data.received, null, approxText = null, onOpen = {}, onTogglePurchased = {}, onRequestCancel = {})
}

@GiftPreviews
@Composable
private fun PotCardPreview() = PreviewFrame {
    PotCard(PurchasesPreviewData.pot, approxText = "≈ 38 CHF", onOpen = {}, onRequestLeave = {})
}

@GiftPreviews
@Composable
private fun PurchasesScreenPreview() = ScreenPreviewFrame(height = 1500.dp) {
    PurchasesScreen(
        state = PurchasesPreviewData.state,
        budgets = BudgetsUiState(rows = BudgetPreviewData.all, hasLoaded = true),
        actions = previewActions,
    )
}

@GiftPreviews
@Composable
private fun PurchasesEmptyPreview() = ScreenPreviewFrame(height = 900.dp) {
    PurchasesScreen(state = PurchasesUiState(hasLoaded = true), budgets = BudgetsUiState(hasLoaded = true), actions = previewActions)
}

@GiftPreviews
@Composable
private fun PurchasesLoadingPreview() = ScreenPreviewFrame {
    PurchasesScreen(state = PurchasesUiState(), budgets = BudgetsUiState(), actions = previewActions)
}

// endregion

package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Money
import ch.jonathanlemaine.giftmanager.core.gifts.ReservationActions
import ch.jonathanlemaine.giftmanager.core.gifts.StoreCatalog
import ch.jonathanlemaine.giftmanager.core.model.IdeaReview
import ch.jonathanlemaine.giftmanager.core.model.ItemStatus
import ch.jonathanlemaine.giftmanager.core.model.PotParticipant
import ch.jonathanlemaine.giftmanager.core.model.ReservationState
import ch.jonathanlemaine.giftmanager.core.model.WishItem
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.IndicativeRateNote
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PotProgressCard
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.PriorityHeart
import ch.jonathanlemaine.giftmanager.ui.components.RemoteImage
import ch.jonathanlemaine.giftmanager.ui.components.SecondaryButton
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.StatusBadge
import ch.jonathanlemaine.giftmanager.ui.components.StoreLinkRow
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal
import kotlinx.coroutines.delay

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
    /** Cœur « très envie » de la fiche : parents seulement ([GiftDetailUiState.canToggleFavorite]). */
    val onToggleFavorite: () -> Unit = {},
    /** « Dire merci » (#75) : ouvre la rédaction d'un merci. */
    val onThank: () -> Unit = {},
)

/**
 * Fiche cadeau (écran 5 de la maquette, iOS : `GiftDetailView`) : grande image et cœur, titre, badge, notes,
 * « Liens par pays », puis les actions selon le rôle.
 *
 * Confidentialité : tout ce qui touche à la réservation vient de `state.reservation` (dérivé de
 * `isParentView` et du statut serveur). En vue parent, ni badge, ni « Je l'offre », ni cagnotte ni
 * participants — seulement la notice « Mode surprise » et, s'il y a lieu, sa propre réservation.
 */
@Composable
fun GiftDetailScreen(state: GiftDetailUiState, actions: GiftDetailActions, modifier: Modifier = Modifier) {
    val item = state.item
    val scrollState = rememberScrollState()
    val imageHeight = if (item?.imageUrl == null) NoImageHeight else ImageHeight
    val titleThreshold = with(LocalDensity.current) { (imageHeight + 40.dp).toPx() }
    val titleUnderBar by remember(titleThreshold) { derivedStateOf { scrollState.value > titleThreshold } }
    val scrolled by remember { derivedStateOf { scrollState.value > 0 } }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeavePot by remember { mutableStateOf(false) }
    // La célébration est consommée par l'hôte dès qu'elle arrive : l'écran la garde le temps de l'afficher.
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    LaunchedEffect(state.celebration) { state.celebration?.let { celebration = it } }

    Box(modifier = modifier.fillMaxSize()) {
        TopBarScreen(
            topBar = {
                GiftTopBar(
                    navigation = TopBarNavigation.BACK,
                    onNavigate = actions.onBack,
                    title = item?.title,
                    titleVisible = titleUnderBar,
                    showDivider = scrolled,
                ) {
                    if (item != null && state.canEdit) DetailMenu(state, item, actions, onRequestDelete = { confirmDelete = true })
                }
            },
        ) {
            when {
                item != null -> ScreenColumn(scrollState = scrollState, spacing = GiftTheme.spacing.xl) {
                    DetailHeader(state, item, imageHeight, actions)
                    LinksSection(state)
                    if (state.readOnly) {
                        ReceiptActions(state, actions)
                        FCNotice(GiftIcons.Archive, stringResource(R.string.gift_detail_archived))
                    } else {
                        state.reservation?.let { reservation ->
                            Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m)) {
                                if (state.isParentView) {
                                    ParentActions(state, reservation, actions)
                                } else {
                                    FamilyActions(state, item, reservation, actions, onLeavePot = { confirmLeavePot = true })
                                }
                            }
                        }
                    }
                }
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { GiftLoadingView(size = 72.dp) }
            }
        }
        CelebrationOverlay(celebration, onFinish = { celebration = null })
    }

    if (confirmDelete && item != null) {
        GiftAlertDialog(
            title = stringResource(R.string.gifts_delete_title, item.title),
            message = stringResource(R.string.gifts_delete_message),
            confirmLabel = stringResource(R.string.gifts_delete),
            onConfirm = {
                confirmDelete = false
                actions.onDelete()
            },
            onDismiss = { confirmDelete = false },
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    if (confirmLeavePot) {
        GiftAlertDialog(
            title = stringResource(R.string.gift_detail_leave_pot_title),
            message = stringResource(R.string.gift_detail_leave_pot_message),
            confirmLabel = stringResource(R.string.gift_detail_leave_pot),
            onConfirm = {
                confirmLeavePot = false
                actions.onLeavePot()
            },
            onDismiss = { confirmLeavePot = false },
            dismissLabel = stringResource(R.string.cancel),
            destructive = true,
        )
    }
    GiftErrorDialog(state.error, actions.onDismissError)
}

private val ImageHeight = 280.dp
private val NoImageHeight = 180.dp

/** Menu ⋮ de la barre : « Je l'offre moi-même » (parent), « Modifier », « Il l'a déjà », « Supprimer ». */
@Composable
private fun DetailMenu(state: GiftDetailUiState, item: WishItem, actions: GiftDetailActions, onRequestDelete: () -> Unit) {
    val colors = GiftTheme.colors
    var open by remember { mutableStateOf(false) }
    val entries = listOfNotNull(
        if (state.canParentReserve) {
            GiftMenuEntry(stringResource(R.string.gift_detail_reserve_myself), GiftIcons.Gift, colors.primary, onClick = actions.onReserve)
        } else {
            null
        },
        GiftMenuEntry(stringResource(R.string.gift_detail_edit), GiftIcons.Edit, colors.primary, onClick = actions.onEdit),
        if (state.canToggleOwned) {
            GiftMenuEntry(
                label = stringResource(if (item.owned) R.string.gifts_back_to_list else R.string.gifts_already_owned),
                icon = if (item.owned) GiftIcons.Undo else GiftIcons.Verified,
                tint = colors.secondary,
                onClick = actions.onToggleOwned,
            )
        } else {
            null
        },
        GiftMenuEntry(stringResource(R.string.gifts_delete), GiftIcons.Delete, colors.takenFg, destructive = true, onClick = onRequestDelete),
    )
    Box {
        RoundIconButton(GiftIcons.More, stringResource(R.string.gift_detail_actions), onClick = { open = true }, tint = colors.textPrimary)
        GiftActionsMenu(expanded = open, onDismiss = { open = false }, entries = entries)
    }
}

/** Image (ou pastel), cœur sur pastille `surface`, bandeau d'idée, titre, badge et notes. */
@Composable
private fun DetailHeader(state: GiftDetailUiState, item: WishItem, imageHeight: androidx.compose.ui.unit.Dp, actions: GiftDetailActions) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .fcCard(padding = 0.dp)
                .clip(GiftTheme.radius.cardShape),
        ) {
            RemoteImage(
                url = item.imageUrl,
                contentScale = ContentScale.Fit,
                placeholderSeed = item.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (item.kind == WishKind.WISH) {
            PriorityHeart(
                isOn = item.isFavorite,
                onToggle = if (state.canToggleFavorite) { _ -> actions.onToggleFavorite() } else null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(spacing.m)
                    .background(colors.surface, CircleShape),
            )
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        state.ideaReview?.let { review -> IdeaBanner(review, item.reviewNote, state.isParentView) }
        Text(
            text = item.title,
            style = type.title,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        state.reservation?.badge?.let { StatusBadge(it) }
        item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            Text(notes, style = type.body, color = colors.textSecondary, modifier = Modifier.padding(top = spacing.xs))
        }
    }
}

/**
 * Où en est une idée (#57), pour son auteur ou les parents. Refusée : `takenFg` ; sinon `potFg` (l'ambre iOS,
 * qui ne porte pas de texte sur Android, cf. DESIGN.md).
 */
@Composable
private fun IdeaBanner(review: IdeaReview, note: String?, isParentView: Boolean) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    val text = when (review) {
        IdeaReview.NONE -> stringResource(R.string.gift_detail_idea_none)
        IdeaReview.PENDING -> stringResource(if (isParentView) R.string.gift_detail_idea_pending_parent else R.string.gift_detail_idea_pending)
        IdeaReview.ACCEPTED -> stringResource(R.string.gift_detail_idea_accepted)
        IdeaReview.REJECTED -> note?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.gift_detail_idea_rejected_note, it) }
            ?: stringResource(R.string.gift_detail_idea_rejected)
        IdeaReview.OWNED_ALREADY -> stringResource(R.string.gift_detail_idea_owned)
    }
    val tint = if (review == IdeaReview.REJECTED) colors.takenFg else colors.potFg
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) { },
    ) {
        Icon(
            imageVector = if (review == IdeaReview.PENDING) GiftIcons.Hourglass else GiftIcons.Lightbulb,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(type.captionBold.glyphSize(1.3f)),
        )
        Text(text, style = type.captionBold, color = tint)
    }
}

/** « Liens par pays » : une ligne par boutique, celles du pays du lecteur d'abord ; ouvre le navigateur. */
@Composable
private fun LinksSection(state: GiftDetailUiState) {
    if (state.links.isEmpty()) return
    val context = LocalContext.current
    val fallback = stringResource(R.string.gift_detail_link_fallback)
    Column(verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        SectionHeader(title = stringResource(R.string.gift_detail_links))
        state.links.forEach { link ->
            StoreLinkRow(
                store = link.store ?: StoreCatalog.store(link.url)?.name ?: fallback,
                countryCode = link.country,
                priceText = link.priceText,
                approxPriceText = state.approxText(link),
                onClick = { openInBrowser(context, link.url) },
            )
        }
        if (state.hasConversion) IndicativeRateNote(state.rateDateText)
    }
}

/**
 * Vue parent : « Reçu ! » après la fête, notice « Mode surprise », puis sa propre réservation éventuelle (« Je
 * l'offre moi-même » depuis le menu) — jamais celle des autres.
 */
@Composable
private fun ParentActions(state: GiftDetailUiState, reservation: ReservationActions, actions: GiftDetailActions) {
    ReceiptActions(state, actions)
    if (reservation.surpriseNotice) {
        FCNotice(GiftIcons.EyeSlash, stringResource(R.string.gift_detail_surprise), tone = FCNoticeTone.SURPRISE)
    }
    if (reservation.canCancelReservation) {
        val colors = GiftTheme.colors
        val type = GiftTheme.type
        Row(
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { },
        ) {
            Icon(GiftIcons.Gift, contentDescription = null, tint = colors.mineFg, modifier = Modifier.size(type.headline.glyphSize()))
            Text(stringResource(R.string.gift_detail_parent_reserved), style = type.headline, color = colors.mineFg)
        }
        Column {
            if (reservation.canMarkPurchased) {
                TextLinkButton(stringResource(R.string.gift_detail_mark_purchased), { actions.onSetPurchased(true) }, enabled = !state.isWorking)
            }
            if (reservation.canUnmarkPurchased) {
                TextLinkButton(stringResource(R.string.gift_detail_unpurchased), { actions.onSetPurchased(false) }, enabled = !state.isWorking)
            }
            TextLinkButton(stringResource(R.string.gift_detail_cancel_reservation), actions.onCancelReservation, enabled = !state.isWorking)
        }
    }
}

/** « Reçu ! » (#42) : un parent, après la fête ; puis « Dire merci » (#75) une fois le cadeau reçu. */
@Composable
private fun ReceiptActions(state: GiftDetailUiState, actions: GiftDetailActions) {
    if (state.canSendThanks) {
        if (state.donors.isNotEmpty()) {
            Text(stringResource(R.string.gift_detail_donors, state.donorNames), style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
        }
        PrimaryButton(stringResource(R.string.gift_detail_say_thanks), actions.onThank, leadingIcon = GiftIcons.Mail)
        Text(
            stringResource(if (state.donors.isEmpty()) R.string.gift_detail_thanks_anonymous else R.string.gift_detail_thanks_donors),
            style = GiftTheme.type.caption,
            color = GiftTheme.colors.textSecondary,
        )
    }
    if (!state.canMarkReceived) return
    SecondaryButton(
        text = stringResource(R.string.gift_detail_received),
        onClick = actions.onMarkReceived,
        leadingIcon = GiftIcons.Gift,
        isLoading = state.isWorking,
    )
}

/** Vue d'un autre membre de la famille : selon le statut renvoyé par le serveur. */
@Composable
private fun FamilyActions(
    state: GiftDetailUiState,
    item: WishItem,
    reservation: ReservationActions,
    actions: GiftDetailActions,
    onLeavePot: () -> Unit,
) {
    val working = state.isWorking
    when {
        reservation.canReserve -> {
            SecondaryButton(
                text = stringResource(R.string.gift_detail_reserve),
                onClick = actions.onReserve,
                leadingIcon = GiftIcons.Gift,
                isLoading = working,
            )
            if (reservation.canStartPot) {
                TextLinkButton(
                    stringResource(R.string.gift_detail_join_pot),
                    actions.onJoinPot,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                )
            }
            PrivacyCaption(stringResource(R.string.gift_detail_privacy_available))
        }
        reservation.showPot -> PotActions(state, item, actions, onLeavePot)
        reservation.canCancelReservation -> MineActions(state, reservation, actions)
        reservation.isTaken -> FCNotice(GiftIcons.Gift, stringResource(R.string.gift_detail_taken))
        reservation.isOwnedByChild -> FCNotice(
            GiftIcons.Verified,
            state.child?.firstName?.let { stringResource(R.string.gift_detail_owned_by_named, it) }
                ?: stringResource(R.string.gift_detail_owned_by_child),
        )
    }
}

/** Je l'offre : « C'est acheté » (ou la pastille « Acheté — bravo ! »), puis l'annulation. */
@Composable
private fun MineActions(state: GiftDetailUiState, reservation: ReservationActions, actions: GiftDetailActions) {
    val colors = GiftTheme.colors
    if (reservation.canUnmarkPurchased) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HitTarget.button)
                .background(colors.availableBg, CapsuleShape)
                .padding(horizontal = GiftTheme.spacing.l)
                .semantics(mergeDescendants = true) { },
        ) {
            Icon(GiftIcons.Check, contentDescription = null, tint = colors.availableFg)
            Text(stringResource(R.string.gift_detail_purchased_done), style = GiftTheme.type.headline, color = colors.availableFg)
        }
        TextLinkButton(
            stringResource(R.string.gift_detail_unpurchased),
            { actions.onSetPurchased(false) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isWorking,
        )
    } else if (reservation.canMarkPurchased) {
        PrimaryButton(
            text = stringResource(R.string.gift_detail_purchased),
            onClick = { actions.onSetPurchased(true) },
            leadingIcon = GiftIcons.Bag,
            isLoading = state.isWorking,
        )
    }
    TextLinkButton(
        stringResource(R.string.gift_detail_cancel_reservation),
        actions.onCancelReservation,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isWorking,
    )
}

/** Cagnotte en cours : progression pour tous les non-parents ; participants et « Modifier ma part » pour ceux qui en sont. */
@Composable
private fun PotActions(state: GiftDetailUiState, item: WishItem, actions: GiftDetailActions, onLeavePot: () -> Unit) {
    state.potProgress?.let { progress ->
        PotProgressCard(progress, myShareText = item.myContribution?.let { Money.format(it, progress.currency) })
    }
    if (item.isInMyPot) {
        if (state.participants.isNotEmpty()) Participants(state.participants)
        PrimaryButton(
            text = stringResource(R.string.gift_detail_edit_share),
            onClick = actions.onJoinPot,
            leadingIcon = GiftIcons.Edit,
            enabled = !state.isWorking,
        )
        TextLinkButton(stringResource(R.string.gift_detail_leave_pot), onLeavePot, modifier = Modifier.fillMaxWidth(), enabled = !state.isWorking)
    } else {
        SecondaryButton(
            text = stringResource(R.string.gift_detail_join_this_pot),
            onClick = actions.onJoinPot,
            leadingIcon = GiftIcons.Group,
            isLoading = state.isWorking,
        )
    }
    PrivacyCaption(stringResource(if (item.isInMyPot) R.string.gift_detail_privacy_pot_member else R.string.gift_detail_privacy_pot))
}

/** Participants (visibles des seuls participants) : avatar, prénom (« Moi » en gras), part en chiffres tabulaires. */
@Composable
private fun Participants(participants: List<PotParticipant>) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val me = stringResource(R.string.gift_detail_me)
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        SectionHeader(title = stringResource(R.string.gift_detail_participants))
        Column(modifier = Modifier.fillMaxWidth().fcCard(padding = spacing.m)) {
            participants.forEachIndexed { index, participant ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.separator)
                val name = if (participant.isMe) me else participant.displayName
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = HitTarget.minimum)
                        .semantics(mergeDescendants = true) { },
                ) {
                    ChildAvatar(name = participant.displayName, emoji = null, colorName = null, size = 32.dp, modifier = Modifier.clearAndSetSemantics { })
                    Text(
                        text = name,
                        style = if (participant.isMe) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(participant.amountText, style = type.callout.tabular(), color = colors.textSecondary)
                }
            }
        }
    }
}

/**
 * Petite célébration (« Réservé ! », « Tu participes ! ») : loutre `mascot_celebrate`, titre et message sur une
 * carte au centre, ressort à l'entrée, fondu seul si les animations sont réduites. Se retire après 1,8 s ou au
 * toucher ; annoncée par TalkBack (région live).
 */
@Composable
private fun CelebrationOverlay(celebration: Celebration?, onFinish: () -> Unit) {
    val reduceMotion = GiftTheme.reduceMotion
    var shown by remember { mutableStateOf<Celebration?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(celebration) {
        if (celebration == null) return@LaunchedEffect
        shown = celebration
        visible = true
        delay(1800)
        visible = false
        delay(300)
        onFinish()
    }
    val current = shown ?: return
    val (titleRes, messageRes) = when (current) {
        Celebration.RESERVED -> R.string.gift_detail_celebration_reserved_title to R.string.gift_detail_celebration_reserved_message
        Celebration.POT -> R.string.gift_detail_celebration_pot_title to R.string.gift_detail_celebration_pot_message
    }
    val title = stringResource(titleRes)
    val message = stringResource(messageRes)
    AnimatedVisibility(
        visible = visible,
        enter = if (reduceMotion) fadeIn(tween(150)) else fadeIn(tween(200)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 400f), initialScale = 0.8f),
        exit = if (reduceMotion) fadeOut(tween(150)) else fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = false },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
                modifier = Modifier
                    .padding(GiftTheme.spacing.xl)
                    .widthIn(max = 320.dp)
                    .fcCard(padding = GiftTheme.spacing.xxl)
                    .clearAndSetSemantics {
                        contentDescription = "$title $message"
                        liveRegion = LiveRegionMode.Polite
                    },
            ) {
                Image(painterResource(Illustration.MASCOT_CELEBRATE.res), contentDescription = null, modifier = Modifier.size(160.dp))
                Text(title, style = GiftTheme.type.title, color = GiftTheme.colors.textPrimary, textAlign = TextAlign.Center)
                Text(message, style = GiftTheme.type.body, color = GiftTheme.colors.textSecondary, textAlign = TextAlign.Center)
            }
        }
    }
}

// region Aperçus

private val previewActions = GiftDetailActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

private fun previewState(item: WishItem, isParent: Boolean, participants: List<PotParticipant> = emptyList()) = GiftDetailUiState(
    isLoading = false,
    item = item,
    links = GiftPreviewData.links(listOf(item)).getValue(item.id),
    child = GiftPreviewData.leo,
    isParentView = isParent,
    participants = participants,
)

@GiftPreviews
@Composable
private fun GiftDetailParentPreview() = ScreenPreviewFrame(height = 1000.dp) {
    GiftDetailScreen(previewState(GiftPreviewData.detailItem(status = null), isParent = true), previewActions)
}

@GiftPreviews
@Composable
private fun GiftDetailParentOwnReservationPreview() = ScreenPreviewFrame(height = 1000.dp) {
    GiftDetailScreen(previewState(GiftPreviewData.detailItem(status = null, mine = ReservationState.RESERVED), isParent = true), previewActions)
}

@GiftPreviews
@Composable
private fun GiftDetailAvailablePreview() = ScreenPreviewFrame(height = 1000.dp) {
    GiftDetailScreen(previewState(GiftPreviewData.detailItem(ItemStatus.AVAILABLE), isParent = false), previewActions)
}

@GiftPreviews
@Composable
private fun GiftDetailPotPreview() = ScreenPreviewFrame(height = 1200.dp) {
    GiftDetailScreen(
        previewState(
            GiftPreviewData.detailItem(ItemStatus.POT, contribution = BigDecimal("40")),
            isParent = false,
            participants = listOf(
                PotParticipant("Mamie Jo", BigDecimal("50"), "CHF", isMe = false),
                PotParticipant("Claire", BigDecimal("40"), "CHF", isMe = true),
                PotParticipant("Paul", BigDecimal("30"), "CHF", isMe = false),
            ),
        ),
        previewActions,
    )
}

@GiftPreviews
@Composable
private fun GiftDetailMinePurchasedPreview() = ScreenPreviewFrame(height = 1000.dp) {
    GiftDetailScreen(previewState(GiftPreviewData.detailItem(ItemStatus.MINE, mine = ReservationState.PURCHASED), isParent = false), previewActions)
}

@GiftPreviews
@Composable
private fun GiftDetailTakenPreview() = ScreenPreviewFrame(height = 1000.dp) {
    GiftDetailScreen(previewState(GiftPreviewData.detailItem(ItemStatus.TAKEN), isParent = false), previewActions)
}

@GiftPreviews
@Composable
private fun GiftDetailIdeaRejectedPreview() = ScreenPreviewFrame(height = 900.dp) {
    GiftDetailScreen(
        previewState(GiftPreviewData.idea(IdeaReview.REJECTED, "Il en a déjà fait cet été"), isParent = false).copy(isMyUserId = GiftPreviewData.id("me")),
        previewActions,
    )
}

// endregion

package ch.jonathanlemaine.giftmanager.ui.gifts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.gifts.ShareTargets
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.ui.children.ChildRow
import ch.jonathanlemaine.giftmanager.ui.common.TopBarTextAction
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Feuille « + » (iOS : `QuickAddGiftView`) : choisir la liste où ajouter. Mes enfants reçoivent des envies
 * (« Ajouter une envie pour… »), ceux des autres foyers des idées (« Proposer une idée pour… »), invisibles
 * pour leurs parents. Sans aucune liste : état vide « Aucun enfant ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    state: QuickAddUiState,
    onPick: (QuickAddTarget) -> Unit,
    onDismiss: () -> Unit,
    /** Mode partage (#79) : lien reçu d'une autre app ; titre « Ajouter ce lien à… » et domaine en sous-titre. */
    sharedLink: String? = null,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        QuickAddContent(state, onPick, onDismiss, sharedLink)
    }
}

/** Contenu de la feuille, séparé pour les aperçus (une `ModalBottomSheet` ne s'affiche pas en aperçu). */
@Composable
internal fun QuickAddContent(state: QuickAddUiState, onPick: (QuickAddTarget) -> Unit, onDismiss: () -> Unit, sharedLink: String? = null) {
    val spacing = GiftTheme.spacing
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(if (sharedLink != null) R.string.share_sheet_title else R.string.add_sheet_title),
                style = GiftTheme.type.title,
                color = GiftTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            TopBarTextAction(stringResource(R.string.add_sheet_close), onClick = onDismiss)
        }
        sharedLink?.let { SharedLinkCard(it) }
        if (state.isEmpty) {
            EmptyStateView(
                illustration = Illustration.EMPTY_BOX,
                title = stringResource(R.string.add_sheet_empty_title),
                message = stringResource(R.string.add_sheet_empty_message),
            )
        }
        if (state.myLists.isNotEmpty()) {
            TargetSection(stringResource(R.string.add_sheet_wish_for), state.myLists, onPick)
        }
        if (state.otherLists.isNotEmpty()) {
            TargetSection(stringResource(R.string.add_sheet_idea_for), state.otherLists, onPick)
            FCNotice(GiftIcons.EyeSlash, stringResource(R.string.add_sheet_notice), tone = FCNoticeTone.SURPRISE)
        }
    }
}

/**
 * Rappel du lien partagé (#79, iOS : aperçu de `ShareView`) : carte compacte bordée au rayon `field`, comme
 * [ch.jonathanlemaine.giftmanager.ui.components.StoreLinkRow] mais non cliquable — icône de lien `primary`,
 * « Lien partagé » en légende puis le domaine (`amazon.fr`). TalkBack : « Lien partagé : amazon.fr ».
 */
@Composable
private fun SharedLinkCard(link: String) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    val label = stringResource(R.string.share_sheet_link_label)
    val domain = ShareTargets.domain(link) ?: link
    val spoken = stringResource(R.string.share_sheet_link_spoken, domain)
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.separator, shape)
            .padding(horizontal = spacing.l, vertical = spacing.m)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        Icon(
            imageVector = GiftIcons.Link,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(type.callout.glyphSize(1.3f)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = type.caption, color = colors.textSecondary)
            Text(
                text = domain,
                style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Titre de section (`captionBold`, titre TalkBack) puis une carte de lignes [ChildRow] séparées par des filets. */
@Composable
private fun TargetSection(title: String, targets: List<QuickAddTarget>, onPick: (QuickAddTarget) -> Unit) {
    val colors = GiftTheme.colors
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
        Text(
            text = title,
            style = GiftTheme.type.captionBold,
            color = colors.textSecondary,
            modifier = Modifier.padding(start = spacing.l).semantics { heading() },
        )
        Column(modifier = Modifier.fillMaxWidth().fcCard(padding = 0.dp)) {
            targets.forEachIndexed { index, target ->
                if (index > 0) {
                    HorizontalDivider(thickness = 1.dp, color = colors.separator, modifier = Modifier.padding(start = spacing.l + 40.dp + spacing.m))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .fcClickable(onClick = { onPick(target) }, shape = GiftTheme.radius.cardShape, pressedScale = 1f)
                        .padding(horizontal = spacing.l, vertical = spacing.s),
                ) {
                    ChildRow(target.child, modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (target.kind == WishKind.IDEA) GiftIcons.Lightbulb else GiftIcons.ChevronRight,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(GiftTheme.type.callout.glyphSize(1.3f)),
                    )
                }
            }
        }
    }
}

// region Aperçus

@GiftPreviews
@Composable
private fun QuickAddPreview() = GiftManagerTheme {
    Column(Modifier.fillMaxWidth().padding(top = GiftTheme.spacing.l).background(GiftTheme.colors.background)) {
        QuickAddContent(
            QuickAddUiState(
                myLists = listOf(QuickAddTarget(GiftPreviewData.leo, WishKind.WISH)),
                otherLists = listOf(QuickAddTarget(GiftPreviewData.zoe, WishKind.IDEA), QuickAddTarget(GiftPreviewData.mamie, WishKind.IDEA)),
            ),
            onPick = {},
            onDismiss = {},
        )
    }
}

@GiftPreviews
@Composable
private fun QuickAddSharePreview() = GiftManagerTheme {
    Column(Modifier.fillMaxWidth().padding(top = GiftTheme.spacing.l).background(GiftTheme.colors.background)) {
        QuickAddContent(
            QuickAddUiState(
                myLists = listOf(QuickAddTarget(GiftPreviewData.leo, WishKind.WISH)),
                otherLists = listOf(QuickAddTarget(GiftPreviewData.zoe, WishKind.IDEA)),
            ),
            onPick = {},
            onDismiss = {},
            sharedLink = "https://www.galaxus.ch/fr/s1/product/lego-technic-42151-bugatti-bolide",
        )
    }
}

@GiftPreviews
@Composable
private fun QuickAddEmptyPreview() = GiftManagerTheme {
    Column(Modifier.fillMaxWidth().padding(top = GiftTheme.spacing.l).background(GiftTheme.colors.background)) {
        QuickAddContent(QuickAddUiState(), onPick = {}, onDismiss = {})
    }
}

// endregion

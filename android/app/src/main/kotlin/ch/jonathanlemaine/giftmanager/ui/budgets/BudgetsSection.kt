package ch.jonathanlemaine.giftmanager.ui.budgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.budgets.Budget
import ch.jonathanlemaine.giftmanager.core.budgets.BudgetMath
import ch.jonathanlemaine.giftmanager.ui.components.ApproxPriceText
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.SectionHeader
import ch.jonathanlemaine.giftmanager.ui.components.fcCard
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.GiftColors
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal
import java.util.UUID

/**
 * Section « Budgets » de Mes achats (iOS : `BudgetsSection`, #40) : en-tête avec « + », une [BudgetCard] par
 * budget (toucher = modifier), l'invitation [BudgetInvitation] tant qu'il n'y en a aucun, puis le total converti
 * indicatif quand les budgets s'additionnent sans recouvrement.
 */
@Composable
fun BudgetsSection(state: BudgetsUiState, onNewBudget: () -> Unit, onEditBudget: (Budget) -> Unit, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.s), modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = stringResource(R.string.budgets_title),
            actionIcon = GiftIcons.Add,
            onAction = onNewBudget,
            actionLabel = stringResource(R.string.budgets_add),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            if (state.showsInvitation) BudgetInvitation(onClick = onNewBudget)
            state.rows.forEach { row -> BudgetCard(row, onClick = { onEditBudget(row.budget) }) }
        }
        state.indicativeTotal?.let { total ->
            Text(
                text = total.text,
                style = GiftTheme.type.captionBold.tabular(),
                color = GiftTheme.colors.textSecondary,
                modifier = Modifier
                    .padding(top = spacing.xs, start = spacing.xs)
                    .clearAndSetSemantics { contentDescription = total.spokenText },
            )
        }
    }
}

/** Couleur de remplissage de la jauge. « Presque atteint » en `potFg`, jamais l'ambre (cf. DESIGN.md, écart `accentAmber`). */
private fun BudgetMath.Level.fill(colors: GiftColors): Color = when (this) {
    BudgetMath.Level.COMFORTABLE -> colors.secondary
    BudgetMath.Level.NEARLY_REACHED -> colors.potFg
    BudgetMath.Level.OVER -> colors.takenFg
}

/**
 * Carte d'un budget (iOS : `BudgetCard`) : périmètre, « 180 / 200 CHF » tabulaire, jauge capsule sur piste
 * `separator`, puis le niveau dit en texte **et** en icône (jamais par la couleur seule) et la jauge convertie.
 * Dépassé : montant et filet `takenFg`. Un seul nœud TalkBack : « Léo · Noël, 180 CHF dépensés sur 200 CHF,
 * Plus que 20 CHF ». [onClick] `null` : carte inerte (galerie).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetCard(row: BudgetRow, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val budget = row.budget
    val isOver = row.level == BudgetMath.Level.OVER
    val shape = GiftTheme.radius.cardShape

    val spoken = buildString {
        append(
            stringResource(
                R.string.budgets_card_spoken,
                budget.scopeTitle,
                BudgetMath.amountText(budget.spent, budget.currency),
                BudgetMath.amountText(budget.amount, budget.currency),
                row.statusText,
            ),
        )
        row.approxText?.let { approx ->
            val (spent, amount) = approx.removePrefix("≈").trim().split(" / ", limit = 2).let { it.first() to it.getOrElse(1) { "" } }
            append(", ")
            append(stringResource(R.string.budgets_approx_spoken, spent, amount))
        }
    }
    val editHint = stringResource(R.string.budgets_edit_hint)

    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.s),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.fcClickable(onClick = onClick, onClickLabel = editHint, shape = shape, pressedScale = 0.98f) else Modifier)
            // Filet posé avant `fcCard` : il suit le bord de la carte et se dessine par-dessus son fond.
            .then(if (isOver) Modifier.border(1.5.dp, colors.takenFg.copy(alpha = 0.5f), shape) else Modifier)
            .fcCard(padding = spacing.l)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = budget.scopeTitle,
                style = type.headline,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = row.gaugeText,
                style = type.callout.copy(fontWeight = FontWeight.SemiBold).tabular(),
                color = if (isOver) colors.takenFg else colors.textPrimary,
            )
        }
        BudgetGauge(progress = budget.progress.toFloat(), hasSpent = budget.spent.signum() > 0, fill = row.level.fill(colors))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BudgetStatus(row, modifier = Modifier.weight(1f))
            row.approxText?.let { ApproxPriceText(it, style = type.caption) }
        }
    }
}

/** « Reste 80 CHF » (discret), « Plus que 20 CHF » (jauge, `potFg`), « Dépassé de 45 € » (triangle, `takenFg`). */
@Composable
private fun BudgetStatus(row: BudgetRow, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val (icon, color, style) = when (row.level) {
        BudgetMath.Level.OVER -> Triple(GiftIcons.Warning, colors.takenFg, type.captionBold)
        BudgetMath.Level.NEARLY_REACHED -> Triple(GiftIcons.Gauge, colors.potFg, type.captionBold)
        BudgetMath.Level.COMFORTABLE -> Triple(null, colors.textSecondary, type.caption)
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(style.glyphSize())) }
        Text(text = row.statusText, style = style.tabular(), color = color)
    }
}

/**
 * Jauge capsule de 8 dp (grandit un peu avec la police, comme `@ScaledMetric`) : piste `separator`, remplissage
 * animé au ressort (instantané si animations réduites ; à sa valeur dans les aperçus). Toujours un bout visible
 * dès qu'une dépense existe. Décorative : la carte dit déjà tout à TalkBack.
 */
@Composable
private fun BudgetGauge(progress: Float, hasSpent: Boolean, fill: Color) {
    val track = GiftTheme.colors.separator
    val reduceMotion = GiftTheme.reduceMotion
    val height = 8.dp.scaledByFont(1.5f)
    val start = if (LocalInspectionMode.current) progress else 0f
    val shown = remember { Animatable(start) }
    LaunchedEffect(progress, reduceMotion) {
        // Ressort réponse 0,4 s, amortissement 0,85 (iOS).
        if (reduceMotion) shown.snapTo(progress) else shown.animateTo(progress, spring(dampingRatio = 0.85f, stiffness = 250f))
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(track, cornerRadius = radius)
        val value = shown.value.coerceIn(0f, 1f)
        val width = maxOf(size.width * value, if (hasSpent) size.height else 0f)
        if (width > 0f) drawRoundRect(fill, size = Size(width, size.height), cornerRadius = radius)
    }
}

/**
 * Invitation à créer un premier budget (iOS : `invitation`) : pastille `pastelBlue` / `mineFg`, « Définir un
 * budget » et sa précision ; toute la carte est le bouton.
 */
@Composable
fun BudgetInvitation(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val badge = HitTarget.minimum.scaledByFont(1.3f)
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .fcClickable(onClick = onClick, shape = GiftTheme.radius.cardShape)
            .fcCard(padding = spacing.m)
            .semantics(mergeDescendants = true) { },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(badge).background(colors.pastelBlue, CircleShape),
        ) {
            Icon(GiftIcons.BarChart, contentDescription = null, tint = colors.mineFg, modifier = Modifier.size(badge * 0.5f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.budgets_invitation_title), style = type.headline, color = colors.textPrimary)
            Text(stringResource(R.string.budgets_invitation_message), style = type.caption, color = colors.textSecondary)
        }
        Icon(
            GiftIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(type.callout.glyphSize()),
        )
    }
}

// region Aperçus

/** Lignes d'exemple pour les aperçus et la galerie : à l'aise, presque atteint, dépassé (budget en EUR, profil CHF). */
internal object BudgetPreviewData {
    private fun budget(child: String?, event: String?, spent: Int, amount: Int, currency: String) = Budget(
        id = UUID.nameUUIDFromBytes("$child$event".toByteArray()),
        childId = child?.let { UUID.nameUUIDFromBytes(it.toByteArray()) },
        childName = child,
        eventId = event?.let { UUID.nameUUIDFromBytes(it.toByteArray()) },
        eventTitle = event,
        amount = BigDecimal(amount),
        spent = BigDecimal(spent),
        currency = currency,
    )

    private fun row(budget: Budget, status: String, approx: String? = null) = BudgetRow(
        budget = budget,
        level = BudgetMath.level(budget),
        gaugeText = BudgetMath.gaugeText(budget.spent, budget.amount, budget.currency),
        statusText = status,
        approxText = approx,
    )

    val comfortable = row(budget("Léo", "Noël 2026", 120, 200, "CHF"), "Reste 80 CHF")
    val nearlyReached = row(budget("Zoé", null, 180, 200, "CHF"), "Plus que 20 CHF")
    val over = row(budget(null, "Anniversaire de Léo", 245, 200, "EUR"), "Dépassé de 45 €", approx = "≈ 230 / 188 CHF")
    val all = listOf(comfortable, nearlyReached, over)
}

@GiftPreviews
@Composable
private fun BudgetCardsPreview() = PreviewFrame {
    BudgetPreviewData.all.forEach { BudgetCard(it, onClick = {}) }
}

@GiftPreviews
@Composable
private fun BudgetInvitationPreview() = PreviewFrame {
    BudgetsSection(BudgetsUiState(hasLoaded = true), onNewBudget = {}, onEditBudget = {})
}

@GiftPreviews
@Composable
private fun BudgetsTotalPreview() = PreviewFrame {
    BudgetsSection(
        BudgetsUiState(
            rows = listOf(BudgetPreviewData.comfortable, BudgetPreviewData.over),
            hasLoaded = true,
            indicativeTotal = IndicativeTotal("≈ 350 sur 388 CHF au total · indicatif", "environ 350 sur 388 CHF au total"),
        ),
        onNewBudget = {},
        onEditBudget = {},
    )
}

// endregion

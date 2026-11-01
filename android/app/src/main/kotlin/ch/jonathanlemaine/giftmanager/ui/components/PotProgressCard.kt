package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.model.PotProgress
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular
import java.math.BigDecimal

/**
 * Progression d'une cagnotte (`PotProgressCard`, #35) : montant réuni, jauge vers le prix, participants.
 *
 * Jamais affichée à un parent (le serveur ne renvoie aucune donnée de cagnotte pour lui). Sans prix de
 * référence dans la devise de la cagnotte, pas de jauge : seulement le montant réuni, plutôt qu'une barre
 * inventée. La jauge se remplit avec un ressort, instantanément si les animations sont réduites.
 * Un seul nœud TalkBack, qui lit toute la phrase.
 */
@Composable
fun PotProgressCard(progress: PotProgress, modifier: Modifier = Modifier, myShareText: String? = null) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val description = potAccessibilityText(progress, myShareText)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fcCard()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp.scaledByFont()).background(colors.potBg, CircleShape),
            ) {
                Icon(GiftIcons.Group, contentDescription = null, tint = colors.potFg, modifier = Modifier.size(type.caption.glyphSize(1.3f)))
            }
            Text(stringResource(R.string.pot_title), style = type.headline, color = colors.textPrimary, modifier = Modifier.weight(1f))
            if (progress.isComplete) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier
                        .background(colors.availableBg, CapsuleShape)
                        .padding(horizontal = spacing.s + 2.dp, vertical = spacing.xs),
                ) {
                    Icon(GiftIcons.Check, contentDescription = null, tint = colors.availableFg, modifier = Modifier.size(type.captionBold.glyphSize()))
                    Text(stringResource(R.string.pot_goal_reached), style = type.captionBold, color = colors.availableFg, maxLines = 1)
                }
            }
        }

        val suffix = progress.targetText?.let { stringResource(R.string.pot_of_target, it) }
            ?: stringResource(R.string.pot_collected_suffix)
        Text(
            text = buildAnnotatedString {
                withStyle(type.title.tabular().toSpanStyle().copy(color = colors.textPrimary)) { append(progress.collectedText) }
                append("  ")
                withStyle(type.callout.tabular().toSpanStyle().copy(color = colors.textSecondary)) { append(suffix) }
            },
            style = type.title,
        )

        progress.fraction?.let { PotGauge(fraction = it.toFloat()) }

        Text(
            text = potDetailLine(progress, myShareText),
            style = type.caption.tabular(),
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun PotGauge(fraction: Float) {
    val colors = GiftTheme.colors
    val reduceMotion = GiftTheme.reduceMotion
    // Aperçus figés : jauge directement à sa valeur.
    val start = if (LocalInspectionMode.current) fraction else 0f
    val shown = remember { Animatable(start) }
    LaunchedEffect(fraction, reduceMotion) {
        // Ressort réponse 0,6 s, amortissement 0,85 (iOS).
        if (reduceMotion) shown.snapTo(fraction) else shown.animateTo(fraction, spring(dampingRatio = 0.85f, stiffness = 110f))
    }
    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(colors.potBg, cornerRadius = radius)
        val value = shown.value.coerceIn(0f, 1f)
        // Toujours un bout de jauge visible dès qu'il y a une participation.
        val width = if (value > 0f) maxOf(size.width * value, 10.dp.toPx()) else 0f
        if (width > 0f) drawRoundRect(colors.potFg, size = Size(width, size.height), cornerRadius = radius)
    }
}

@Composable
private fun potDetailLine(progress: PotProgress, myShareText: String?): String {
    val parts = mutableListOf(progress.participantsText)
    if (myShareText != null) parts += stringResource(R.string.pot_my_share, myShareText)
    val remaining = progress.remainingText
    if (remaining != null && !progress.isComplete) parts += stringResource(R.string.pot_remaining, remaining)
    return parts.joinToString(" · ")
}

@Composable
private fun potAccessibilityText(progress: PotProgress, myShareText: String?): String {
    val parts = mutableListOf(stringResource(R.string.pot_a11y_collected, progress.collectedText))
    progress.targetText?.let { parts[0] = parts[0] + " " + stringResource(R.string.pot_a11y_target, it) }
    parts += progress.participantsText
    if (myShareText != null) parts += stringResource(R.string.pot_a11y_my_share, myShareText)
    val remaining = progress.remainingText
    when {
        progress.isComplete -> parts += stringResource(R.string.pot_a11y_goal_reached)
        remaining != null -> parts += stringResource(R.string.pot_a11y_missing, remaining)
    }
    return parts.joinToString(", ")
}

@GiftPreviews
@Composable
private fun PotProgressCardPreview() = PreviewFrame {
    PotProgressCard(PotProgress(BigDecimal(120), "CHF", BigDecimal(199), 3), myShareText = "CHF 40.00")
    PotProgressCard(PotProgress(BigDecimal(210), "CHF", BigDecimal(199), 5))
    PotProgressCard(PotProgress(BigDecimal(60), "EUR", null, 2))
}

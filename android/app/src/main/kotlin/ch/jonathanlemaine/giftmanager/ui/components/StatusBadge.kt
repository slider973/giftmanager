package ch.jonathanlemaine.giftmanager.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftColors
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Libellé localisé du statut (ressource, pas la chaîne du modèle). */
@get:StringRes
val GiftStatus.labelRes: Int
    get() = when (this) {
        GiftStatus.AVAILABLE -> R.string.status_available
        GiftStatus.TAKEN -> R.string.status_taken
        GiftStatus.MINE -> R.string.status_mine
        GiftStatus.OWNED -> R.string.status_owned
        GiftStatus.POT -> R.string.status_pot
    }

fun GiftStatus.background(colors: GiftColors): Color = when (this) {
    GiftStatus.AVAILABLE -> colors.availableBg
    GiftStatus.TAKEN -> colors.takenBg
    GiftStatus.MINE -> colors.mineBg
    GiftStatus.OWNED -> colors.ownedBg
    GiftStatus.POT -> colors.potBg
}

fun GiftStatus.foreground(colors: GiftColors): Color = when (this) {
    GiftStatus.AVAILABLE -> colors.availableFg
    GiftStatus.TAKEN -> colors.takenFg
    GiftStatus.MINE -> colors.mineFg
    GiftStatus.OWNED -> colors.ownedFg
    GiftStatus.POT -> colors.potFg
}

/**
 * Pastille capsule pastel portant le statut d'un cadeau (`StatusBadge`). Paires fond/texte AA dans les
 * deux apparences. TalkBack : « Statut : Disponible ».
 */
@Composable
fun StatusBadge(status: GiftStatus, modifier: Modifier = Modifier) {
    val colors = GiftTheme.colors
    val label = stringResource(status.labelRes)
    val description = stringResource(R.string.status_a11y, label)
    Text(
        text = label,
        style = GiftTheme.type.captionBold,
        color = status.foreground(colors),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = modifier
            .background(status.background(colors), CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.s + GiftTheme.spacing.xs / 2, vertical = GiftTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = description },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@GiftPreviews
@Composable
private fun StatusBadgePreview() = PreviewFrame {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
    ) {
        GiftStatus.entries.forEach { StatusBadge(it) }
    }
}

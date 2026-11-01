package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Pastille de fonctionnalité (`FeaturePill`, en-tête de la maquette, onboarding) : icône colorée sur
 * disque pastel, titre et sous-titre centrés. [color] teinte l'icône ; le disque prend [background] (un
 * pastel du thème) ou, à défaut, [color] à 16 % sur `surface`. Paires conseillées : `heart`/`pastelPink`,
 * `availableFg`/`pastelMint`, `mineFg`/`pastelBlue`, `accentAmber`/`pastelPeach`, `ownedFg`/`pastelLavender`.
 * L'icône est décorative : titre et sous-titre sont lus ensemble.
 */
@Composable
fun FeaturePill(
    icon: ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    background: Color? = null,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val disc = 64.dp.scaledByFont(1.4f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = modifier.semantics(mergeDescendants = true) { },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(bottom = GiftTheme.spacing.xs)
                .size(disc)
                .background(background ?: color.copy(alpha = 0.16f).compositeOver(colors.surface), CircleShape),
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(disc * 0.42f))
        }
        Text(title, style = type.headline, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(subtitle, style = type.caption, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@GiftPreviews
@Composable
private fun FeaturePillPreview() = PreviewFrame {
    val colors = GiftTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xl)) {
        FeaturePill(GiftIcons.Group, colors.heart, "Groupes familiaux", "Plusieurs foyers, plusieurs pays", Modifier.weight(1f), colors.pastelPink)
        FeaturePill(GiftIcons.Gift, colors.availableFg, "Listes par événement", "Anniversaire, Noël, et plus", Modifier.weight(1f), colors.pastelMint)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xl)) {
        FeaturePill(GiftIcons.Lock, colors.accentAmber, "Surprise garantie", "« Déjà pris » sans savoir par qui", Modifier.weight(1f), colors.pastelPeach)
        FeaturePill(GiftIcons.Shield, colors.ownedFg, "Enfants protégés", "Les parents ne voient pas les réservations", Modifier.weight(1f), colors.pastelLavender)
    }
}

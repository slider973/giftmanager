package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular

/** Échelle de police à partir de laquelle les cartes passent en pile verticale (tailles d'accessibilité). */
internal const val ACCESSIBILITY_FONT_SCALE = 1.5f

/**
 * Carte d'un cadeau dans une liste (`GiftCard`, écran 3 de la maquette).
 *
 * Vignette à gauche ; titre, drapeau + prix (+ conversion indicative), boutique ; cœur en haut à droite ;
 * badge de statut en bas à droite. [status] `null` → **aucun badge** : mode surprise, quand un parent
 * consulte la liste de son propre enfant. À partir d'une police à 150 %, la carte s'empile verticalement
 * plutôt que d'écraser le titre.
 *
 * Le cœur est un indicateur : la carte entière est la cible ([onClick]). TalkBack lit la carte d'un bloc.
 */
@Composable
fun GiftCard(
    title: String,
    imageUrl: String?,
    priceText: String?,
    storeText: String?,
    countryCode: String?,
    isFavorite: Boolean,
    status: GiftStatus?,
    modifier: Modifier = Modifier,
    approxPriceText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val spacing = GiftTheme.spacing
    val isAccessibilitySize = LocalDensity.current.fontScale >= ACCESSIBILITY_FONT_SCALE
    val interaction = if (onClick != null) {
        Modifier.fcClickable(
            onClick = onClick,
            onClickLabel = stringResource(R.string.gift_open),
            shape = GiftTheme.radius.cardShape,
            pressedScale = 0.98f,
        )
    } else {
        Modifier
    }

    val cardModifier = modifier
        .fillMaxWidth()
        .then(interaction)
        .fcCard(padding = spacing.m)
        .semantics(mergeDescendants = true) { }

    if (isAccessibilitySize) {
        Column(modifier = cardModifier, verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            Row(verticalAlignment = Alignment.Top) {
                Thumbnail(title, imageUrl, side = 96.dp)
                Spacer(Modifier.weight(1f).width(spacing.s))
                PriorityHeart(isOn = isFavorite, modifier = Modifier.bleed(top = spacing.s, end = spacing.s))
            }
            Details(title, priceText, storeText, countryCode, approxPriceText, maxTitleLines = Int.MAX_VALUE)
            status?.let { StatusBadge(it) }
        }
    } else {
        Row(
            modifier = cardModifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Thumbnail(title, imageUrl, side = 76.dp.scaledByFont(1.3f))
            Details(
                title,
                priceText,
                storeText,
                countryCode,
                approxPriceText,
                maxTitleLines = 2,
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                PriorityHeart(isOn = isFavorite, modifier = Modifier.bleed(top = spacing.s, end = spacing.s))
                status?.let { StatusBadge(it, modifier = Modifier.padding(top = spacing.s)) }
            }
        }
    }
}

@Composable
private fun Thumbnail(title: String, imageUrl: String?, side: androidx.compose.ui.unit.Dp) {
    RemoteImage(
        url = imageUrl,
        placeholderSeed = title,
        modifier = Modifier
            .size(side)
            .clip(GiftTheme.radius.thumbShape)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun Details(
    title: String,
    priceText: String?,
    storeText: String?,
    countryCode: String?,
    approxPriceText: String?,
    maxTitleLines: Int,
    modifier: Modifier = Modifier,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = title,
            style = type.headline,
            color = colors.textPrimary,
            maxLines = maxTitleLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = spacing.xs / 2),
        )
        if (priceText != null || countryCode != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs + 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (countryCode != null) CountryFlag(countryCode, style = type.caption)
                if (priceText != null) {
                    Text(text = priceText, style = type.caption.tabular(), color = colors.textPrimary)
                }
                if (approxPriceText != null) ApproxPriceText(approxPriceText, style = type.caption)
            }
        }
        if (storeText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = GiftIcons.Bag,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(type.caption.glyphSize()),
                )
                Text(
                    text = storeText,
                    style = type.caption,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@GiftPreviews
@Composable
private fun GiftCardPreview() = PreviewFrame {
    GiftCard("LEGO Technic McLaren F1", null, "CHF 199.–", "Galaxus", "CH", isFavorite = true, status = GiftStatus.AVAILABLE)
    GiftCard("PlayStation 5 Pro", null, "CHF 799.–", "Digitec", "CH", isFavorite = false, status = GiftStatus.TAKEN)
    GiftCard(
        "Casque Sony WH-1000XM5", null, "€ 299,00", "Amazon.fr", "FR",
        isFavorite = true, status = GiftStatus.MINE, approxPriceText = "≈ 277 CHF",
    )
    GiftCard(
        "Maillot PSG 2025 avec un nom très long qui passe sur deux lignes", null, null, null, null,
        isFavorite = true, status = null,
    )
}

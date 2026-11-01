package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget
import ch.jonathanlemaine.giftmanager.ui.theme.tabular

/**
 * Ligne « Liens par pays » (`StoreLinkRow`, écran 5) : drapeau, boutique, prix (+ conversion indicative
 * dessous), icône de lien externe. Toute la ligne est cliquable ; TalkBack : « Galaxus, Suisse, CHF 199.– »
 * puis « Appuyez deux fois pour ouvrir le site de la boutique ».
 */
@Composable
fun StoreLinkRow(
    store: String,
    countryCode: String?,
    priceText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    approxPriceText: String? = null,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val shape = GiftTheme.radius.fieldShape
    val description = buildList {
        add(store)
        if (CountryFlagLogic.normalized(countryCode) != null) add(CountryFlagLogic.name(countryCode))
        priceText?.let(::add)
        approxPriceText?.let { add(stringResource(R.string.approx_price_spoken, approxAmount(it))) }
    }.joinToString(", ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .fcClickable(onClick = onClick, onClickLabel = stringResource(R.string.store_link_open), shape = shape, pressedScale = 0.98f)
            .heightIn(min = HitTarget.button.scaledByFont())
            .background(colors.surface, shape)
            .border(1.dp, colors.separator, shape)
            .padding(horizontal = spacing.l, vertical = spacing.s)
            .semantics { contentDescription = description },
    ) {
        Box(Modifier.clearAndSetSemantics { }) { CountryFlag(countryCode, style = type.title) }
        Text(
            text = store,
            style = type.callout,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).clearAndSetSemantics { },
        )
        if (priceText != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.clearAndSetSemantics { }) {
                Text(
                    text = priceText,
                    style = type.callout.copy(fontWeight = FontWeight.SemiBold).tabular(),
                    color = colors.textPrimary,
                )
                approxPriceText?.let { ApproxPriceText(it, style = type.caption) }
            }
        }
        Icon(
            imageVector = GiftIcons.OpenExternal,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(type.callout.glyphSize(1.2f)),
        )
    }
}

@GiftPreviews
@Composable
private fun StoreLinkRowPreview() = PreviewFrame {
    SectionHeader(title = "Liens par pays")
    StoreLinkRow("Galaxus (CH)", "CH", "CHF 199.–", onClick = {})
    StoreLinkRow("Amazon.fr (FR)", "FR", "€ 199,99", onClick = {}, approxPriceText = "≈ 185 CHF")
    StoreLinkRow("Boutique en ligne", null, null, onClick = {})
}

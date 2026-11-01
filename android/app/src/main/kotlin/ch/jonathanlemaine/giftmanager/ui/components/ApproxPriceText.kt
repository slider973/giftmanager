package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.tabular

/**
 * Prix converti indicatif, posé **après** le prix d'origine (« CHF 199.– ≈ 214 € ») — `ApproxPriceText`.
 * Toujours `textSecondary`, chiffres tabulaires ; ne prend jamais le pas sur le prix réel.
 * TalkBack : « environ 214 €, conversion indicative ». Hérite du style de texte ambiant.
 */
@Composable
fun ApproxPriceText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val spoken = stringResource(R.string.approx_price_spoken, approxAmount(text))
    Text(
        text = text,
        style = style.tabular(),
        color = GiftTheme.colors.textSecondary,
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    )
}

/** Montant sans le symbole « ≈ », pour la lecture vocale. */
internal fun approxAmount(text: String): String = text.replace("≈", "").trim()

/**
 * Mention sous des prix convertis (`IndicativeRateNote`) : « ≈ Conversion indicative, taux BCE du
 * 2 octobre. Le prix de la boutique fait foi. »
 */
@Composable
fun IndicativeRateNote(rateDateText: String?, modifier: Modifier = Modifier) {
    val style = GiftTheme.type.caption
    val color = GiftTheme.colors.textSecondary
    val text = rateDateText?.let { stringResource(R.string.rate_note_dated, it) } ?: stringResource(R.string.rate_note)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s)) {
        Text(
            text = stringResource(R.string.approx_symbol),
            style = style.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(text = text, style = style, color = color)
    }
}

@GiftPreviews
@Composable
private fun ApproxPriceTextPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs)) {
        Text("CHF 199.–", style = GiftTheme.type.caption.tabular(), color = GiftTheme.colors.textPrimary)
        ApproxPriceText("≈ 214 €", style = GiftTheme.type.caption)
    }
    IndicativeRateNote(rateDateText = "2 octobre")
    IndicativeRateNote(rateDateText = null)
}

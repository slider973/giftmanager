package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Drapeau d'un pays depuis son code ISO 3166-1 alpha-2 (« CH », « fr », « UK » → « GB ») — `CountryFlag`.
 * Code absent ou invalide : globe `textSecondary`. Hérite du style de texte ambiant (taille du drapeau).
 * TalkBack lit le nom du pays en français.
 */
@Composable
fun CountryFlag(code: String?, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    val flag = CountryFlagLogic.emoji(code)
    val name = if (flag == null) stringResource(R.string.country_unknown) else CountryFlagLogic.name(code)
    val semantics = modifier.clearAndSetSemantics { contentDescription = name }
    if (flag != null) {
        Text(text = flag, style = style, modifier = semantics)
    } else {
        Icon(
            imageVector = GiftIcons.Globe,
            contentDescription = null,
            tint = GiftTheme.colors.textSecondary,
            modifier = semantics.size(style.glyphSize(1.1f)),
        )
    }
}

@GiftPreviews
@Composable
private fun CountryFlagPreview() = PreviewFrame {
    Row(horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m), verticalAlignment = Alignment.CenterVertically) {
        listOf("CH", "FR", "DE", "IT", "BE", "uk", "", "X1").forEach { CountryFlag(it, style = GiftTheme.type.title) }
    }
}

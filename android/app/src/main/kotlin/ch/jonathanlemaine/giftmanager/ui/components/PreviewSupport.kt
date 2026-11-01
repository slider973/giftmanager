package ch.jonathanlemaine.giftmanager.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Aperçus standard d'un composant : clair, sombre, et police système à 200 %. */
@Preview(name = "Clair", widthDp = 360)
@Preview(name = "Sombre", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Police 200 %", widthDp = 360, fontScale = 2f)
internal annotation class GiftPreviews

/** Cadre d'aperçu : thème, fond d'écran, marge d'écran `xl`, éléments espacés de `m`. */
@Composable
internal fun PreviewFrame(content: @Composable ColumnScope.() -> Unit) {
    GiftManagerTheme {
        Column(
            modifier = Modifier
                .background(GiftTheme.colors.background)
                .padding(GiftTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
            content = content,
        )
    }
}

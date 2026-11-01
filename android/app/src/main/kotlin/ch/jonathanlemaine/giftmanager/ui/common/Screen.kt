package ch.jonathanlemaine.giftmanager.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Largeur maximale du contenu d'un écran : lignes lisibles sur tablette et en paysage. */
val ScreenContentMaxWidth: Dp = 560.dp

/**
 * Colonne d'écran défilante sur le fond de l'app (`ScrollView` + `.padding(Spacing.xl)` d'iOS).
 *
 * Les marges système (`safeDrawing`, clavier compris) sont posées **hors** du défilement : rien ne passe
 * sous la barre d'état, et le contenu remonte au-dessus du clavier. Un hôte qui a déjà appliqué une partie
 * des marges (le `Scaffold` des onglets) les consomme avant : elles ne sont pas comptées deux fois.
 * Contenu centré et limité à [ScreenContentMaxWidth] ; il défile en paysage et aux grandes polices.
 */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = GiftTheme.spacing.l,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(scrollState),
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .widthIn(max = ScreenContentMaxWidth)
                .fillMaxWidth()
                .padding(GiftTheme.spacing.xl),
            content = content,
        )
    }
}

/** Grand titre d'écran arrondi (`largeTitle`), titre TalkBack. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        style = GiftTheme.type.largeTitle,
        color = GiftTheme.colors.textPrimary,
        textAlign = textAlign,
        modifier = modifier.semantics { heading() },
    )
}

/** Texte courant secondaire (`body` / `textSecondary`). */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = text,
        style = GiftTheme.type.body.copy(lineHeight = GiftTheme.type.body.lineHeight * 1.08f),
        color = GiftTheme.colors.textSecondary,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/** Code lu caractère par caractère par TalkBack (« A B C 1 2 3 ») plutôt que comme un mot. */
fun spelledOut(code: String): String = code.toList().joinToString(" ")

/** Cadre d'aperçu d'un écran complet : thème et hauteur de téléphone bornée. */
@Composable
internal fun ScreenPreviewFrame(height: Dp = 760.dp, content: @Composable () -> Unit) = GiftManagerTheme {
    Box(Modifier.fillMaxWidth().height(height).background(GiftTheme.colors.background)) { content() }
}

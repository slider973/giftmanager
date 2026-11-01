package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * État vide illustré par une mascotte loutre (`EmptyStateView`) : illustration, titre (titre TalkBack),
 * message, et un CTA facultatif. L'illustration est décorative ; elle grandit un peu avec la police
 * (180 dp, plafonnée à 260 dp). Texte limité à 320 dp de large pour rester lisible sur tablette.
 */
@Composable
fun EmptyStateView(
    illustration: Illustration,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    val textWidth = Modifier.widthIn(max = 320.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.l),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xl, vertical = spacing.xxl),
    ) {
        Image(
            painter = painterResource(illustration.res),
            contentDescription = null,
            modifier = Modifier.size(minOf(180.dp.scaledByFont(1.45f), 260.dp)),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.s),
            modifier = textWidth,
        ) {
            Text(
                text = title,
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = type.body.copy(lineHeight = type.body.lineHeight * 1.08f),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            PrimaryButton(text = actionLabel, onClick = onAction, modifier = textWidth.padding(top = spacing.s))
        }
    }
}

@GiftPreviews
@Composable
private fun EmptyStateViewPreview() = PreviewFrame {
    EmptyStateView(
        Illustration.EMPTY_BOX,
        "Aucun cadeau pour l'instant",
        "Ajoutez une première envie depuis n'importe quelle boutique.",
        actionLabel = "Ajouter un cadeau",
        onAction = {},
    )
}

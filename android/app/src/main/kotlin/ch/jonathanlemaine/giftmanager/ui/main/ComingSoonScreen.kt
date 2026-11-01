package ch.jonathanlemaine.giftmanager.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration

/**
 * Onglet pas encore livré : grand titre de l'onglet ([title]), puis état vide illustré « Bientôt ici » et
 * [message] qui dit ce que l'onglet contiendra. Mêmes loutres que les états vides iOS de ces écrans.
 */
@Composable
fun ComingSoonScreen(illustration: Illustration, title: String, message: String, modifier: Modifier = Modifier) {
    ScreenColumn(modifier = modifier) {
        ScreenTitle(title)
        EmptyStateView(
            illustration = illustration,
            title = stringResource(R.string.coming_soon_title),
            message = message,
        )
    }
}

@GiftPreviews
@Composable
private fun ComingSoonPreview() = ScreenPreviewFrame {
    ComingSoonScreen(Illustration.MASCOT_THINKING, "Recherche", "Tu pourras bientôt chercher un cadeau dans toutes les listes de la famille.")
}

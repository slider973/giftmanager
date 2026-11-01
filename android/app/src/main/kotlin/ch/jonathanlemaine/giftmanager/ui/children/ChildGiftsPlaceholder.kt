package ch.jonathanlemaine.giftmanager.ui.children

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.GiftTopBar
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.TopBarNavigation
import ch.jonathanlemaine.giftmanager.ui.common.TopBarScreen
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration

/**
 * Remplaçant de la liste de cadeaux d'un enfant (#73) : la route `ChildGiftsRoute` existe déjà, #73 remplacera
 * ce composable sans changer sa signature de navigation. Écran poussé (barre d'onglets visible) : ← et le
 * prénom en titre, puis la loutre au cadeau et « La liste arrive bientôt ».
 */
@Composable
fun ChildGiftsPlaceholder(childName: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    TopBarScreen(
        modifier = modifier,
        topBar = { GiftTopBar(TopBarNavigation.BACK, onNavigate = onBack, title = childName) },
    ) {
        ScreenColumn(horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyStateView(
                illustration = Illustration.MASCOT_GIFT,
                title = stringResource(R.string.child_gifts_soon_title),
                message = stringResource(R.string.child_gifts_soon_message, childName.orEmpty()),
            )
        }
    }
}

@GiftPreviews
@Composable
private fun ChildGiftsPlaceholderPreview() = ScreenPreviewFrame { ChildGiftsPlaceholder(childName = "Léo", onBack = {}) }

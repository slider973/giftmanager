package ch.jonathanlemaine.giftmanager.ui.children

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton

/**
 * Remplaçant de la liste de cadeaux d'un enfant (#73) : la route `ChildGiftsRoute` existe déjà, #73 remplacera
 * ce composable sans changer sa signature de navigation.
 */
@Composable
fun ChildGiftsPlaceholder(childName: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScreenColumn(modifier = modifier) {
        TextLinkButton(text = stringResource(R.string.back), onClick = onBack)
        EmptyStateView(
            illustration = Illustration.MASCOT_GIFT,
            title = stringResource(R.string.child_gifts_soon_title),
            message = stringResource(R.string.child_gifts_soon_message, childName.orEmpty()),
        )
    }
}

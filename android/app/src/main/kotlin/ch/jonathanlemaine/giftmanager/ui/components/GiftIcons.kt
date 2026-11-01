package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import ch.jonathanlemaine.giftmanager.R

/**
 * Icônes du design system, équivalents Material des SF Symbols utilisés sur iOS. Celles absentes de
 * `material-icons-core` sont des vector drawables (`res/drawable/ic_*.xml`), sans `icons-extended`.
 */
object GiftIcons {
    val Add: ImageVector get() = Icons.Filled.Add
    val Check: ImageVector get() = Icons.Filled.CheckCircle
    val Heart: ImageVector get() = Icons.Filled.Favorite
    val HeartOutline: ImageVector get() = Icons.Filled.FavoriteBorder
    /** Chevron « suivant », inversé automatiquement en droite-à-gauche. */
    val ChevronRight: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val Lock: ImageVector get() = Icons.Filled.Lock
    val Search: ImageVector get() = Icons.Filled.Search
    val Share: ImageVector get() = Icons.Filled.Share
    val Warning: ImageVector get() = Icons.Filled.Warning

    val Gift: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_gift)
    val Bag: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_shopping_bag)
    val Globe: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_public)
    val OpenExternal: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_open_in_new)
    val Group: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_group)
    val EyeSlash: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_visibility_off)
    val Archive: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_archive)
    val Link: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_link)
    val Error: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_error)
    val Shield: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_verified_user)
    val Clear: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_cancel)
}

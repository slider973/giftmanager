package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
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
    /** Coche simple (famille choisie, élément sélectionné d'un menu). */
    val Checkmark: ImageVector get() = Icons.Filled.Check
    /** Flèche « valider et continuer » (`arrow.right`), inversée en droite-à-gauche. */
    val ArrowForward: ImageVector get() = Icons.AutoMirrored.Filled.ArrowForward
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector get() = Icons.Filled.Close
    val AddCircle: ImageVector get() = Icons.Filled.AddCircle
    val DropDown: ImageVector get() = Icons.Filled.ArrowDropDown
    val Home: ImageVector get() = Icons.Filled.Home
    val Person: ImageVector get() = Icons.Filled.Person
    val Profile: ImageVector get() = Icons.Filled.AccountCircle
    val Mail: ImageVector get() = Icons.Filled.MailOutline
    /** Enveloppe pleine (`envelope.fill`) : « Dire merci ». */
    val MailFilled: ImageVector get() = Icons.Filled.Email
    /** Envoyer (`paperplane.fill`), inversé en droite-à-gauche. */
    val Send: ImageVector get() = Icons.AutoMirrored.Filled.Send
    val Info: ImageVector get() = Icons.Filled.Info
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Delete: ImageVector get() = Icons.Filled.Delete
    val More: ImageVector get() = Icons.Filled.MoreVert
    val Calendar: ImageVector get() = Icons.Filled.DateRange
    val SignOut: ImageVector get() = Icons.AutoMirrored.Filled.ExitToApp
    /** Monter / descendre un souhait (mode « Ordonner »). */
    val MoveUp: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val MoveDown: ImageVector get() = Icons.Filled.KeyboardArrowDown

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
    val Sparkles: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_auto_awesome)
    val Cake: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_cake)
    val PersonAdd: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_person_add)
    val Key: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_key)
    val Code: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_tag)
    val Money: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_payments)
    val Hourglass: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_hourglass)
    /** Idée (`lightbulb`). */
    val Lightbulb: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_lightbulb)
    val Camera: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_photo_camera)
    val Paste: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_content_paste)
    val RemoveCircle: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_remove_circle)
    /** « Ordonner » (`arrow.up.arrow.down`). */
    val Reorder: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_swap_vert)
    /** « Remettre dans la liste » (`arrow.uturn.left`). */
    val Undo: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_undo)
    /** « Il l'a déjà » (`checkmark.seal`). */
    val Verified: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_verified)
    /** Budgets (`chart.bar.fill`). */
    val BarChart: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_bar_chart)
    /** Budget presque atteint (`gauge.with.dots.needle.67percent`). */
    val Gauge: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_speed)
    /** Case ronde vide (`circle`) : bascule « acheté » décochée. */
    val CircleOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_radio_unchecked)
    /** Prix en baisse (`arrow.down.right`). */
    val TrendDown: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_south_east)
    /** Prix en hausse (`arrow.up.right`). */
    val TrendUp: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_north_east)
    /** Prix stable (`equal`). */
    val TrendFlat: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_equal)
    /** « Plus en stock » (`shippingbox`). */
    val OutOfStock: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_inventory)
    /** Alerte, notification (`bell`). */
    val Bell: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_notifications_none)
}

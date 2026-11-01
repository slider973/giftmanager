package ch.jonathanlemaine.giftmanager.ui.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.format.Formatting
import ch.jonathanlemaine.giftmanager.core.model.Child
import ch.jonathanlemaine.giftmanager.ui.components.ChildAvatar
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Diamètre de l'avatar d'une [ChildRow] (iOS : 40 pt). */
val ChildRowAvatarSize = 40.dp

/**
 * Ligne d'une liste (iOS : `ChildRow`) : avatar 40 dp, prénom en `headline`, puis « 8 ans · Foyer Martin »
 * ou « Liste d'adulte · … » en légende. [householdName] `null` quand le foyer est déjà dit par le contexte
 * (carte du foyer). L'avatar est décoratif : le prénom est lu une fois, par le texte.
 */
@Composable
fun ChildRow(child: Child, modifier: Modifier = Modifier, householdName: String? = null) {
    val kind = if (child.isAdult) stringResource(R.string.child_adult_list) else Formatting.ageText(child.age())
    val subtitle = listOfNotNull(kind, householdName).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.m),
        modifier = modifier,
    ) {
        ChildAvatar(
            name = child.firstName,
            emoji = child.avatarEmoji,
            colorName = child.avatarColor,
            photoUrl = child.avatarUrl,
            size = ChildRowAvatarSize,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = child.firstName, style = GiftTheme.type.headline, color = GiftTheme.colors.textPrimary)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = GiftTheme.type.caption, color = GiftTheme.colors.textSecondary)
            }
        }
    }
}

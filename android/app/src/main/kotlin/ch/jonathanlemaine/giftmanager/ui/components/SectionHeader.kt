package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * En-tête de section (`SectionHeader`) : « Événements à venir », avec un bouton rond `primary` optionnel.
 * Le titre est un titre TalkBack (navigation par titres) ; [actionLabel] nomme le bouton (« Ajouter » par
 * défaut).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    val colors = GiftTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        modifier = modifier.fillMaxWidth().heightIn(min = HitTarget.minimum),
    ) {
        Text(
            text = title,
            style = GiftTheme.type.headline,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionIcon != null && onAction != null) {
            val label = actionLabel ?: stringResource(R.string.section_add)
            val badge = 32.dp.scaledByFont(1.4f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .bleed(end = GiftTheme.spacing.s)
                    .sizeIn(minWidth = HitTarget.minimum, minHeight = HitTarget.minimum)
                    .fcClickable(onClick = onAction, shape = CircleShape, pressedScale = 0.9f)
                    .semantics { contentDescription = label },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(badge).background(colors.primary, CircleShape),
                ) {
                    Icon(actionIcon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(badge * 0.6f))
                }
            }
        }
    }
}

@GiftPreviews
@Composable
private fun SectionHeaderPreview() = PreviewFrame {
    SectionHeader("Événements à venir", actionIcon = GiftIcons.Add, onAction = {}, actionLabel = "Ajouter un événement")
    SectionHeader("Événements passés")
}

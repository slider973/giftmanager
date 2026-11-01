package ch.jonathanlemaine.giftmanager.ui.gifts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.glyphSize
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Une entrée d'un menu d'actions (menu ⋮ d'une carte ou de la fiche). [destructive] : libellé et icône en
 * `takenFg` semi-gras, comme l'action destructive d'une alerte.
 */
internal data class GiftMenuEntry(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/** Menu déroulant aux couleurs de l'app : carte `surface`, rayon `field`, lignes ≥ 48 dp. */
@Composable
internal fun GiftActionsMenu(expanded: Boolean, onDismiss: () -> Unit, entries: List<GiftMenuEntry>) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = GiftTheme.radius.fieldShape,
        containerColor = colors.surface,
    ) {
        entries.forEach { entry ->
            DropdownMenuItem(
                text = {
                    Text(
                        text = entry.label,
                        style = if (entry.destructive) type.callout.copy(fontWeight = FontWeight.SemiBold) else type.callout,
                        color = if (entry.destructive) colors.takenFg else colors.textPrimary,
                    )
                },
                leadingIcon = { Icon(entry.icon, contentDescription = null, tint = entry.tint) },
                onClick = {
                    onDismiss()
                    entry.onClick()
                },
                modifier = Modifier.heightIn(min = HitTarget.minimum),
            )
        }
    }
}

/**
 * Bouton rond 48 dp, icône seule (⋮, ↑, ↓, coller, retirer) : [label] est lu par TalkBack. Désactivé, il reste
 * visible à 38 % pour garder la mise en page stable.
 */
@Composable
internal fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = GiftTheme.colors.primary,
    enabled: Boolean = true,
    role: Role = Role.Button,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(HitTarget.minimum)
            .fcClickable(onClick = onClick, enabled = enabled, role = role, shape = CircleShape, pressedScale = 0.9f)
            .semantics { contentDescription = label },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/**
 * Mention de confidentialité centrée sous les actions (« Personne ne saura que c'est toi… ») : cadenas +
 * légende `textSecondary`, lus d'un bloc.
 */
@Composable
internal fun PrivacyCaption(text: String, modifier: Modifier = Modifier) {
    val type = GiftTheme.type
    val colors = GiftTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.xs, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GiftTheme.spacing.l)
            .clearAndSetSemantics { contentDescription = text },
    ) {
        Icon(GiftIcons.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(type.caption.glyphSize()))
        Text(text = text, style = type.caption, color = colors.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Libellé visible d'un champ (`captionBold` / `textSecondary`), jamais de placeholder seul. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = GiftTheme.type.captionBold, color = GiftTheme.colors.textSecondary, modifier = modifier)
}

/** « Claire », « Claire et Paul », « Claire, Paul et Léa ». */
@Composable
internal fun joinedNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names.first()
    else -> stringResource(R.string.add_gift_names_pair, names.dropLast(1).joinToString(", "), names.last())
}

/** Ouvre [url] dans le navigateur (lien d'une boutique). Sans navigateur installé, ne fait rien. */
internal fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Aucune app ne sait ouvrir ce lien : rien à faire, le lien reste visible.
    }
}

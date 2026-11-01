package ch.jonathanlemaine.giftmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/** Opacité d'un bouton désactivé (iOS : 45 %). */
private const val DISABLED_ALPHA = 0.45f

/**
 * Pilule pleine largeur commune. [leadingIcon] se place avant le libellé (« 🎁 Ajouter à la liste ») ;
 * [trailingIcon] (chevron, flèche) se cale contre le bord droit, comme sur la maquette (« Continuer › »).
 */
@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
    fill: Color,
    modifier: Modifier,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    isLoading: Boolean,
    enabled: Boolean,
) {
    val content = GiftTheme.colors.onPrimary
    val loadingText = stringResource(R.string.loading)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .fcClickable(onClick = onClick, enabled = enabled && !isLoading)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = HitTarget.button)
            .background(fill, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.s)
            .semantics { if (isLoading) stateDescription = loadingText },
    ) {
        PillContent(
            text = text,
            color = content,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            modifier = Modifier.alpha(if (isLoading) 0f else 1f),
        )
        if (isLoading) {
            CircularProgressIndicator(color = content, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PillContent(
    text: String,
    color: Color,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    modifier: Modifier = Modifier,
) {
    val style = GiftTheme.type.headline
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
            modifier = Modifier.padding(horizontal = if (trailingIcon != null) GiftTheme.spacing.xl else 0.dp),
        ) {
            leadingIcon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(style.glyphSize())) }
            Text(text = text, style = style, color = color, textAlign = TextAlign.Center)
        }
        trailingIcon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = color,
                modifier = Modifier.align(Alignment.CenterEnd).size(GiftTheme.type.callout.glyphSize(1.4f)),
            )
        }
    }
}

/** CTA principal bleu nuit (`PrimaryButton`) : « Continuer », « Ajouter à la liste ». Un seul par écran. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) = PillButton(text, onClick, GiftTheme.colors.primary, modifier, leadingIcon, trailingIcon, isLoading, enabled)

/** CTA vert (`SecondaryButton`) : « Ajouter à la liste » sur la fiche cadeau. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) = PillButton(text, onClick, GiftTheme.colors.secondary, modifier, leadingIcon, trailingIcon, isLoading, enabled)

/** Lien texte discret `primary` (`TextLinkButton`) : « Modifier les informations ». Cible ≥ 48 dp. */
@Composable
fun TextLinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fcClickable(onClick = onClick, enabled = enabled, shape = GiftTheme.radius.fieldShape, pressedScale = 1f)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .heightIn(min = HitTarget.minimum)
            .padding(horizontal = GiftTheme.spacing.s),
    ) {
        Text(
            text = text,
            style = GiftTheme.type.callout.copy(fontWeight = FontWeight.Medium),
            color = GiftTheme.colors.primary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Style de [FCPillLabel]. */
enum class FCPillStyle { PRIMARY, SECONDARY }

/**
 * Apparence d'un [PrimaryButton] / [SecondaryButton] pour un hôte qui gère lui-même le clic (menu, partage
 * système) — `FCPillLabel`. Poser `fcClickable` (ou `fcPressable`) sur l'hôte pour le retour tactile.
 */
@Composable
fun FCPillLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: FCPillStyle = FCPillStyle.PRIMARY,
) {
    val colors = GiftTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HitTarget.button)
            .background(if (style == FCPillStyle.PRIMARY) colors.primary else colors.secondary, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.s),
    ) {
        PillContent(text = text, color = colors.onPrimary, leadingIcon = icon, trailingIcon = null)
    }
}

@GiftPreviews
@Composable
private fun ButtonsPreview() = PreviewFrame {
    PrimaryButton("Continuer", onClick = {}, trailingIcon = GiftIcons.ChevronRight)
    PrimaryButton("Ajouter à la liste", onClick = {}, isLoading = true)
    PrimaryButton("Continuer", onClick = {}, enabled = false)
    SecondaryButton("Ajouter à la liste", onClick = {}, leadingIcon = GiftIcons.Gift)
    TextLinkButton("Modifier les informations", onClick = {})
    FCPillLabel("Partager l'invitation", icon = GiftIcons.Share)
}

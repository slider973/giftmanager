package ch.jonathanlemaine.giftmanager.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.components.scaledByFont
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Couleurs du bouton « Sign in with Google » (règles de marque Google, variante Android / Material).
 * Imposées par Google, elles sont **hors tokens** et ne servent qu'à ce bouton. Valeurs ARGB pour que
 * `GoogleSignInButtonContrastTest` puisse les vérifier.
 */
internal object GoogleButtonColors {
    const val LIGHT_CONTAINER = 0xFFFFFFFF
    const val LIGHT_BORDER = 0xFF747775
    const val LIGHT_CONTENT = 0xFF1F1F1F

    const val DARK_CONTAINER = 0xFF131314
    const val DARK_BORDER = 0xFF8E918F
    const val DARK_CONTENT = 0xFFE3E3E3
}

/** Opacité du libellé pendant la connexion : lisible, mais visiblement en attente (iOS : 0,6). */
private const val BUSY_CONTENT_ALPHA = 0.6f

/** Opacité d'un bouton désactivé hors chargement (règle Google : 38 %). */
private const val DISABLED_ALPHA = 0.38f

/**
 * Bouton « Se connecter avec Google » conforme aux règles de marque Google : pilule, logo « G » multicolore
 * officiel ([R.drawable.ic_google_g], jamais teinté), libellé en police système Medium, fond blanc / bordure
 * grise en clair, fond anthracite en sombre. Le thème suit celui de l'app ([GiftTheme.colors]`.isDark`),
 * pas la configuration système, pour rester juste dans la galerie.
 *
 * Hauteur [HitTarget.button] (52 dp), comme les autres pilules de l'app : au-delà des 40 dp minimaux de
 * Google et de la cible tactile de 48 dp. Le libellé passe à la ligne aux très grandes polices plutôt que
 * d'être tronqué.
 *
 * [isLoading] : bouton désactivé, le logo cède sa place à un indicateur de progression de même taille
 * (aucun saut de mise en page), et TalkBack annonce « Connexion en cours ».
 */
@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val dark = GiftTheme.colors.isDark
    val container = Color(if (dark) GoogleButtonColors.DARK_CONTAINER else GoogleButtonColors.LIGHT_CONTAINER)
    val border = Color(if (dark) GoogleButtonColors.DARK_BORDER else GoogleButtonColors.LIGHT_BORDER)
    val content = Color(if (dark) GoogleButtonColors.DARK_CONTENT else GoogleButtonColors.LIGHT_CONTENT)
    val style = GiftTheme.type.callout.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium)
    val logoSize = 20.dp.scaledByFont(1.4f)
    val busy = stringResource(R.string.sign_in_in_progress)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .fcClickable(onClick = onClick, enabled = enabled && !isLoading)
            .alpha(if (enabled || isLoading) 1f else DISABLED_ALPHA)
            .heightIn(min = HitTarget.button)
            .background(container, CapsuleShape)
            .border(1.dp, border, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.s)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                if (isLoading) stateDescription = busy
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            // 10 dp entre le logo et le libellé : écart prescrit par Google.
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(logoSize)) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = content,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(logoSize * 0.9f),
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_google_g),
                        contentDescription = null,
                        modifier = Modifier.size(logoSize),
                    )
                }
            }
            Text(
                text = stringResource(R.string.sign_in_with_google),
                style = style,
                color = content,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(if (isLoading) BUSY_CONTENT_ALPHA else 1f),
            )
        }
    }
}

@GiftPreviews
@Composable
private fun GoogleSignInButtonPreview() = PreviewFrame {
    GoogleSignInButton(onClick = {})
    GoogleSignInButton(onClick = {}, isLoading = true)
    GoogleSignInButton(onClick = {}, enabled = false)
}

@Preview(name = "Sur fond blanc", widthDp = 360)
@Composable
private fun GoogleSignInButtonOnSurfacePreview() = PreviewFrame {
    Box(Modifier.background(GiftTheme.colors.surface).padding(GiftTheme.spacing.l)) {
        GoogleSignInButton(onClick = {})
    }
}

package ch.jonathanlemaine.giftmanager.ui.auth

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
import androidx.compose.material3.Icon
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
 * Couleurs du bouton « Sign in with Apple » (Human Interface Guidelines, variante web / autre plateforme) :
 * noir sur fond clair, blanc sur fond sombre. Imposées par Apple, elles sont **hors tokens** et ne servent
 * qu'à ce bouton. Valeurs ARGB pour que `AppleSignInButtonContrastTest` puisse les vérifier.
 */
internal object AppleButtonColors {
    const val LIGHT_CONTAINER = 0xFF000000
    const val LIGHT_CONTENT = 0xFFFFFFFF

    const val DARK_CONTAINER = 0xFFFFFFFF
    const val DARK_CONTENT = 0xFF000000

    /** Contour 1 dp du bouton blanc (style « white outline » d'Apple), qui en fixe le bord sur toute surface. */
    const val DARK_BORDER = 0xFF000000
}

/** Opacité du libellé pendant la connexion, comme pour Google. */
private const val BUSY_CONTENT_ALPHA = 0.6f

/** Opacité d'un bouton désactivé hors chargement, comme pour Google (38 %). */
private const val DISABLED_ALPHA = 0.38f

/** Hauteur du logo dans sa case : la pomme, plus haute que large, s'aligne optiquement sur le « G » de Google. */
private const val LOGO_OPTICAL_RATIO = 0.9f

/**
 * Bouton « Se connecter avec Apple » conforme aux HIG « Sign in with Apple » (variante hors plateforme
 * Apple) : pilule noire, logo et libellé blancs en thème clair ; pilule blanche cernée, logo et libellé noirs
 * en thème sombre. Logo officiel ([R.drawable.ic_apple_logo]) monochrome, teinté de la couleur du libellé.
 * Le thème suit celui de l'app ([GiftTheme.colors]`.isDark`), comme [GoogleSignInButton].
 *
 * Jumeau de [GoogleSignInButton] : même hauteur ([HitTarget.button], 52 dp), même forme, même police
 * système Medium, même case de logo et même écart logo / libellé, pour qu'aucun des deux ne paraisse
 * secondaire. Le libellé passe à la ligne aux très grandes polices plutôt que d'être tronqué.
 *
 * [isLoading] : bouton désactivé, le logo cède sa place à un indicateur de même taille (aucun saut de mise
 * en page), et TalkBack annonce « Connexion en cours ». Le logo est décoratif : le libellé suffit.
 */
@Composable
fun AppleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val dark = GiftTheme.colors.isDark
    val container = Color(if (dark) AppleButtonColors.DARK_CONTAINER else AppleButtonColors.LIGHT_CONTAINER)
    val content = Color(if (dark) AppleButtonColors.DARK_CONTENT else AppleButtonColors.LIGHT_CONTENT)
    // Bordure seulement sur la variante blanche ; sur la noire elle serait invisible.
    val border = if (dark) Color(AppleButtonColors.DARK_BORDER) else container
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
            // Même écart que le bouton Google (10 dp), pour des libellés alignés d'un bouton à l'autre.
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
                    Icon(
                        painter = painterResource(R.drawable.ic_apple_logo),
                        contentDescription = null,
                        tint = content,
                        modifier = Modifier.size(logoSize * LOGO_OPTICAL_RATIO),
                    )
                }
            }
            Text(
                text = stringResource(R.string.sign_in_with_apple),
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
private fun AppleSignInButtonPreview() = PreviewFrame {
    AppleSignInButton(onClick = {})
    AppleSignInButton(onClick = {}, isLoading = true)
    AppleSignInButton(onClick = {}, enabled = false)
}

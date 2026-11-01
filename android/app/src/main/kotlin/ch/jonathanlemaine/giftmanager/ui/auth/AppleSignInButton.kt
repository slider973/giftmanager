package ch.jonathanlemaine.giftmanager.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.fcClickable
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import ch.jonathanlemaine.giftmanager.ui.theme.HitTarget

/**
 * Bouton « Se connecter avec Apple » (#83), version de câblage : pilule noire (blanche en sombre), même
 * hauteur que le bouton Google. Le logo et les marges de la charte Apple restent à poser par l'ui-designer.
 */
@Composable
fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = GiftTheme.colors.isDark
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .fcClickable(onClick = onClick, enabled = enabled)
            .alpha(if (enabled) 1f else 0.38f)
            .heightIn(min = HitTarget.button)
            .background(if (dark) Color.White else Color.Black, CapsuleShape)
            .padding(horizontal = GiftTheme.spacing.xl, vertical = GiftTheme.spacing.s),
    ) {
        Text(
            text = stringResource(R.string.sign_in_with_apple),
            style = GiftTheme.type.callout.copy(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium),
            color = if (dark) Color.Black else Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

package ch.jonathanlemaine.giftmanager.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Écran de connexion (câblage minimal, à redessiner par le ui-designer).
 * Purement déclaratif : [onSignIn] et [onDismiss] relaient vers [SignInViewModel].
 */
@Composable
fun SignInScreen(state: SignInUiState, onSignIn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.l, Alignment.CenterVertically),
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = spacing.xl, vertical = spacing.l),
    ) {
        Text(
            text = stringResource(R.string.sign_in_title),
            style = GiftTheme.type.largeTitle,
            color = GiftTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.sign_in_message),
            style = GiftTheme.type.body,
            color = GiftTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (state is SignInUiState.Error) {
            FCNotice(
                icon = GiftIcons.Warning,
                text = stringResource(state.kind.messageRes()),
                tone = FCNoticeTone.WARNING,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextLinkButton(text = stringResource(R.string.sign_in_error_dismiss), onClick = onDismiss)
        }
        PrimaryButton(
            text = stringResource(R.string.sign_in_with_google),
            onClick = onSignIn,
            isLoading = state is SignInUiState.InProgress,
        )
    }
}

private fun SignInUiState.Kind.messageRes(): Int = when (this) {
    SignInUiState.Kind.NoGoogleAccount -> R.string.sign_in_error_no_account
    SignInUiState.Kind.Network -> R.string.sign_in_error_network
    SignInUiState.Kind.NotConfigured -> R.string.sign_in_error_not_configured
    SignInUiState.Kind.GoogleUnavailable -> R.string.sign_in_error_unavailable
    SignInUiState.Kind.Generic -> R.string.sign_in_error_generic
}

@Preview(name = "Connexion", widthDp = 360, heightDp = 640)
@Composable
private fun SignInPreview() = GiftManagerTheme { SignInScreen(SignInUiState.Idle, onSignIn = {}, onDismiss = {}) }

@Preview(name = "Erreur réseau", widthDp = 360, heightDp = 640)
@Composable
private fun SignInErrorPreview() = GiftManagerTheme {
    SignInScreen(SignInUiState.Error(SignInUiState.Kind.Network), onSignIn = {}, onDismiss = {})
}

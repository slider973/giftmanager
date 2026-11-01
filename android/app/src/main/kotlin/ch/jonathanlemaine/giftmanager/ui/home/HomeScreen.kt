package ch.jonathanlemaine.giftmanager.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/**
 * Écran purement déclaratif : toute la logique vit dans [HomeViewModel].
 * [onOpenGallery] n'est fourni qu'en debug (galerie du design system) ; `null` → aucun lien.
 * [onSignOut] déconnecte l'utilisateur ; `null` → aucun bouton.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    onOpenGallery: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
) {
    val spacing = GiftTheme.spacing
    Column(
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Contenu centré, mais défilable en paysage ou en très grande police plutôt que rogné.
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.l, Alignment.CenterVertically),
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xl, vertical = spacing.l),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.l),
                    modifier = Modifier.widthIn(max = 420.dp),
                ) { HomeContent(state) }
            }
        }
        if (onSignOut != null && state is HomeUiState.SignedIn) {
            TextLinkButton(
                text = stringResource(R.string.sign_out),
                onClick = onSignOut,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = spacing.s),
            )
        }
        if (onOpenGallery != null) {
            TextLinkButton(
                text = stringResource(R.string.home_gallery_link),
                onClick = onOpenGallery,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = spacing.s),
            )
        }
    }
}

@Composable
private fun HomeContent(state: HomeUiState) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    if (state == HomeUiState.Loading) {
        GiftLoadingView(label = stringResource(R.string.home_session_loading))
        return
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            text = stringResource(R.string.home_title),
            style = type.largeTitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_tagline),
            style = type.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when (state) {
        HomeUiState.Error -> FCNotice(
            icon = GiftIcons.Warning,
            text = stringResource(R.string.home_error),
            tone = FCNoticeTone.WARNING,
            modifier = live,
        )
        HomeUiState.SignedOut -> StatusLine(stringResource(R.string.home_signed_out), live)
        is HomeUiState.SignedIn -> StatusLine(stringResource(R.string.home_signed_in, state.displayName), live)
        HomeUiState.Loading -> Unit
    }
}

@Composable
private fun StatusLine(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = GiftTheme.type.callout,
        color = GiftTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Preview(name = "Chargement", widthDp = 360, heightDp = 640)
@Composable
private fun HomeLoadingPreview() = GiftManagerTheme { HomeScreen(HomeUiState.Loading, onOpenGallery = {}) }

@Preview(name = "Connecté", widthDp = 360, heightDp = 640)
@Composable
private fun HomeSignedInPreview() = GiftManagerTheme { HomeScreen(HomeUiState.SignedIn("Jonathan"), onOpenGallery = {}) }

@Preview(name = "Erreur, sombre", widthDp = 360, heightDp = 640, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeErrorPreview() = GiftManagerTheme { HomeScreen(HomeUiState.Error) }

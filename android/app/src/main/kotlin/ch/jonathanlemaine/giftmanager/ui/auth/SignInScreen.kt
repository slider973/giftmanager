package ch.jonathanlemaine.giftmanager.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.GiftManagerTheme
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Largeur maximale du bloc de contenu : lisible sur tablette et en paysage (texte limité à 320 dp). */
private val ContentMaxWidth = 420.dp

/** Taille de l'illustration d'une page d'onboarding (iOS : 280 pt au plus). */
private val IllustrationMaxSize = 280.dp

/**
 * Écran de connexion, calqué sur la dernière page de l'onboarding iOS (`OnboardingView`) : loutre
 * « surprise », « La surprise est garantie », puis le bouton Google en bas d'écran. Purement déclaratif :
 * [onSignIn] et [onDismiss] relaient vers [SignInViewModel].
 *
 * Erreur → dialogue « Oups » ([SignInErrorDialog]) ; annulation → rien (le ViewModel revient à `Idle`).
 * Bord à bord : les marges `safeDrawing` sont posées ici, une seule fois.
 */
@Composable
fun SignInScreen(state: SignInUiState, onSignIn: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    SignInContent(
        state = state,
        onSignIn = onSignIn,
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    )
    if (state is SignInUiState.Error) {
        SignInErrorDialog(kind = state.kind, onDismiss = onDismiss)
    }
}

/**
 * Contenu de l'écran de connexion, sans fond ni marges système : la page illustrée en haut (défilable en
 * paysage ou en très grande police plutôt que rognée), le bouton Google fixé en bas, à portée de pouce.
 *
 * L'onboarding (#71) en fera sa dernière page : [OnboardingPage] dans le carrousel, [SignInActions] à la
 * place du bouton « Continuer ».
 */
@Composable
fun SignInContent(state: SignInUiState, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GiftTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val illustrationSize = minOf(IllustrationMaxSize, maxHeight * 0.4f)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .fillMaxWidth(),
            ) {
                OnboardingPage(
                    illustration = Illustration.MASCOT_SURPRISE,
                    title = R.string.sign_in_title,
                    message = R.string.sign_in_message,
                    // Rétrécit sur les petites hauteurs (paysage) pour garder le titre dans le premier écran.
                    illustrationSize = illustrationSize,
                )
            }
        }
        SignInActions(
            state = state,
            onSignIn = onSignIn,
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .padding(start = spacing.xl, end = spacing.xl, top = spacing.l, bottom = spacing.l),
        )
    }
}

/**
 * Une page d'onboarding (`OnboardingView.pageView`) : illustration décorative de [illustrationSize]
 * (280 dp par défaut), titre arrondi (titre TalkBack) et message centré limité à 320 dp. Sans défilement :
 * c'est à l'hôte de le poser.
 */
@Composable
fun OnboardingPage(
    illustration: Illustration,
    @StringRes title: Int,
    @StringRes message: Int,
    modifier: Modifier = Modifier,
    illustrationSize: Dp = IllustrationMaxSize,
) {
    val colors = GiftTheme.colors
    val type = GiftTheme.type
    val spacing = GiftTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xxl),
        modifier = modifier
            .widthIn(max = ContentMaxWidth)
            .padding(horizontal = spacing.xl, vertical = spacing.l),
    ) {
        Image(
            painter = painterResource(illustration.res),
            contentDescription = null,
            modifier = Modifier.size(illustrationSize),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Text(
                text = stringResource(title),
                style = type.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(message),
                style = type.body.copy(lineHeight = type.body.lineHeight * 1.08f),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

/** Zone d'action de la page de connexion : le bouton Google, désactivé avec indicateur pendant la connexion. */
@Composable
fun SignInActions(state: SignInUiState, onSignIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        GoogleSignInButton(onClick = onSignIn, isLoading = state is SignInUiState.InProgress)
    }
}

/** Alerte d'échec, comme l'alerte iOS : « Oups », le message propre au [kind], et « OK ». */
@Composable
fun SignInErrorDialog(kind: SignInUiState.Kind, onDismiss: () -> Unit) {
    val colors = GiftTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.sign_in_error_ok),
                    style = GiftTheme.type.callout.copy(fontWeight = FontWeight.Medium),
                    color = colors.primary,
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.sign_in_error_title),
                style = GiftTheme.type.title,
                color = colors.textPrimary,
            )
        },
        text = {
            Text(
                text = stringResource(kind.messageRes()),
                style = GiftTheme.type.callout,
                color = colors.textSecondary,
            )
        },
        shape = GiftTheme.radius.cardShape,
        containerColor = colors.surface,
    )
}

@StringRes
private fun SignInUiState.Kind.messageRes(): Int = when (this) {
    SignInUiState.Kind.NoGoogleAccount -> R.string.sign_in_error_no_account
    SignInUiState.Kind.Network -> R.string.sign_in_error_network
    SignInUiState.Kind.NotConfigured -> R.string.sign_in_error_not_configured
    SignInUiState.Kind.GoogleUnavailable -> R.string.sign_in_error_unavailable
    SignInUiState.Kind.Generic -> R.string.sign_in_error_generic
}

/** Cadre d'aperçu plein écran : hauteur de téléphone bornée, pour que le bouton reste en bas. */
@Composable
private fun SignInPreviewFrame(state: SignInUiState) = GiftManagerTheme {
    Box(Modifier.fillMaxWidth().heightIn(max = 720.dp)) {
        SignInScreen(state, onSignIn = {}, onDismiss = {}, modifier = Modifier.fillMaxSize())
    }
}

@GiftPreviews
@Preview(name = "Paysage", widthDp = 720, heightDp = 360)
@Composable
private fun SignInIdlePreview() = SignInPreviewFrame(SignInUiState.Idle)

@GiftPreviews
@Composable
private fun SignInInProgressPreview() = SignInPreviewFrame(SignInUiState.InProgress)

@GiftPreviews
@Composable
private fun SignInErrorPreview() = SignInPreviewFrame(SignInUiState.Error(SignInUiState.Kind.NoGoogleAccount))

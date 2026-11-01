package ch.jonathanlemaine.giftmanager.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.core.error.GiftError
import ch.jonathanlemaine.giftmanager.ui.auth.OnboardingPage
import ch.jonathanlemaine.giftmanager.ui.auth.SignInActions
import ch.jonathanlemaine.giftmanager.ui.auth.SignInErrorDialog
import ch.jonathanlemaine.giftmanager.ui.auth.SignInUiState
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ProfileFields
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.common.ScreenTitle
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.theme.CapsuleShape
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import kotlinx.coroutines.launch

/** Largeur maximale de la zone d'action (comme l'écran de connexion). */
private val ActionsMaxWidth = 420.dp

/** Taille maximale d'une illustration d'onboarding (iOS : 280 pt). */
private val IllustrationMaxSize = 280.dp

/**
 * Onboarding (écran 1 de la maquette, iOS : `OnboardingView`) : carrousel glissant de quatre pages
 * ([OnboardingPages]), points de pagination, puis « Continuer » sur les trois premières et le bouton Google
 * sur la dernière (« La surprise est garantie »), à la même place et à la même hauteur : rien ne saute.
 *
 * Le retour système revient à la page précédente. Pendant la connexion, le carrousel est figé. Échec → alerte
 * « Oups » de #70 ; annulation → rien. Animations réduites : pages et points changent sans glissement.
 */
@Composable
fun OnboardingScreen(
    signInState: SignInUiState,
    onSignIn: () -> Unit,
    onDismissSignInError: () -> Unit,
    modifier: Modifier = Modifier,
) = OnboardingContent(signInState, onSignIn, onDismissSignInError, modifier)

@Composable
private fun OnboardingContent(
    signInState: SignInUiState,
    onSignIn: () -> Unit,
    onDismissSignInError: () -> Unit,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val pages = OnboardingPages.all
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val reduceMotion = GiftTheme.reduceMotion
    val spacing = GiftTheme.spacing
    val current = pagerState.currentPage
    val isSigningIn = signInState is SignInUiState.InProgress

    fun goTo(page: Int) {
        scope.launch { if (reduceMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page) }
    }

    BackHandler(enabled = current > 0 && !isSigningIn) { goTo(current - 1) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fcScreenBackground()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSigningIn,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            // Défilable en paysage et aux grandes polices ; l'illustration rétrécit sur les petites hauteurs.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight)
                        .fillMaxWidth(),
                ) {
                    OnboardingPage(
                        illustration = page.illustration,
                        title = page.title,
                        message = page.message,
                        illustrationSize = minOf(IllustrationMaxSize, this@BoxWithConstraints.maxHeight * 0.4f),
                    )
                }
            }
        }

        PageDots(count = pages.size, current = current, modifier = Modifier.padding(vertical = spacing.l))

        Crossfade(
            targetState = OnboardingPages.isLast(current),
            animationSpec = if (reduceMotion) snap() else tween(200),
            label = "onboardingAction",
            modifier = Modifier
                .widthIn(max = ActionsMaxWidth)
                .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.l),
        ) { isLast ->
            if (isLast) {
                SignInActions(state = signInState, onSignIn = onSignIn)
            } else {
                PrimaryButton(
                    text = stringResource(R.string.onboarding_continue),
                    onClick = { goTo(OnboardingPages.next(current)) },
                    trailingIcon = GiftIcons.ChevronRight,
                )
            }
        }
    }

    if (signInState is SignInUiState.Error) {
        SignInErrorDialog(kind = signInState.kind, onDismiss = onDismissSignInError)
    }
}

/**
 * Points de pagination (iOS : `PageDots`) : point actif allongé 20 × 8 en `primary`, autres 8 × 8 en
 * `textSecondary` à 40 %. Un seul nœud TalkBack : « Page 2 sur 4 ». Ressort, instantané si animations réduites.
 */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_a11y, current + 1, count)
    Row(
        horizontalArrangement = Arrangement.spacedBy(GiftTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        repeat(count) { index -> PageDot(isCurrent = index == current) }
    }
}

@Composable
private fun PageDot(isCurrent: Boolean) {
    val colors = GiftTheme.colors
    val reduceMotion = GiftTheme.reduceMotion
    val width by animateDpAsState(
        targetValue = if (isCurrent) 20.dp else 8.dp,
        animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 0.8f, stiffness = 440f),
        label = "dotWidth",
    )
    val color by animateColorAsState(
        targetValue = if (isCurrent) colors.primary else colors.textSecondary.copy(alpha = 0.4f),
        animationSpec = if (reduceMotion) snap() else tween(200),
        label = "dotColor",
    )
    Box(Modifier.size(width = width, height = 8.dp).background(color, CapsuleShape))
}

/**
 * Premier lancement (iOS : `ProfileSetupView`) : loutre `mascot_love`, « Faisons connaissance », prénom,
 * pays et devise, puis « C'est parti ». Le bouton reste désactivé tant que le prénom est vide.
 */
@Composable
fun ProfileSetupScreen(
    state: ProfileSetupUiState,
    onNameChange: (String) -> Unit,
    onCountryChange: (String) -> Unit,
    onCurrencyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = GiftTheme.spacing
    ScreenColumn(modifier = modifier, spacing = spacing.xl) {
        Image(
            painter = painterResource(Illustration.MASCOT_LOVE.res),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).size(160.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            ScreenTitle(stringResource(R.string.profile_setup_title))
            BodyText(stringResource(R.string.profile_setup_message))
        }
        ProfileFields(state.name, state.country, state.currency, onNameChange, onCountryChange, onCurrencyChange)
        PrimaryButton(
            text = stringResource(R.string.profile_setup_go),
            onClick = onSave,
            trailingIcon = GiftIcons.ArrowForward,
            isLoading = state.isSaving,
            enabled = state.canSave,
        )
    }
    GiftErrorDialog(state.error, onDismissError)
}

// region Aperçus

@GiftPreviews
@Composable
private fun OnboardingFirstPagePreview() = ScreenPreviewFrame {
    OnboardingContent(SignInUiState.Idle, onSignIn = {}, onDismissSignInError = {})
}

@GiftPreviews
@Composable
private fun OnboardingSignInPagePreview() = ScreenPreviewFrame {
    OnboardingContent(SignInUiState.InProgress, onSignIn = {}, onDismissSignInError = {}, initialPage = OnboardingPages.lastIndex)
}

@GiftPreviews
@Composable
private fun ProfileSetupEmptyPreview() = ScreenPreviewFrame {
    ProfileSetupScreen(ProfileSetupUiState(), {}, {}, {}, {}, {})
}

@GiftPreviews
@Composable
private fun ProfileSetupFilledPreview() = ScreenPreviewFrame {
    ProfileSetupScreen(ProfileSetupUiState(name = "Jonathan", country = "FR", currency = "EUR", isSaving = true), {}, {}, {}, {}, {})
}

@GiftPreviews
@Composable
private fun ProfileSetupErrorPreview() = ScreenPreviewFrame {
    ProfileSetupScreen(ProfileSetupUiState(name = "Jonathan", error = GiftError.Network), {}, {}, {}, {}, {})
}

// endregion

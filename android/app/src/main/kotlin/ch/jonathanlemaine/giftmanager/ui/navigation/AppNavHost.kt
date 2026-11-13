package ch.jonathanlemaine.giftmanager.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.common.ScreenPreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.GiftOpeningSplash
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.TextLinkButton
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.family.GroupSetupActions
import ch.jonathanlemaine.giftmanager.ui.family.GroupSetupScreen
import ch.jonathanlemaine.giftmanager.ui.family.GroupSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.main.MainScaffold
import ch.jonathanlemaine.giftmanager.ui.onboarding.OnboardingScreen
import ch.jonathanlemaine.giftmanager.ui.onboarding.ProfileSetupScreen
import ch.jonathanlemaine.giftmanager.ui.onboarding.ProfileSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.root.RootDestination
import ch.jonathanlemaine.giftmanager.ui.root.RootViewModel
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme

/** Routage racine selon la phase de la session : chargement, onboarding + connexion, profil, famille, app. */
@Composable
fun AppNavHost(viewModelFactory: ViewModelProvider.Factory) {
    val root: RootViewModel = viewModel(factory = viewModelFactory)
    val destination = root.destination.collectAsStateWithLifecycle()
    // Cadeau qui s'ouvre, joué une fois au démarrage par-dessus le premier écran (survit à une rotation).
    var showSplash by rememberSaveable { mutableStateOf(true) }
    Box(modifier = Modifier.fillMaxSize()) {
        when (destination.value) {
            RootDestination.Loading -> FullScreenLoading()
            RootDestination.Onboarding -> OnboardingRoute(viewModelFactory)
            RootDestination.LoadFailed -> LoadFailedScreen(onRetry = root::retry, onSignOut = root::signOut)
            RootDestination.ProfileSetup -> ProfileSetupRoute(viewModelFactory)
            RootDestination.GroupSetup -> GroupSetupRoute(viewModelFactory)
            RootDestination.Main -> MainScaffold(viewModelFactory)
        }
        AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut(tween(350))) {
            GiftOpeningSplash(onFinished = { showSplash = false })
        }
    }
}

/** Chargement de la session : le paquet qui flotte, au centre du fond crème (même fond que la fenêtre native). */
@Composable
private fun FullScreenLoading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fcScreenBackground().fillMaxSize()) {
        GiftLoadingView()
    }
}

/**
 * Données de départ impossibles à charger : loutre pensive, cause et remède, « Réessayer » en CTA, et une
 * sortie (« Se déconnecter ») pour ne jamais rester bloqué. Centré verticalement, défilable.
 */
@Composable
private fun LoadFailedScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fcScreenBackground().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .fillMaxWidth()
                .padding(bottom = GiftTheme.spacing.xl),
        ) {
            EmptyStateView(
                illustration = Illustration.MASCOT_THINKING,
                title = stringResource(R.string.load_failed_title),
                message = stringResource(R.string.load_failed_message),
                actionLabel = stringResource(R.string.load_failed_retry),
                onAction = onRetry,
            )
            TextLinkButton(stringResource(R.string.sign_out), onSignOut)
        }
    }
}

@GiftPreviews
@Composable
private fun LoadFailedPreview() = ScreenPreviewFrame { LoadFailedScreen(onRetry = {}, onSignOut = {}) }

@GiftPreviews
@Composable
private fun FullScreenLoadingPreview() = ScreenPreviewFrame { FullScreenLoading() }

@Composable
private fun OnboardingRoute(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: SignInViewModel = viewModel(factory = viewModelFactory)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    // LocalContext est l'Activity ici : Credential Manager en a besoin pour afficher le sélecteur.
    val context = LocalContext.current
    OnboardingScreen(
        signInState = state.value,
        onSignIn = { viewModel.signIn(context) },
        onDismissSignInError = viewModel::dismissMessage,
        onDemoError = viewModel::showError,
        onSignInWithApple = viewModel::signInWithApple,
    )
}

@Composable
private fun ProfileSetupRoute(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: ProfileSetupViewModel = viewModel(factory = viewModelFactory)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    ProfileSetupScreen(
        state = state.value,
        onNameChange = viewModel::onNameChange,
        onCountryChange = viewModel::onCountryChange,
        onCurrencyChange = viewModel::onCurrencyChange,
        onSave = viewModel::save,
        onDismissError = viewModel::dismissError,
    )
}

@Composable
private fun GroupSetupRoute(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: GroupSetupViewModel = viewModel(factory = viewModelFactory)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    GroupSetupScreen(
        state = state.value,
        actions = GroupSetupActions(
            onShowCreate = viewModel::showCreate,
            onShowJoin = viewModel::showJoin,
            onBack = viewModel::showChoose,
            onGroupNameChange = viewModel::onGroupNameChange,
            onHouseholdNameChange = viewModel::onHouseholdNameChange,
            onGroupCodeChange = viewModel::onGroupCodeChange,
            onHouseholdCodeChange = viewModel::onHouseholdCodeChange,
            onCreateGroup = viewModel::createGroup,
            onJoinGroup = viewModel::joinGroup,
            onCreateHousehold = viewModel::createHousehold,
            onJoinHousehold = viewModel::joinHousehold,
            onShareHousehold = viewModel::shareHousehold,
            onSignOut = viewModel::signOut,
            onDismissError = viewModel::dismissError,
        ),
    )
}

package ch.jonathanlemaine.giftmanager.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
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

/** Routage racine selon la phase de la session : chargement, onboarding + connexion, profil, famille, app. */
@Composable
fun AppNavHost(viewModelFactory: ViewModelProvider.Factory) {
    val root: RootViewModel = viewModel(factory = viewModelFactory)
    val destination = root.destination.collectAsStateWithLifecycle()
    when (destination.value) {
        RootDestination.Loading -> FullScreenLoading()
        RootDestination.Onboarding -> OnboardingRoute(viewModelFactory)
        RootDestination.LoadFailed -> LoadFailedScreen(onRetry = root::retry, onSignOut = root::signOut)
        RootDestination.ProfileSetup -> ProfileSetupRoute(viewModelFactory)
        RootDestination.GroupSetup -> GroupSetupRoute(viewModelFactory)
        RootDestination.Main -> MainScaffold(viewModelFactory)
    }
}

@Composable
private fun FullScreenLoading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fcScreenBackground().fillMaxSize()) {
        GiftLoadingView()
    }
}

@Composable
private fun LoadFailedScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    ScreenColumn {
        BodyText(stringResource(R.string.load_failed_message))
        PrimaryButton(stringResource(R.string.load_failed_retry), onRetry)
        TextLinkButton(stringResource(R.string.sign_out), onSignOut)
    }
}

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

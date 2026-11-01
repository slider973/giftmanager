package ch.jonathanlemaine.giftmanager.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import ch.jonathanlemaine.giftmanager.ui.auth.SignInScreen
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.components.GiftLoadingView
import ch.jonathanlemaine.giftmanager.ui.components.fcScreenBackground
import ch.jonathanlemaine.giftmanager.ui.debug.DebugEntryPoints
import ch.jonathanlemaine.giftmanager.ui.home.HomeScreen
import ch.jonathanlemaine.giftmanager.ui.home.HomeViewModel
import ch.jonathanlemaine.giftmanager.ui.root.RootViewModel

/** Routage racine selon la session : chargement, connexion, puis l'app. */
@Composable
fun AppNavHost(viewModelFactory: ViewModelProvider.Factory) {
    val root: RootViewModel = viewModel(factory = viewModelFactory)
    val authState = root.state.collectAsStateWithLifecycle()
    when (authState.value) {
        AuthState.Loading -> FullScreenLoading()
        AuthState.SignedOut -> SignInRoute(viewModelFactory)
        is AuthState.SignedIn -> SignedInNavHost(viewModelFactory)
    }
}

@Composable
private fun FullScreenLoading() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fcScreenBackground().fillMaxSize()) {
        GiftLoadingView()
    }
}

@Composable
private fun SignInRoute(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: SignInViewModel = viewModel(factory = viewModelFactory)
    val state = viewModel.uiState.collectAsStateWithLifecycle()
    // LocalContext est l'Activity ici : Credential Manager en a besoin pour afficher le sélecteur.
    val context = LocalContext.current
    SignInScreen(
        state = state.value,
        onSignIn = { viewModel.signIn(context) },
        onDismiss = viewModel::dismissMessage,
    )
}

@Composable
private fun SignedInNavHost(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
            val state = viewModel.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                state = state.value,
                onOpenGallery = if (DebugEntryPoints.HAS_DESIGN_SYSTEM_GALLERY) {
                    { navController.navigate(DebugGallery) }
                } else {
                    null
                },
                onSignOut = viewModel::signOut,
            )
        }
        if (DebugEntryPoints.HAS_DESIGN_SYSTEM_GALLERY) {
            composable<DebugGallery> {
                DebugEntryPoints.DesignSystemGalleryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

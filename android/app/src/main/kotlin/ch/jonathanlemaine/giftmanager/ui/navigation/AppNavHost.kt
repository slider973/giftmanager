package ch.jonathanlemaine.giftmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.jonathanlemaine.giftmanager.ui.debug.DebugEntryPoints
import ch.jonathanlemaine.giftmanager.ui.home.HomeScreen
import ch.jonathanlemaine.giftmanager.ui.home.HomeViewModel

@Composable
fun AppNavHost(viewModelFactory: ViewModelProvider.Factory) {
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
            )
        }
        if (DebugEntryPoints.HAS_DESIGN_SYSTEM_GALLERY) {
            composable<DebugGallery> {
                DebugEntryPoints.DesignSystemGalleryScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

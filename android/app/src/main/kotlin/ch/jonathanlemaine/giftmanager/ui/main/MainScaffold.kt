package ch.jonathanlemaine.giftmanager.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ShoppingCart
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.BodyText
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenColumn
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.debug.DebugEntryPoints
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeActions
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeScreen
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeViewModel
import ch.jonathanlemaine.giftmanager.ui.navigation.DebugGallery
import ch.jonathanlemaine.giftmanager.ui.navigation.FamilyHome
import ch.jonathanlemaine.giftmanager.ui.navigation.HomeGraph
import ch.jonathanlemaine.giftmanager.ui.navigation.ProfileTab
import ch.jonathanlemaine.giftmanager.ui.navigation.Purchases
import ch.jonathanlemaine.giftmanager.ui.navigation.Search
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileActions
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileScreen
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileViewModel

/**
 * Écran principal (iOS : `MainTabView`) : barre d'onglets Accueil · Recherche · « + » · Mes achats · Profil.
 * L'état de chaque onglet est conservé quand on en change. « + » n'est pas une destination : il ouvre une
 * feuille (provisoire jusqu'à #73). Câblage minimal : le `ui-designer` stylise la barre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory)
    val pendingInvite by mainViewModel.pendingInviteCode.collectAsStateWithLifecycle()
    val mainError by mainViewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabItem(R.string.tab_home, Icons.Filled.Home, destination?.hierarchy?.any { it.hasRoute(HomeGraph::class) } == true) {
                    navController.switchTab(HomeGraph)
                }
                TabItem(R.string.tab_search, GiftIcons.Search, destination?.hierarchy?.any { it.hasRoute(Search::class) } == true) {
                    navController.switchTab(Search)
                }
                TabItem(R.string.tab_add, GiftIcons.Add, selected = false) { showAddSheet = true }
                TabItem(R.string.tab_purchases, Icons.Filled.ShoppingCart, destination?.hierarchy?.any { it.hasRoute(Purchases::class) } == true) {
                    navController.switchTab(Purchases)
                }
                TabItem(R.string.tab_profile, Icons.Filled.AccountCircle, destination?.hierarchy?.any { it.hasRoute(ProfileTab::class) } == true) {
                    navController.switchTab(ProfileTab)
                }
            }
        },
    ) { padding ->
        NavHost(navController = navController, startDestination = HomeGraph, modifier = Modifier.padding(padding)) {
            navigation<HomeGraph>(startDestination = FamilyHome) {
                composable<FamilyHome> { FamilyHomeRoute(viewModelFactory) }
            }
            composable<Search> { PlaceholderScreen() }
            composable<Purchases> { PlaceholderScreen() }
            composable<ProfileTab> {
                ProfileRoute(
                    viewModelFactory,
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

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            ScreenColumn { BodyText(stringResource(R.string.add_sheet_title) + "\n" + stringResource(R.string.placeholder_coming_soon)) }
        }
    }
    pendingInvite?.let { code ->
        AlertDialog(
            onDismissRequest = mainViewModel::dismissInvite,
            title = { Text(stringResource(R.string.invite_dialog_title)) },
            text = { Text(stringResource(R.string.invite_dialog_message, code)) },
            confirmButton = { TextButton(onClick = mainViewModel::confirmInvite) { Text(stringResource(R.string.group_join_submit)) } },
            dismissButton = { TextButton(onClick = mainViewModel::dismissInvite) { Text(stringResource(R.string.cancel)) } },
        )
    }
    GiftErrorDialog(mainError, mainViewModel::dismissError)
}

@Composable
private fun RowScope.TabItem(label: Int, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label)) },
    )
}

/** Change d'onglet en sauvegardant l'état de celui qu'on quitte et en restaurant celui qu'on rejoint. */
private fun NavController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun PlaceholderScreen() {
    ScreenColumn(Modifier.fillMaxSize()) { BodyText(stringResource(R.string.placeholder_coming_soon)) }
}

@Composable
private fun FamilyHomeRoute(viewModelFactory: ViewModelProvider.Factory) {
    val viewModel: FamilyHomeViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    FamilyHomeScreen(
        state = state,
        actions = FamilyHomeActions(
            onSelectTab = viewModel::selectTab,
            onRenameChange = viewModel::onRenameChange,
            onRename = viewModel::rename,
            onSelectGroup = viewModel::selectGroup,
            onShareText = { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, null))
            },
            onShowJoinDialog = viewModel::showJoinDialog,
            onHideJoinDialog = viewModel::hideJoinDialog,
            onJoinCodeChange = viewModel::onJoinCodeChange,
            onJoin = viewModel::joinOtherGroup,
            onDismissError = viewModel::dismissError,
        ),
    )
}

@Composable
private fun ProfileRoute(viewModelFactory: ViewModelProvider.Factory, onOpenGallery: (() -> Unit)?) {
    val viewModel: ProfileViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileScreen(
        state = state,
        actions = ProfileActions(
            onNameChange = viewModel::onNameChange,
            onCountryChange = viewModel::onCountryChange,
            onCurrencyChange = viewModel::onCurrencyChange,
            onSave = viewModel::save,
            onPurchaseReminders = viewModel::setPurchaseReminders,
            onFamilyNews = viewModel::setFamilyNews,
            onBirthdayReminders = viewModel::setBirthdayReminders,
            onRequestUnshare = viewModel::requestUnshare,
            onConfirmUnshare = viewModel::confirmUnshare,
            onCancelUnshare = viewModel::cancelUnshare,
            onSignOut = viewModel::signOut,
            onRequestDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onCancelDelete = viewModel::cancelDelete,
            onDismissError = viewModel::dismissError,
        ),
        onOpenGallery = onOpenGallery,
    )
}

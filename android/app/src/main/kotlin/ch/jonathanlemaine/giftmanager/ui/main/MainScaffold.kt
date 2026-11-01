package ch.jonathanlemaine.giftmanager.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import ch.jonathanlemaine.giftmanager.R
import ch.jonathanlemaine.giftmanager.ui.common.GiftAlertDialog
import ch.jonathanlemaine.giftmanager.ui.common.GiftErrorDialog
import ch.jonathanlemaine.giftmanager.ui.common.ScreenContentMaxWidth
import ch.jonathanlemaine.giftmanager.ui.components.EmptyStateView
import ch.jonathanlemaine.giftmanager.ui.components.FCNotice
import ch.jonathanlemaine.giftmanager.ui.components.FCNoticeTone
import ch.jonathanlemaine.giftmanager.ui.components.GiftIcons
import ch.jonathanlemaine.giftmanager.ui.components.GiftPreviews
import ch.jonathanlemaine.giftmanager.ui.components.Illustration
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
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
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import kotlinx.coroutines.launch

/**
 * Écran principal (iOS : `MainTabView`) : barre d'onglets Accueil · Recherche · « + » · Mes achats · Profil
 * ([MainNavigationBar]). L'état de chaque onglet est conservé quand on en change. « + » n'est pas une
 * destination : il ouvre une feuille (provisoire jusqu'à #73).
 *
 * Bord à bord : le `Scaffold` réserve la barre d'état et la barre d'onglets, et **consomme** ces marges ;
 * les écrans posent `safeDrawing` sans les compter deux fois (seul le clavier reste à leur charge).
 */
@Composable
fun MainScaffold(viewModelFactory: ViewModelProvider.Factory) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = backStackEntry?.destination?.mainTab()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    val mainViewModel: MainViewModel = viewModel(factory = viewModelFactory)
    val pendingInvite by mainViewModel.pendingInviteCode.collectAsStateWithLifecycle()
    val mainError by mainViewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = GiftTheme.colors.background,
        bottomBar = {
            MainNavigationBar(
                current = currentTab,
                onSelect = { tab -> navController.switchTab(tab.route) },
                onAdd = { showAddSheet = true },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            navigation<HomeGraph>(startDestination = FamilyHome) {
                composable<FamilyHome> { FamilyHomeRoute(viewModelFactory) }
            }
            composable<Search> {
                ComingSoonScreen(Illustration.MASCOT_THINKING, stringResource(R.string.tab_search), stringResource(R.string.search_coming_soon))
            }
            composable<Purchases> {
                ComingSoonScreen(Illustration.MASCOT_SLEEPING, stringResource(R.string.tab_purchases), stringResource(R.string.purchases_coming_soon))
            }
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

    if (showAddSheet) AddGiftSheet(onDismiss = { showAddSheet = false })

    pendingInvite?.let { code ->
        GiftAlertDialog(
            title = stringResource(R.string.invite_dialog_title),
            message = stringResource(R.string.invite_dialog_message, code),
            confirmLabel = stringResource(R.string.group_join_submit),
            onConfirm = mainViewModel::confirmInvite,
            onDismiss = mainViewModel::dismissInvite,
            dismissLabel = stringResource(R.string.cancel),
        )
    }
    GiftErrorDialog(mainError, mainViewModel::dismissError)
}

private val MainTab.route: Any
    get() = when (this) {
        MainTab.Home -> HomeGraph
        MainTab.Search -> Search
        MainTab.Purchases -> Purchases
        MainTab.Profile -> ProfileTab
    }

/** Onglet qui contient [this] ; `null` hors onglets (galerie de debug, ouverte depuis Profil → Profil). */
private fun NavDestination.mainTab(): MainTab? = when {
    hierarchy.any { it.hasRoute(HomeGraph::class) } -> MainTab.Home
    hierarchy.any { it.hasRoute(Search::class) } -> MainTab.Search
    hierarchy.any { it.hasRoute(Purchases::class) } -> MainTab.Purchases
    hierarchy.any { it.hasRoute(ProfileTab::class) || it.hasRoute(DebugGallery::class) } -> MainTab.Profile
    else -> null
}

/** Change d'onglet en sauvegardant l'état de celui qu'on quitte et en restaurant celui qu'on rejoint. */
private fun NavController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Feuille « + » provisoire (#73) : loutre au cadeau, ce que le bouton permettra bientôt, rappel que les
 * idées restent cachées aux parents, et « Compris » qui referme la feuille (glisser vers le bas aussi).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGiftSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GiftTheme.colors.background,
        contentColor = GiftTheme.colors.textPrimary,
    ) {
        AddGiftSheetContent(
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
            },
        )
    }
}

@Composable
private fun AddGiftSheetContent(onClose: () -> Unit) {
    val spacing = GiftTheme.spacing
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = spacing.xl, end = spacing.xl, bottom = spacing.xl),
    ) {
        Text(
            text = stringResource(R.string.add_sheet_title),
            style = GiftTheme.type.title,
            color = GiftTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        EmptyStateView(
            illustration = Illustration.MASCOT_GIFT,
            title = stringResource(R.string.coming_soon_title),
            message = stringResource(R.string.add_sheet_message),
            modifier = Modifier.widthIn(max = ScreenContentMaxWidth),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.l), modifier = Modifier.widthIn(max = ScreenContentMaxWidth)) {
            FCNotice(GiftIcons.EyeSlash, stringResource(R.string.add_sheet_notice), tone = FCNoticeTone.SURPRISE)
            PrimaryButton(text = stringResource(R.string.add_sheet_close), onClick = onClose)
        }
    }
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
            onRefresh = viewModel::refresh,
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

@GiftPreviews
@Composable
private fun AddGiftSheetPreview() = PreviewFrame { AddGiftSheetContent(onClose = {}) }

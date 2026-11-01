package ch.jonathanlemaine.giftmanager.ui.main

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import ch.jonathanlemaine.giftmanager.ui.components.PreviewFrame
import ch.jonathanlemaine.giftmanager.ui.components.PrimaryButton
import ch.jonathanlemaine.giftmanager.ui.debug.DebugEntryPoints
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeActions
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeScreen
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeViewModel
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.gifts.ShareResolution
import ch.jonathanlemaine.giftmanager.core.gifts.ShareTargets
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddSheet
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddTarget
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddViewModel
import ch.jonathanlemaine.giftmanager.push.NotificationTarget
import ch.jonathanlemaine.giftmanager.ui.notifications.NotificationsViewModel
import ch.jonathanlemaine.giftmanager.ui.navigation.AddGiftRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ChildEditorRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ChildGiftsRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ChildModeRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.SantaRoute
import ch.jonathanlemaine.giftmanager.ui.santa.SantaDestination
import ch.jonathanlemaine.giftmanager.ui.navigation.DebugGallery
import ch.jonathanlemaine.giftmanager.ui.navigation.EventDetailRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.EventEditorRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.FamilyHome
import ch.jonathanlemaine.giftmanager.ui.navigation.GiftDetailRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.HomeGraph
import ch.jonathanlemaine.giftmanager.ui.navigation.PendingIdeasRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ProfileTab
import ch.jonathanlemaine.giftmanager.ui.navigation.BudgetEditorRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.PurchaseGiftRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.Purchases
import ch.jonathanlemaine.giftmanager.ui.navigation.PurchasesGraph
import ch.jonathanlemaine.giftmanager.ui.navigation.Search
import ch.jonathanlemaine.giftmanager.ui.navigation.SearchGiftDetailRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.SearchGraph
import ch.jonathanlemaine.giftmanager.ui.navigation.ThanksComposerRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ThanksInboxRoute
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileActions
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileScreen
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileViewModel
import ch.jonathanlemaine.giftmanager.ui.theme.GiftTheme
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Écran principal (iOS : `MainTabView`) : barre d'onglets Accueil · Recherche · « + » · Mes achats · Profil
 * ([MainNavigationBar]). L'état de chaque onglet est conservé quand on en change. « + » n'est pas une
 * destination : il ouvre la feuille d'ajout rapide ([QuickAddSheet], #73).
 *
 * La barre reste affichée sur les écrans poussés de consultation (fiche d'un événement, liste d'un enfant),
 * comme sur iOS, et s'efface sur les formulaires plein écran (éditeurs d'enfant et d'événement, ajout d'un cadeau).
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
    val childModeActive by mainViewModel.childModeActive.collectAsStateWithLifecycle()
    val pendingLink by mainViewModel.pendingSharedLink.collectAsStateWithLifecycle()
    val shareWithoutLink by mainViewModel.shareWithoutLink.collectAsStateWithLifecycle()
    // Partage (#79) : lien dont l'enfant reste à choisir (feuille en mode partage) et message à afficher.
    var shareSheetLink by rememberSaveable { mutableStateOf<String?>(null) }
    var shareNotice by rememberSaveable { mutableStateOf<ShareNotice?>(null) }
    val quickAdd: QuickAddViewModel = viewModel(factory = viewModelFactory)
    val quickAddState by quickAdd.uiState.collectAsStateWithLifecycle()

    // Notifications push (#76) : destination d'une notification touchée, et feuille d'explication de l'autorisation.
    val notifications: NotificationsViewModel = viewModel(factory = viewModelFactory)
    val notificationTarget by notifications.pendingTarget.collectAsStateWithLifecycle()
    val notificationPromptVisible by notifications.promptVisible.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { notifications.onMainScreenReached() }
    // MainScaffold n'existe qu'en phase Ready, et le modèle ne publie rien pendant le mode enfant : la destination attend.
    LaunchedEffect(notificationTarget) {
        val target = notificationTarget ?: return@LaunchedEffect
        navController.openNotificationTarget(target)
        notifications.consumeTarget(target)
    }

    // MainScaffold n'existe qu'en phase Ready, et le modèle ne publie rien pendant le mode enfant : le lien attend.
    LaunchedEffect(pendingLink) {
        val link = pendingLink ?: return@LaunchedEffect
        when (val resolution = ShareTargets.resolve(quickAddState)) {
            ShareResolution.NoList -> shareNotice = ShareNotice.NO_LIST
            is ShareResolution.Single -> navController.openAddGift(resolution.target, quickAddState.defaultEventId, link)
            ShareResolution.Choose -> shareSheetLink = link
        }
        mainViewModel.consumeSharedLink()
    }
    LaunchedEffect(shareWithoutLink) {
        if (!shareWithoutLink) return@LaunchedEffect
        shareNotice = ShareNotice.NO_LINK
        mainViewModel.consumeShareWithoutLink()
    }

    Scaffold(
        containerColor = GiftTheme.colors.background,
        bottomBar = {
            // Masquée sur les formulaires plein écran (éditeurs) : ils se ferment par ✕ ou Retour, pas en
            // changeant d'onglet. Visible sur les écrans poussés de consultation (fiche, liste).
            val reduceMotion = GiftTheme.reduceMotion
            AnimatedVisibility(
                visible = backStackEntry?.destination?.isFullScreenForm() != true,
                enter = if (reduceMotion) EnterTransition.None else expandVertically(tween(200), expandFrom = Alignment.Top),
                exit = if (reduceMotion) ExitTransition.None else shrinkVertically(tween(200), shrinkTowards = Alignment.Top),
            ) {
                MainNavigationBar(
                    current = currentTab,
                    onSelect = { tab -> navController.switchTab(tab.route) },
                    onAdd = { showAddSheet = true },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            navigation<HomeGraph>(startDestination = FamilyHome) {
                composable<FamilyHome> {
                    FamilyHomeRoute(
                        viewModelFactory,
                        onOpenEvent = { navController.navigate(EventDetailRoute(it.toString())) },
                        onNewEvent = { navController.navigate(EventEditorRoute()) },
                        onEditEvent = { navController.navigate(EventEditorRoute(it.toString())) },
                        onOpenChild = { childId, eventId ->
                            navController.navigate(ChildGiftsRoute(childId.toString(), eventId?.toString(), readOnly = false))
                        },
                        onEditChild = { navController.navigate(ChildEditorRoute(childId = it.toString())) },
                        onAddChild = { navController.navigate(ChildEditorRoute()) },
                        onAddAdult = { navController.navigate(ChildEditorRoute(adult = true)) },
                        onOpenPendingIdeas = { navController.navigate(PendingIdeasRoute) },
                    )
                }
                composable<PendingIdeasRoute> {
                    PendingIdeasDestination(viewModelFactory, onBack = { navController.popBackStack() })
                }
                composable<EventDetailRoute> {
                    EventDetailDestination(
                        viewModelFactory,
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(EventEditorRoute(it.toString())) },
                        onOpenChild = { childId, eventId, readOnly ->
                            navController.navigate(ChildGiftsRoute(childId.toString(), eventId.toString(), readOnly))
                        },
                    )
                }
                composable<ChildEditorRoute> { ChildEditorDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
                composable<EventEditorRoute> { EventEditorDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
                composable<ChildGiftsRoute> {
                    ChildGiftsDestination(
                        viewModelFactory,
                        onBack = { navController.popBackStack() },
                        onOpenItem = { navController.navigate(GiftDetailRoute(it.toString())) },
                        onAdd = { childId, kind, eventId, owned ->
                            navController.navigate(AddGiftRoute(childId.toString(), kind.routeValue(), eventId?.toString(), owned, null))
                        },
                        onStartChildMode = { childId, eventId -> navController.navigate(ChildModeRoute(childId.toString(), eventId?.toString())) },
                        onLaunchSanta = { navController.navigate(SantaRoute(it.toString())) },
                    )
                }
                composable<GiftDetailRoute> {
                    GiftDetailDestination(
                        viewModelFactory,
                        onBack = { navController.popBackStack() },
                        onEdit = { childId, kind, eventId, itemId ->
                            navController.navigate(AddGiftRoute(childId.toString(), kind.routeValue(), eventId?.toString(), false, itemId.toString()))
                        },
                        onThank = { navController.navigate(ThanksComposerRoute(it.toString())) },
                    )
                }
                composable<AddGiftRoute> { AddGiftDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
            }
            navigation<SearchGraph>(startDestination = Search) {
                composable<Search> {
                    SearchDestination(viewModelFactory, onOpenItem = { navController.navigate(SearchGiftDetailRoute(it.toString())) })
                }
                composable<SearchGiftDetailRoute> {
                    GiftDetailDestination(
                        viewModelFactory,
                        onBack = { navController.popBackStack() },
                        onEdit = { childId, kind, eventId, itemId ->
                            navController.navigate(AddGiftRoute(childId.toString(), kind.routeValue(), eventId?.toString(), false, itemId.toString()))
                        },
                        onThank = { navController.navigate(ThanksComposerRoute(it.toString())) },
                    )
                }
            }
            navigation<PurchasesGraph>(startDestination = Purchases) {
                composable<Purchases> {
                    PurchasesDestination(
                        viewModelFactory,
                        onOpenItem = { navController.navigate(PurchaseGiftRoute(it.toString())) },
                        onNewBudget = { navController.navigate(BudgetEditorRoute()) },
                        onOpenThanks = { navController.navigate(ThanksInboxRoute) },
                        onEditBudget = { budget ->
                            navController.navigate(
                                BudgetEditorRoute(
                                    budgetId = budget.id.toString(),
                                    childId = budget.childId?.toString(),
                                    eventId = budget.eventId?.toString(),
                                    amount = budget.amount.toPlainString(),
                                    currency = budget.currency,
                                ),
                            )
                        },
                    )
                }
                composable<PurchaseGiftRoute> {
                    GiftDetailDestination(
                        viewModelFactory,
                        onBack = { navController.popBackStack() },
                        onEdit = { childId, kind, eventId, itemId ->
                            navController.navigate(AddGiftRoute(childId.toString(), kind.routeValue(), eventId?.toString(), false, itemId.toString()))
                        },
                        onThank = { navController.navigate(ThanksComposerRoute(it.toString())) },
                    )
                }
                composable<BudgetEditorRoute> { BudgetEditorDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
            }
            // Remerciements (#75) : racine du NavHost, hors des graphes d'onglets, ouverts depuis plusieurs onglets.
            composable<ChildModeRoute> { ChildModeDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
            composable<SantaRoute> { SantaDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
            composable<ThanksComposerRoute> { ThanksComposerDestination(viewModelFactory, onClose = { navController.popBackStack() }) }
            composable<ThanksInboxRoute> { ThanksInboxDestination(viewModelFactory, onBack = { navController.popBackStack() }) }
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

    if (showAddSheet || shareSheetLink != null) {
        QuickAddSheet(
            state = quickAddState,
            onPick = { target ->
                val link = shareSheetLink
                showAddSheet = false
                shareSheetLink = null
                navController.openAddGift(target, quickAddState.defaultEventId, link)
            },
            onDismiss = {
                showAddSheet = false
                shareSheetLink = null
            },
            sharedLink = shareSheetLink,
        )
    }

    // Câblage minimal : le design remplace cette boîte par une feuille d'explication.
    if (notificationPromptVisible) {
        GiftAlertDialog(
            title = stringResource(R.string.notif_prompt_title),
            message = stringResource(R.string.notif_prompt_message),
            confirmLabel = stringResource(R.string.notif_prompt_enable),
            onConfirm = notifications::requestPermission,
            onDismiss = notifications::dismissPrompt,
            dismissLabel = stringResource(R.string.notif_prompt_later),
        )
    }

    shareNotice?.let { notice ->
        GiftAlertDialog(
            title = stringResource(notice.title),
            message = stringResource(notice.message),
            confirmLabel = stringResource(R.string.share_notice_ok),
            onConfirm = { shareNotice = null },
            onDismiss = { shareNotice = null },
        )
    }

    // Mode enfant : rien ne s'affiche devant l'enfant ; l'invitation et l'erreur restent en attente jusqu'à la sortie.
    if (!childModeActive) pendingInvite?.let { code ->
        GiftAlertDialog(
            title = stringResource(R.string.invite_dialog_title),
            message = stringResource(R.string.invite_dialog_message, code),
            confirmLabel = stringResource(R.string.group_join_submit),
            onConfirm = mainViewModel::confirmInvite,
            onDismiss = mainViewModel::dismissInvite,
            dismissLabel = stringResource(R.string.cancel),
        )
    }
    if (!childModeActive) GiftErrorDialog(mainError, mainViewModel::dismissError)
}

/** Message d'un partage qui n'ouvre aucun formulaire (#79). */
private enum class ShareNotice(@StringRes val title: Int, @StringRes val message: Int) {
    NO_LINK(R.string.share_no_link_title, R.string.share_no_link_message),
    NO_LIST(R.string.share_no_list_title, R.string.share_no_list_message),
}

private val MainTab.route: Any
    get() = when (this) {
        MainTab.Home -> HomeGraph
        MainTab.Search -> SearchGraph
        MainTab.Purchases -> PurchasesGraph
        MainTab.Profile -> ProfileTab
    }

/** Onglet qui contient [this] ; `null` hors onglets (galerie de debug, ouverte depuis Profil → Profil). */
private fun NavDestination.mainTab(): MainTab? = when {
    hierarchy.any { it.hasRoute(HomeGraph::class) } -> MainTab.Home
    hierarchy.any { it.hasRoute(SearchGraph::class) } -> MainTab.Search
    hierarchy.any { it.hasRoute(PurchasesGraph::class) || it.hasRoute(ThanksInboxRoute::class) } -> MainTab.Purchases
    hierarchy.any { it.hasRoute(ProfileTab::class) || it.hasRoute(DebugGallery::class) } -> MainTab.Profile
    else -> null
}

/** Formulaire plein écran (création / modification) : la barre d'onglets s'efface (Material : full-screen dialog). */
private fun NavDestination.isFullScreenForm(): Boolean =
    hasRoute(ChildEditorRoute::class) || hasRoute(EventEditorRoute::class) || hasRoute(AddGiftRoute::class) ||
        hasRoute(BudgetEditorRoute::class) || hasRoute(ThanksComposerRoute::class) || hasRoute(ChildModeRoute::class) ||
        hasRoute(SantaRoute::class)

private fun WishKind.routeValue(): String = if (this == WishKind.IDEA) AddGiftRoute.KIND_IDEA else AddGiftRoute.KIND_WISH

/** Ouvre le formulaire d'ajout sur [target] (dans l'onglet Accueil), pré-rempli du lien [url] s'il vient d'un partage. */
private fun NavController.openAddGift(target: QuickAddTarget, eventId: UUID?, url: String? = null) {
    // Le formulaire vit dans l'onglet Accueil : on y revient avant de l'ouvrir.
    switchTab(HomeGraph)
    navigate(
        AddGiftRoute(
            childId = target.child.id.toString(),
            kind = target.kind.routeValue(),
            eventId = eventId?.toString(),
            owned = false,
            itemId = null,
            url = url,
        ),
    )
}

/**
 * Ouvre l'écran visé par une notification (#76), dans l'onglet qui le contient. Un cadeau, un événement ou un enfant
 * devenu introuvable ferme son écran de lui-même (voir les destinations), on retombe alors sur l'onglet.
 */
private fun NavController.openNotificationTarget(target: NotificationTarget) {
    when (target) {
        NotificationTarget.Home -> switchTab(HomeGraph)
        NotificationTarget.MyPurchases -> switchTab(PurchasesGraph)
        NotificationTarget.ThanksInbox -> {
            switchTab(PurchasesGraph)
            navigate(ThanksInboxRoute)
        }
        is NotificationTarget.GiftDetail -> {
            switchTab(HomeGraph)
            navigate(GiftDetailRoute(target.itemId.toString()))
        }
        is NotificationTarget.Event -> {
            switchTab(HomeGraph)
            navigate(EventDetailRoute(target.eventId.toString()))
        }
        is NotificationTarget.ChildList -> {
            switchTab(HomeGraph)
            navigate(ChildGiftsRoute(target.childId.toString(), eventId = null, readOnly = false))
        }
    }
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
private fun FamilyHomeRoute(
    viewModelFactory: ViewModelProvider.Factory,
    onOpenEvent: (UUID) -> Unit,
    onNewEvent: () -> Unit,
    onEditEvent: (UUID) -> Unit,
    onOpenChild: (childId: UUID, eventId: UUID?) -> Unit,
    onEditChild: (UUID) -> Unit,
    onAddChild: () -> Unit,
    onAddAdult: () -> Unit,
    onOpenPendingIdeas: () -> Unit,
) {
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
            onOpenEvent = onOpenEvent,
            onNewEvent = onNewEvent,
            onEditEvent = onEditEvent,
            // Une liste s'ouvre dans le contexte de l'événement courant (prochain Noël, sinon prochain événement).
            onOpenChild = { childId -> onOpenChild(childId, state.currentEventId) },
            onEditChild = onEditChild,
            onAddChild = onAddChild,
            onAddAdult = onAddAdult,
            onOpenPendingIdeas = onOpenPendingIdeas,
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

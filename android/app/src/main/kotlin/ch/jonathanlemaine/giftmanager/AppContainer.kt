package ch.jonathanlemaine.giftmanager

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import ch.jonathanlemaine.giftmanager.core.config.AppConfig
import ch.jonathanlemaine.giftmanager.core.gifts.AndroidImageResizer
import ch.jonathanlemaine.giftmanager.core.gifts.HttpLinkPreviewService
import ch.jonathanlemaine.giftmanager.core.gifts.LinkPreviewService
import ch.jonathanlemaine.giftmanager.core.gifts.NoPriceApproximation
import ch.jonathanlemaine.giftmanager.core.gifts.PriceApproximator
import ch.jonathanlemaine.giftmanager.core.model.WishKind
import ch.jonathanlemaine.giftmanager.core.session.AppSession
import ch.jonathanlemaine.giftmanager.data.ChildRepository
import ch.jonathanlemaine.giftmanager.data.EventRepository
import ch.jonathanlemaine.giftmanager.data.FamilyRepository
import ch.jonathanlemaine.giftmanager.data.GiftRepository
import ch.jonathanlemaine.giftmanager.data.HouseholdRepository
import ch.jonathanlemaine.giftmanager.data.ImageUploader
import ch.jonathanlemaine.giftmanager.data.ItemNotifier
import ch.jonathanlemaine.giftmanager.data.ItemRepository
import ch.jonathanlemaine.giftmanager.data.PotRepository
import ch.jonathanlemaine.giftmanager.data.ReservationRepository
import ch.jonathanlemaine.giftmanager.data.ProfileRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseChildRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseEventRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseFamilyRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseGiftRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseHouseholdRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseImageUploader
import ch.jonathanlemaine.giftmanager.data.SupabaseItemNotifier
import ch.jonathanlemaine.giftmanager.data.SupabaseItemRepository
import ch.jonathanlemaine.giftmanager.data.SupabasePotRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseReservationRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProfileRepository
import ch.jonathanlemaine.giftmanager.data.SupabaseProvider
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.CredentialManagerGoogleSource
import ch.jonathanlemaine.giftmanager.data.auth.GoogleCredentialSource
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthBackend
import ch.jonathanlemaine.giftmanager.data.auth.SupabaseAuthRepository
import ch.jonathanlemaine.giftmanager.data.prefs.CurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreCurrentGroupStore
import ch.jonathanlemaine.giftmanager.data.prefs.DataStoreLocalPrefs
import ch.jonathanlemaine.giftmanager.data.prefs.LocalPrefs
import ch.jonathanlemaine.giftmanager.data.prefs.giftDataStore
import ch.jonathanlemaine.giftmanager.net.OkHttpLinkFetcher
import ch.jonathanlemaine.giftmanager.ui.auth.SignInViewModel
import ch.jonathanlemaine.giftmanager.ui.children.ChildEditorMode
import ch.jonathanlemaine.giftmanager.ui.children.ChildEditorViewModel
import ch.jonathanlemaine.giftmanager.ui.events.EventDetailViewModel
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorMode
import ch.jonathanlemaine.giftmanager.ui.events.EventEditorViewModel
import ch.jonathanlemaine.giftmanager.ui.family.FamilyHomeViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.AddGiftViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.ChildGiftsViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.GiftDetailViewModel
import ch.jonathanlemaine.giftmanager.ui.gifts.QuickAddViewModel
import ch.jonathanlemaine.giftmanager.ui.navigation.AddGiftRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ChildEditorRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.ChildGiftsRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.EventDetailRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.EventEditorRoute
import ch.jonathanlemaine.giftmanager.ui.navigation.GiftDetailRoute
import ch.jonathanlemaine.giftmanager.ui.family.GroupSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.main.MainViewModel
import ch.jonathanlemaine.giftmanager.ui.onboarding.ProfileSetupViewModel
import ch.jonathanlemaine.giftmanager.ui.profile.ProfileViewModel
import ch.jonathanlemaine.giftmanager.ui.root.RootViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

/** Injection de dépendances manuelle : un seul conteneur, créé au lancement de l'app. */
class AppContainer(private val config: AppConfig, private val context: Context) {
    private val supabase by lazy { SupabaseProvider.create(config) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: GiftRepository by lazy { SupabaseGiftRepository(supabase) }

    val googleCredentialSource: GoogleCredentialSource by lazy { CredentialManagerGoogleSource(config.googleWebClientId) }

    val authRepository: AuthRepository by lazy { SupabaseAuthRepository(SupabaseAuthBackend(supabase), config, googleCredentialSource) }

    val profileRepository: ProfileRepository by lazy { SupabaseProfileRepository(supabase) }
    val familyRepository: FamilyRepository by lazy { SupabaseFamilyRepository(supabase) }
    val householdRepository: HouseholdRepository by lazy { SupabaseHouseholdRepository(supabase) }
    val childRepository: ChildRepository by lazy { SupabaseChildRepository(supabase) }
    val eventRepository: EventRepository by lazy { SupabaseEventRepository(supabase) }

    val itemRepository: ItemRepository by lazy { SupabaseItemRepository(supabase) }
    val reservationRepository: ReservationRepository by lazy { SupabaseReservationRepository(supabase) }
    val potRepository: PotRepository by lazy { SupabasePotRepository(supabase) }
    val imageUploader: ImageUploader by lazy { SupabaseImageUploader(supabase) }

    /** Notifications de la famille : envoyées sur le scope de l'app, sans qu'aucun écran n'attende. */
    val itemNotifier: ItemNotifier by lazy { SupabaseItemNotifier(supabase, appScope) }

    /** Aperçu de lien : page relue en HTTP (garde SSRF), analysée localement. */
    val linkPreviewService: LinkPreviewService by lazy { HttpLinkPreviewService(OkHttpLinkFetcher()) }

    /** Prix converti indicatif : aucun tant que les taux de change ne sont pas là (#74). */
    val priceApproximator: PriceApproximator = NoPriceApproximation

    /** Redimensionne les photos choisies avant leur envoi (`AddGiftViewModel.onImagePicked`). */
    val imageResizer: AndroidImageResizer by lazy { AndroidImageResizer(context) }

    val currentGroupStore: CurrentGroupStore by lazy { DataStoreCurrentGroupStore(giftDataStore(context)) }
    val localPrefs: LocalPrefs by lazy { DataStoreLocalPrefs(giftDataStore(context)) }

    /** État global de la session ; commence à suivre l'authentification dès sa première utilisation. */
    val session: AppSession by lazy {
        AppSession(
            authRepository, profileRepository, familyRepository, householdRepository, childRepository, eventRepository,
            currentGroupStore, appScope,
        ).also(AppSession::start)
    }

    /**
     * Fabrique des ViewModel. Ceux d'un écran paramétré (fiche événement, éditeurs, liste et fiche cadeau) lisent leur argument dans la
     * route de navigation : `extras` contient les arguments de l'entrée de pile, que `createSavedStateHandle()`
     * expose et que `toRoute<…>()` décode. Les autres n'ont besoin que de `create(modelClass)`.
     */
    val viewModelFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            createWithArguments(modelClass, extras) ?: create(modelClass)

        @Suppress("UNCHECKED_CAST")
        private fun <T : ViewModel> createWithArguments(modelClass: Class<T>, extras: CreationExtras): T? {
            val viewModel: ViewModel = when {
                modelClass.isAssignableFrom(EventDetailViewModel::class.java) -> {
                    val route = extras.route<EventDetailRoute>()
                    EventDetailViewModel(UUID.fromString(route.eventId), session, eventRepository)
                }
                modelClass.isAssignableFrom(ChildGiftsViewModel::class.java) -> {
                    val route = extras.route<ChildGiftsRoute>()
                    ChildGiftsViewModel(
                        UUID.fromString(route.childId), route.eventId?.let(UUID::fromString), route.readOnly, session, itemRepository,
                    )
                }
                modelClass.isAssignableFrom(GiftDetailViewModel::class.java) -> {
                    val route = extras.route<GiftDetailRoute>()
                    GiftDetailViewModel(
                        UUID.fromString(route.itemId), session, itemRepository, reservationRepository, potRepository, itemNotifier,
                    )
                }
                modelClass.isAssignableFrom(AddGiftViewModel::class.java) -> {
                    val route = extras.route<AddGiftRoute>()
                    AddGiftViewModel(
                        childId = UUID.fromString(route.childId),
                        kind = if (route.kind == AddGiftRoute.KIND_IDEA) WishKind.IDEA else WishKind.WISH,
                        owned = route.owned,
                        eventId = route.eventId?.let(UUID::fromString),
                        itemId = route.itemId?.let(UUID::fromString),
                        session = session,
                        items = itemRepository,
                        images = imageUploader,
                        notifier = itemNotifier,
                        previews = linkPreviewService,
                    )
                }
                modelClass.isAssignableFrom(ChildEditorViewModel::class.java) -> {
                    val route = extras.route<ChildEditorRoute>()
                    val mode = when {
                        route.childId != null -> ChildEditorMode.Edit(UUID.fromString(route.childId))
                        route.adult -> ChildEditorMode.CreateAdult
                        else -> ChildEditorMode.Create
                    }
                    ChildEditorViewModel(mode, session, childRepository)
                }
                modelClass.isAssignableFrom(EventEditorViewModel::class.java) -> {
                    val route = extras.route<EventEditorRoute>()
                    val mode = route.eventId?.let { EventEditorMode.Edit(UUID.fromString(it)) } ?: EventEditorMode.Create
                    EventEditorViewModel(mode, session, eventRepository)
                }
                else -> return null
            }
            return viewModel as T
        }

        private inline fun <reified R : Any> CreationExtras.route(): R = createSavedStateHandle().toRoute<R>()

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            when {
                modelClass.isAssignableFrom(SignInViewModel::class.java) -> SignInViewModel(authRepository)
                modelClass.isAssignableFrom(RootViewModel::class.java) -> RootViewModel(session)
                modelClass.isAssignableFrom(ProfileSetupViewModel::class.java) -> ProfileSetupViewModel(session)
                modelClass.isAssignableFrom(GroupSetupViewModel::class.java) -> GroupSetupViewModel(session)
                modelClass.isAssignableFrom(FamilyHomeViewModel::class.java) -> FamilyHomeViewModel(session)
                modelClass.isAssignableFrom(ProfileViewModel::class.java) -> ProfileViewModel(session, localPrefs)
                modelClass.isAssignableFrom(MainViewModel::class.java) -> MainViewModel(session)
                modelClass.isAssignableFrom(QuickAddViewModel::class.java) -> QuickAddViewModel(session)
                else -> throw IllegalArgumentException("ViewModel inconnu : ${modelClass.name}")
            } as T
    }
}

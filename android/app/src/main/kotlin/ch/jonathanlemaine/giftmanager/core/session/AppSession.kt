package ch.jonathanlemaine.giftmanager.core.session

import ch.jonathanlemaine.giftmanager.core.error.runCatchingCancellable
import ch.jonathanlemaine.giftmanager.core.format.Countries
import ch.jonathanlemaine.giftmanager.core.model.FamilyGroup
import ch.jonathanlemaine.giftmanager.core.model.HouseholdGroup
import ch.jonathanlemaine.giftmanager.data.ChildRepository
import ch.jonathanlemaine.giftmanager.data.EventRepository
import ch.jonathanlemaine.giftmanager.data.FamilyRepository
import ch.jonathanlemaine.giftmanager.data.HouseholdRepository
import ch.jonathanlemaine.giftmanager.data.ProfileRepository
import ch.jonathanlemaine.giftmanager.data.auth.AuthRepository
import ch.jonathanlemaine.giftmanager.data.auth.AuthState
import ch.jonathanlemaine.giftmanager.data.prefs.CurrentGroupStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * État global de la session (miroir de `AppState.swift`) : phase, profil, famille courante et données de
 * référence. Les actions sont des `suspend` qui renvoient un [Result] : l'appelant décide d'afficher
 * l'erreur ([ch.jonathanlemaine.giftmanager.core.error.GiftError.from]). Les rechargements sont sérialisés.
 *
 * Avant chaque chargement de la famille, `ensureBirthdayEvents` (anniversaires automatiques, #43) est appelé ;
 * ses erreurs sont ignorées. Les lectures dérivées (enfants de mon foyer, événements à venir…) sont dans
 * `SessionSelectors.kt`.
 */
class AppSession(
    private val auth: AuthRepository,
    private val profileRepo: ProfileRepository,
    private val familyRepo: FamilyRepository,
    private val householdRepo: HouseholdRepository,
    private val childRepo: ChildRepository,
    private val eventRepo: EventRepository,
    private val currentGroupStore: CurrentGroupStore,
    private val scope: CoroutineScope,
    /**
     * Appelé à la déconnexion, tant que la session est encore valide (retrait du jeton de notifications push, #76).
     * Ses échecs sont ignorés : rien ne doit empêcher de se déconnecter.
     */
    private val beforeSignOut: suspend () -> Unit = {},
) {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _pendingInviteCode = MutableStateFlow<String?>(null)

    /** Code reçu par lien, utilisé par l'écran « Rejoindre » ou la confirmation de l'écran principal. */
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    private val _pendingSharedLink = MutableStateFlow<String?>(null)

    /** Lien partagé depuis une autre app, en attente d'être ouvert dans l'ajout de cadeau (survit à la connexion). */
    val pendingSharedLink: StateFlow<String?> = _pendingSharedLink.asStateFlow()

    private val _shareWithoutLink = MutableStateFlow(false)

    /** Un partage de texte sans lien est arrivé : l'écran principal l'annonce. */
    val shareWithoutLink: StateFlow<Boolean> = _shareWithoutLink.asStateFlow()

    private val _itemsRevision = MutableStateFlow(0)

    /**
     * Compteur incrémenté quand les cadeaux ont pu changer ([itemsChanged]) : les écrans qui affichent des
     * données dérivées des listes (compteurs d'équilibre, #41) se rechargent en l'observant.
     */
    val itemsRevision: StateFlow<Int> = _itemsRevision.asStateFlow()

    private val lock = Mutex()
    private var started = false

    /** Suit la session d'authentification. Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // collectLatest : une déconnexion interrompt le chargement en cours.
            auth.state.collectLatest { authState ->
                when (authState) {
                    AuthState.Loading -> _state.update { if (it.userId == null) it.copy(phase = SessionPhase.Launching) else it }
                    AuthState.SignedOut -> reset()
                    is AuthState.SignedIn -> didSignIn(authState.userId)
                }
            }
        }
    }

    private suspend fun didSignIn(userId: UUID) {
        val current = _state.value
        // Un renouvellement de jeton ne doit pas recharger les données.
        if (current.userId == userId && current.phase != SessionPhase.Launching && current.phase != SessionPhase.SignedOut) return
        _state.value = SessionState(phase = SessionPhase.Launching, userId = userId)
        refreshAll()
    }

    private fun reset() {
        _state.value = SessionState(phase = SessionPhase.SignedOut)
    }

    // region Chargement

    /** Recharge profil, familles et données de la famille courante, puis recalcule la phase. */
    suspend fun refreshAll(): Result<Unit> = lock.withLock { loadAll() }

    /** Recharge uniquement les données de la famille courante (tirer pour rafraîchir). */
    suspend fun refreshGroupData(): Result<Unit> = lock.withLock {
        val group = _state.value.currentGroup ?: return@withLock Result.success(Unit)
        runCatchingCancellable { loadGroupData(group) }
    }

    /** Recharge les données de la famille courante après une modification (enfant, événement). */
    suspend fun reloadGroup(): Result<Unit> = refreshGroupData()

    /** Signale que des cadeaux ont changé (voir [itemsRevision]). */
    fun itemsChanged() {
        _itemsRevision.update { it + 1 }
    }

    private suspend fun loadAll(): Result<Unit> {
        val userId = _state.value.userId ?: return Result.success(Unit)
        return try {
            val profile = profileRepo.myProfile(userId)
            if (!profile.onboarded) {
                updateFor(userId) { it.copy(profile = profile, phase = SessionPhase.NeedsProfile, loadFailed = false) }
                return Result.success(Unit)
            }
            val groups = familyRepo.myGroups()
            val saved = currentGroupStore.get()
            val current = groups.firstOrNull { it.id == saved } ?: groups.firstOrNull()
            updateFor(userId) { it.copy(profile = profile, groups = groups, currentGroup = current) }
            if (current == null) {
                updateFor(userId) { it.copy(phase = SessionPhase.NeedsGroup, loadFailed = false) }
                return Result.success(Unit)
            }
            loadGroupData(current)
            updateFor(userId) {
                it.copy(phase = if (it.myHousehold == null) SessionPhase.NeedsGroup else SessionPhase.Ready, loadFailed = false)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Premier chargement raté : on ne reste pas bloqué sur l'écran de chargement (comme iOS).
            updateFor(userId) {
                if (it.phase == SessionPhase.Launching) it.copy(phase = SessionPhase.SignedOut, loadFailed = true) else it
            }
            Result.failure(e)
        }
    }

    private suspend fun loadGroupData(group: FamilyGroup) {
        val userId = _state.value.userId
        // Anniversaires automatiques : une erreur ici ne doit pas bloquer le chargement.
        runCatchingCancellable { eventRepo.ensureBirthdayEvents(group.id) }
        coroutineScope {
            val children = async { childRepo.children(group.id) }
            val events = async { eventRepo.events() }
            val households = async { householdRepo.households(group.id) }
            val householdMembers = async { householdRepo.householdMembers(group.id) }
            val groupMembers = async { familyRepo.groupMembers(group.id) }
            val householdGroups = async { runCatchingCancellable { householdRepo.myHouseholdGroups() } }
            val loadedHouseholds = households.await()
            val loadedHouseholdMembers = householdMembers.await()
            val members = profileRepo.profiles(groupMembers.await().map { it.userId })
            val sharing: List<HouseholdGroup> = householdGroups.await().getOrDefault(emptyList())
            // Un enfant peut remonter plusieurs fois s'il est visible via plusieurs partages (#60).
            val loadedChildren = children.await().distinctBy { it.id }
            val loadedEvents = events.await()
            updateFor(userId) {
                it.copy(
                    households = loadedHouseholds,
                    householdMembers = loadedHouseholdMembers,
                    householdGroups = sharing,
                    members = members,
                    children = loadedChildren,
                    events = loadedEvents,
                )
            }
        }
    }

    /** N'applique la mise à jour que si la session est toujours celle de [userId] (déconnexion pendant un chargement). */
    private fun updateFor(userId: UUID?, transform: (SessionState) -> SessionState) {
        _state.update { if (it.userId == userId) transform(it) else it }
    }

    // endregion

    // region Profil

    suspend fun saveProfile(name: String, country: String, currency: String): Result<Unit> = runCatchingCancellable {
        val userId = requireUser()
        profileRepo.updateProfile(userId, name, country, currency, onboarded = true)
        val phase = _state.value.phase
        updateFor(userId) { state ->
            state.copy(
                profile = state.profile?.copy(displayName = name, country = country, currency = currency, onboarded = true),
            )
        }
        if (phase == SessionPhase.NeedsProfile) refreshAll().getOrThrow()
    }

    /** Préférence serveur des rappels d'anniversaire (#43). */
    suspend fun setBirthdayReminders(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        val userId = requireUser()
        if (_state.value.profile?.notifyBirthdayReminders == enabled) return@runCatchingCancellable
        profileRepo.setBirthdayReminders(userId, enabled)
        updateFor(userId) { it.copy(profile = it.profile?.copy(notifyBirthdayReminders = enabled)) }
    }

    /** Rappels d'achat envoyés par le serveur (#59). */
    suspend fun setPurchaseReminders(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        val userId = requireUser()
        if (_state.value.profile?.notifyPurchaseReminders == enabled) return@runCatchingCancellable
        profileRepo.setPurchaseReminders(userId, enabled)
        updateFor(userId) { it.copy(profile = it.profile?.copy(notifyPurchaseReminders = enabled)) }
    }

    // endregion

    // region Famille et foyer

    suspend fun selectGroup(group: FamilyGroup): Result<Unit> = runCatchingCancellable {
        currentGroupStore.set(group.id)
        _state.update { it.copy(currentGroup = group) }
        refreshAll().getOrThrow()
    }

    suspend fun createGroup(name: String, householdName: String): Result<Unit> = runCatchingCancellable {
        val id = familyRepo.createGroup(name, householdName, country())
        currentGroupStore.set(id)
        refreshAll().getOrThrow()
    }

    /**
     * Rejoint une famille et la rend courante ; renvoie son identifiant. Le foyer est choisi ou créé ensuite
     * (la phase reste `NeedsGroup`) ; hors de ce parcours, l'appelant enchaîne [refreshAll].
     */
    suspend fun joinGroup(code: String): Result<UUID> = runCatchingCancellable {
        val id = familyRepo.joinGroup(code)
        currentGroupStore.set(id)
        lock.withLock {
            val groups = familyRepo.myGroups()
            val current = groups.firstOrNull { it.id == id }
            _state.update { it.copy(groups = groups, currentGroup = current) }
            if (current != null) loadGroupData(current)
        }
        id
    }

    suspend fun renameGroup(groupId: UUID, name: String): Result<Unit> = runCatchingCancellable {
        familyRepo.renameGroup(groupId, name)
        refreshAll().getOrThrow()
    }

    suspend fun createHousehold(name: String): Result<Unit> = runCatchingCancellable {
        val group = _state.value.currentGroup ?: error("Aucune famille courante")
        householdRepo.createHousehold(group.id, name, country())
        refreshAll().getOrThrow()
    }

    suspend fun joinHousehold(code: String): Result<Unit> = runCatchingCancellable {
        householdRepo.joinHousehold(code)
        refreshAll().getOrThrow()
    }

    /** Partage mon foyer avec la famille courante (#60) : les enfants y apparaissent sans ressaisie. */
    suspend fun shareHousehold(): Result<Unit> = runCatchingCancellable {
        val group = _state.value.currentGroup ?: error("Aucune famille courante")
        householdRepo.shareHousehold(group.id)
        refreshAll().getOrThrow()
    }

    suspend fun unshareHousehold(group: FamilyGroup): Result<Unit> = runCatchingCancellable {
        householdRepo.unshareHousehold(group.id)
        refreshAll().getOrThrow()
    }

    // endregion

    // region Compte

    suspend fun signOut() {
        runCatchingCancellable { beforeSignOut() }
        runCatchingCancellable { auth.signOut() }
        // Le lien partagé appartenait à la session qui se ferme.
        clearPendingSharedLink()
        clearShareWithoutLink()
        reset()
    }

    /** Supprime le compte puis déconnecte (erreurs de déconnexion ignorées, comme iOS). */
    suspend fun deleteAccount(): Result<Unit> = runCatchingCancellable {
        profileRepo.deleteAccount()
        signOut()
    }

    // endregion

    // region Invitations

    /** Mémorise le code d'un lien `famillecadeaux://join/CODE` ; ignore tout autre lien. */
    fun handleDeepLink(uri: String?) {
        InviteLink.parse(uri)?.let { _pendingInviteCode.value = it }
    }

    fun clearPendingInviteCode() {
        _pendingInviteCode.value = null
    }

    // endregion

    // region Partage

    /** Mémorise un lien http(s) partagé ; un nouveau partage remplace le précédent. */
    fun handleSharedLink(url: String) {
        _shareWithoutLink.value = false
        _pendingSharedLink.value = url
    }

    /** Partage de texte sans lien : pas de formulaire vide, seulement une alerte. */
    fun handleShareWithoutLink() {
        _pendingSharedLink.value = null
        _shareWithoutLink.value = true
    }

    fun clearPendingSharedLink() {
        _pendingSharedLink.value = null
    }

    fun clearShareWithoutLink() {
        _shareWithoutLink.value = false
    }

    // endregion

    private fun requireUser(): UUID = _state.value.userId ?: error("Aucune session")

    private fun country(): String = _state.value.profile?.country ?: Countries.DEFAULT_COUNTRY
}

import Foundation
import Observation
import Supabase

/// État global : session, profil, famille courante et ses données de référence.
@MainActor
@Observable
final class AppState {
    enum Phase: Equatable {
        case launching
        case signedOut
        case needsProfile
        case needsGroup
        case ready
    }

    private(set) var phase: Phase = .launching
    private(set) var userId: UUID?
    var profile: Profile?

    private(set) var groups: [FamilyGroup] = []
    private(set) var currentGroup: FamilyGroup?
    private(set) var households: [Household] = []
    private(set) var householdMembers: [HouseholdMember] = []
    /// Familles avec lesquelles mon foyer est partagé (#60).
    private(set) var householdGroups: [HouseholdGroup] = []
    private(set) var members: [Profile] = []
    private(set) var children: [Child] = []
    private(set) var events: [GiftEvent] = []

    /// Incrémenté après chaque modification de cadeaux pour que les écrans se rechargent.
    private(set) var itemsRevision = 0
    /// Dernier chargement de la famille (enfants, foyers, événements des autres membres).
    private var lastGroupRefresh: Date?

    var errorMessage: String?
    /// Prénom fourni par Apple à la première connexion, proposé à la création du profil.
    var suggestedName: String?

    let repository: GiftRepository
    private var authTask: Task<Void, Never>?

    private static let currentGroupKey = "currentGroupId"

    init(repository: GiftRepository = GiftRepository()) {
        self.repository = repository
    }

    /// Affiche une erreur à l'utilisateur, sauf les annulations (écran quitté, rafraîchissement interrompu).
    func report(_ error: Error) {
        if error is CancellationError || (error as? URLError)?.code == .cancelled { return }
        if String(describing: error).contains("CancellationError") { return }
        errorMessage = GiftError(error).localizedDescription
    }

    // MARK: - Session

    func start() {
        guard authTask == nil else { return }
        authTask = Task { [weak self] in
            guard let self else { return }
            for await (event, session) in repository.client.auth.authStateChanges {
                switch event {
                case .initialSession, .signedIn, .userUpdated, .tokenRefreshed:
                    if let session, !session.isExpired {
                        await self.didSignIn(userId: session.user.id)
                    } else if event == .initialSession {
                        self.reset()
                    }
                case .signedOut, .userDeleted:
                    self.reset()
                default:
                    break
                }
            }
        }
    }

    private func didSignIn(userId: UUID) async {
        guard self.userId != userId || phase == .launching || phase == .signedOut else { return }
        self.userId = userId
        await refreshAll()
    }

    private func reset() {
        NotificationRouter.shared.reset()
        userId = nil
        profile = nil
        groups = []
        currentGroup = nil
        households = []
        householdMembers = []
        householdGroups = []
        members = []
        children = []
        events = []
        phase = .signedOut
    }

    func signOut() async {
        try? await repository.client.auth.signOut()
        reset()
    }

    func deleteAccount() async {
        do {
            try await repository.deleteAccount()
            try? await repository.client.auth.signOut()
            reset()
        } catch {
            report(error)
        }
    }

    // MARK: - Chargement

    func refreshAll() async {
        guard let userId else { return }
        do {
            profile = try await repository.myProfile(userId: userId)
            guard profile?.onboarded == true else {
                phase = .needsProfile
                return
            }
            groups = try await repository.myGroups()
            let saved = UserDefaults.standard.string(forKey: Self.currentGroupKey).flatMap(UUID.init(uuidString:))
            currentGroup = groups.first { $0.id == saved } ?? groups.first
            guard currentGroup != nil else {
                phase = .needsGroup
                return
            }
            try await refreshGroupData()
            phase = myHousehold == nil ? .needsGroup : .ready
            if phase == .ready { await setUpNotifications() }
        } catch {
            report(error)
            if phase == .launching { phase = .signedOut }
        }
    }

    func refreshGroupData() async throws {
        guard let group = currentGroup else { return }
        // Anniversaires automatiques (#43) : crée celui de l'an prochain une fois le précédent passé.
        // Sans effet si tout est à jour ; une erreur ici ne doit pas bloquer le chargement.
        try? await repository.ensureBirthdayEvents(groupId: group.id)
        async let households = repository.households(groupId: group.id)
        async let householdMembers = repository.householdMembers(groupId: group.id)
        async let groupMembers = repository.groupMembers(groupId: group.id)
        async let children = repository.children(groupId: group.id)
        async let events = repository.events(groupId: group.id)
        async let householdGroups = repository.myHouseholdGroups()
        self.households = try await households
        self.householdMembers = try await householdMembers
        // Un enfant peut remonter plusieurs fois s'il est visible via plusieurs partages (#60).
        self.children = try await children.reduce(into: [Child]()) { unique, child in
            if !unique.contains(where: { $0.id == child.id }) { unique.append(child) }
        }
        self.events = try await events
        self.householdGroups = (try? await householdGroups) ?? []
        members = try await repository.profiles(ids: try await groupMembers.map(\.userId))
        lastGroupRefresh = .now
    }

    /// Au retour au premier plan : les autres foyers ont pu ajouter des enfants, des événements ou des cadeaux
    /// pendant que l'app était en arrière-plan. Au plus une fois toutes les 30 s.
    func refreshOnForeground() async {
        guard phase == .ready else { return }
        if let last = lastGroupRefresh, Date.now.timeIntervalSince(last) < 30 { return }
        await reloadGroup()
        itemsChanged()
    }

    func reloadGroup() async {
        do {
            try await refreshGroupData()
        } catch {
            report(error)
        }
    }

    func selectGroup(_ group: FamilyGroup) async {
        UserDefaults.standard.set(group.id.uuidString, forKey: Self.currentGroupKey)
        currentGroup = group
        await refreshAll()
    }

    /// Préférence serveur des rappels d'anniversaire (#43).
    func setBirthdayReminders(_ enabled: Bool) async {
        guard let userId, profile?.notifyBirthdayReminders != enabled else { return }
        do {
            try await repository.setBirthdayReminders(userId: userId, enabled: enabled)
            profile?.notifyBirthdayReminders = enabled
        } catch {
            report(error)
        }
    }

    func itemsChanged() {
        itemsRevision += 1
    }

    // MARK: - Notifications

    func setUpNotifications() async {
        let notifications = NotificationService.shared
        notifications.onDeviceToken = { [repository] token in
            #if DEBUG
            let environment = "sandbox"
            #else
            let environment = "production"
            #endif
            Task { try? await repository.registerDevice(token: token, environment: environment) }
        }
        await notifications.registerForRemote()
        await refreshReminders()
    }

    func refreshReminders() async {
        guard let reservations = try? await repository.myReservations() else { return }
        await NotificationService.shared.scheduleReminders(reservations: reservations, events: events)
    }

    // MARK: - Invitations

    /// Code reçu par lien (famillecadeaux://join/CODE), utilisé par l'écran « Rejoindre ».
    var pendingInviteCode: String?

    func handleDeepLink(_ url: URL) {
        guard url.scheme == "famillecadeaux", url.host() == "join" else { return }
        let code = url.pathComponents.dropFirst().first ?? URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "code" }?.value
        guard let code, !code.isEmpty else { return }
        // En phase .ready, MainTabView demande confirmation avant de rejoindre.
        pendingInviteCode = code.uppercased()
    }

    static func inviteMessage(for group: FamilyGroup) -> String {
        """
        Rejoins « \(group.name) » sur Gift Manager pour coordonner les cadeaux des enfants 🎁
        Code d'invitation : \(group.inviteCode)
        famillecadeaux://join/\(group.inviteCode)
        """
    }

    // MARK: - Profil et famille

    func saveProfile(name: String, country: String, currency: String) async {
        guard let userId else { return }
        let updated = Profile(id: userId, displayName: name, country: country, currency: currency, onboarded: true,
                              notifyBirthdayReminders: profile?.notifyBirthdayReminders ?? true)
        do {
            try await repository.updateProfile(updated)
            profile = updated
            if phase == .needsProfile { await refreshAll() }
        } catch {
            report(error)
        }
    }

    func createGroup(name: String, householdName: String) async -> Bool {
        do {
            let id = try await repository.createGroup(name: name, householdName: householdName,
                                                      country: profile?.country ?? "CH")
            UserDefaults.standard.set(id.uuidString, forKey: Self.currentGroupKey)
            await refreshAll()
            return true
        } catch {
            report(error)
            return false
        }
    }

    /// Rejoint un groupe ; renvoie son id. Le foyer est choisi ou créé ensuite.
    func joinGroup(code: String) async -> UUID? {
        do {
            let id = try await repository.joinGroup(code: code)
            UserDefaults.standard.set(id.uuidString, forKey: Self.currentGroupKey)
            groups = try await repository.myGroups()
            currentGroup = groups.first { $0.id == id }
            try await refreshGroupData()
            return id
        } catch {
            report(error)
            return nil
        }
    }

    func createHousehold(name: String) async -> Bool {
        guard let group = currentGroup else { return false }
        do {
            _ = try await repository.createHousehold(groupId: group.id, name: name, country: profile?.country ?? "CH")
            await refreshAll()
            return true
        } catch {
            report(error)
            return false
        }
    }

    func joinHousehold(code: String) async -> Bool {
        do {
            _ = try await repository.joinHousehold(code: code)
            await refreshAll()
            return true
        } catch {
            report(error)
            return false
        }
    }

    /// Partage le foyer existant avec la famille courante (#60) : les enfants y apparaissent
    /// sans ressaisie.
    func shareHouseholdWithCurrentGroup() async -> Bool {
        guard let group = currentGroup else { return false }
        do {
            try await repository.shareHousehold(withGroup: group.id)
            await refreshAll()
            return true
        } catch {
            report(error)
            return false
        }
    }

    func unshareHousehold(from group: FamilyGroup) async -> Bool {
        do {
            try await repository.unshareHousehold(fromGroup: group.id)
            await refreshAll()
            return true
        } catch {
            report(error)
            return false
        }
    }

    // MARK: - Dérivés

    var myHousehold: Household? {
        guard let userId,
              let membership = householdMembers.first(where: { $0.userId == userId }) else { return nil }
        return households.first { $0.id == membership.householdId }
    }

    /// Familles où mon foyer est visible, hors celle en cours de consultation.
    var familiesSharingMyHousehold: [FamilyGroup] {
        guard let householdId = myHousehold?.id else { return [] }
        let ids = Set(householdGroups.filter { $0.householdId == householdId }.map(\.groupId))
        return groups.filter { ids.contains($0.id) }
    }

    /// Mon foyer existe mais n'est pas encore partagé avec la famille affichée.
    var canShareHouseholdWithCurrentGroup: Bool {
        guard let householdId = myHousehold?.id, let group = currentGroup else { return false }
        return !householdGroups.contains { $0.householdId == householdId && $0.groupId == group.id }
    }

    var myChildren: [Child] {
        guard let id = myHousehold?.id else { return [] }
        return children.filter { $0.householdId == id }
    }

    var otherChildren: [Child] {
        guard let id = myHousehold?.id else { return children }
        return children.filter { $0.householdId != id }
    }

    func isParent(of child: Child) -> Bool {
        child.householdId == myHousehold?.id
    }

    func household(of child: Child) -> Household? {
        households.first { $0.id == child.householdId }
    }

    func parents(of household: Household) -> [Profile] {
        let ids = Set(householdMembers.filter { $0.householdId == household.id }.map(\.userId))
        return members.filter { ids.contains($0.id) }
    }

    /// Enfants puis listes d'adultes du foyer.
    func children(of household: Household) -> [Child] {
        let members = children.filter { $0.householdId == household.id }
        return members.filter { !$0.isAdult } + members.filter(\.isAdult)
    }

    var upcomingEvents: [GiftEvent] {
        events.filter { !$0.isPast }.sorted { $0.eventDate < $1.eventDate }
    }

    var pastEvents: [GiftEvent] {
        events.filter(\.isPast).sorted { $0.eventDate > $1.eventDate }
    }

    /// Événement « courant » : le prochain Noël, sinon le prochain événement.
    var currentEvent: GiftEvent? {
        upcomingEvents.first { $0.kind == .christmas } ?? upcomingEvents.first
    }

    func child(_ id: UUID?) -> Child? {
        children.first { $0.id == id }
    }

    /// Listes concernées par un événement (toutes pour Noël, celle du fêté pour un anniversaire),
    /// listes d'adultes comprises.
    func children(for event: GiftEvent) -> [Child] {
        if let childId = event.childId { return children.filter { $0.id == childId } }
        return children
    }
}

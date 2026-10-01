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
    private(set) var members: [Profile] = []
    private(set) var children: [Child] = []
    private(set) var events: [GiftEvent] = []

    /// Incrémenté après chaque modification de cadeaux pour que les écrans se rechargent.
    private(set) var itemsRevision = 0

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
        userId = nil
        profile = nil
        groups = []
        currentGroup = nil
        households = []
        householdMembers = []
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
        } catch {
            report(error)
            if phase == .launching { phase = .signedOut }
        }
    }

    func refreshGroupData() async throws {
        guard let group = currentGroup else { return }
        async let households = repository.households(groupId: group.id)
        async let householdMembers = repository.householdMembers(groupId: group.id)
        async let groupMembers = repository.groupMembers(groupId: group.id)
        async let children = repository.children(groupId: group.id)
        async let events = repository.events(groupId: group.id)
        self.households = try await households
        self.householdMembers = try await householdMembers
        self.children = try await children
        self.events = try await events
        members = try await repository.profiles(ids: try await groupMembers.map(\.userId))
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

    func itemsChanged() {
        itemsRevision += 1
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
        Rejoins « \(group.name) » sur Famille Cadeaux pour coordonner les cadeaux des enfants 🎁
        Code d'invitation : \(group.inviteCode)
        famillecadeaux://join/\(group.inviteCode)
        """
    }

    // MARK: - Profil et famille

    func saveProfile(name: String, country: String, currency: String) async {
        guard let userId else { return }
        let updated = Profile(id: userId, displayName: name, country: country, currency: currency, onboarded: true)
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

    // MARK: - Dérivés

    var myHousehold: Household? {
        guard let userId,
              let membership = householdMembers.first(where: { $0.userId == userId }) else { return nil }
        return households.first { $0.id == membership.householdId }
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

    func children(of household: Household) -> [Child] {
        children.filter { $0.householdId == household.id }
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

    /// Enfants concernés par un événement (tous pour Noël, l'enfant pour un anniversaire).
    func children(for event: GiftEvent) -> [Child] {
        if let childId = event.childId { return children.filter { $0.id == childId } }
        return children
    }
}

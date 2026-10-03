import Foundation
import Observation

/// Cadeaux d'un enfant (souhaits, possédés, idées) avec leurs liens.
@MainActor
@Observable
final class GiftListModel {
    let child: Child
    /// Événement affiché ; `nil` = tous les cadeaux de l'enfant, tous événements confondus (#61).
    private(set) var eventId: UUID?
    private(set) var items: [WishItem] = []
    private(set) var links: [UUID: [ItemLink]] = [:]
    private(set) var isLoading = false
    private(set) var hasLoaded = false

    init(child: Child, eventId: UUID?) {
        self.child = child
        self.eventId = eventId
    }

    var wishes: [WishItem] { items.filter { $0.kind == .wish && !$0.owned } }
    var owned: [WishItem] { items.filter { $0.kind == .wish && $0.owned } }
    var ideas: [WishItem] { items.filter { $0.kind == .idea } }

    /// Change l'événement affiché et recharge. Un cadeau rangé ailleurs n'est plus « perdu ».
    func select(eventId: UUID?, repository: GiftRepository) async throws {
        guard eventId != self.eventId else { return }
        self.eventId = eventId
        hasLoaded = false
        try await load(repository)
    }

    func load(_ repository: GiftRepository) async throws {
        isLoading = true
        defer { isLoading = false }
        let fetched = try await repository.childItems(childId: child.id, eventId: eventId)
        let fetchedLinks = try await repository.links(itemIds: fetched.map(\.id))
        items = fetched
        links = Dictionary(grouping: fetchedLinks, by: \.itemId)
        hasLoaded = true
    }

    func links(for item: WishItem, preferredCountry: String?) -> [ItemLink] {
        StoreCatalog.sorted(links[item.id] ?? [], preferredCountry: preferredCountry)
    }

    /// Mise à jour locale optimiste après une action (réservation, favori…).
    func update(_ item: WishItem) {
        if let index = items.firstIndex(where: { $0.id == item.id }) { items[index] = item }
    }

    func remove(_ itemId: UUID) {
        items.removeAll { $0.id == itemId }
    }

    func move(wishesFrom source: IndexSet, to destination: Int) -> [UUID] {
        var ordered = wishes
        ordered.move(fromOffsets: source, toOffset: destination)
        for (index, item) in ordered.enumerated() {
            var copy = item
            copy.position = index
            update(copy)
        }
        let others = items.filter { !($0.kind == .wish && !$0.owned) }
        items = ordered + others
        return ordered.map(\.id)
    }
}

extension WishItem {
    /// Statut affiché : `nil` pour les parents (mode surprise).
    func displayStatus(isParent: Bool) -> GiftStatus? {
        guard !isParent else { return nil }
        switch status {
        case .available: return .available
        case .taken: return .taken
        case .mine: return .mine
        case .owned: return .owned
        case .pot: return .pot
        case nil: return nil
        }
    }
}

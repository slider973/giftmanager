import Foundation
import Observation

/// Ce que l'enfant voit d'un souhait : une photo, un nom, un cœur.
///
/// Volontairement sans prix, statut, réservation, cagnotte ni notes : ce qui n'existe pas
/// dans ce type ne peut pas s'afficher par erreur en mode enfant (#36).
struct ChildModeItem: Identifiable, Equatable, Sendable {
    let id: UUID
    let title: String
    let imageURL: URL?
    var isFavorite: Bool
}

/// Mode enfant : l'enfant parcourt sa liste et pose des cœurs « très envie » (#36).
@MainActor
@Observable
final class ChildModeModel {
    let child: Child
    private(set) var items: [ChildModeItem]
    /// Cœurs en cours d'enregistrement : un second tap est ignoré jusqu'à la réponse.
    private(set) var saving: Set<UUID> = []
    /// Au moins un cœur a été enregistré : la liste du parent doit se recharger à la sortie.
    private(set) var didChange = false

    @ObservationIgnored private let savePriority: (UUID, Bool) async throws -> Void

    init(child: Child, wishes: [WishItem], savePriority: @escaping (UUID, Bool) async throws -> Void) {
        self.child = child
        self.items = Self.visibleItems(wishes)
        self.savePriority = savePriority
    }

    /// Seuls les souhaits de la liste : jamais les idées des autres adultes (secrètes pour les parents)
    /// ni les jouets déjà possédés. L'ordre choisi par les parents est conservé.
    nonisolated static func visibleItems(_ items: [WishItem]) -> [ChildModeItem] {
        items
            .filter { $0.kind == .wish && !$0.owned }
            .map { ChildModeItem(id: $0.id, title: $0.title, imageURL: $0.imageURL, isFavorite: $0.isFavorite) }
    }

    var favoriteCount: Int { items.filter(\.isFavorite).count }

    /// Bascule le cœur tout de suite, puis l'enregistre sur le souhait.
    /// En cas d'échec, le cœur revient à son état précédent et l'erreur remonte.
    func toggleFavorite(_ id: UUID) async throws {
        guard !saving.contains(id), let index = items.firstIndex(where: { $0.id == id }) else { return }
        let newValue = !items[index].isFavorite
        items[index].isFavorite = newValue
        saving.insert(id)
        defer { saving.remove(id) }
        do {
            try await savePriority(id, newValue)
            didChange = true
        } catch {
            if let current = items.firstIndex(where: { $0.id == id }) { items[current].isFavorite = !newValue }
            throw error
        }
    }
}

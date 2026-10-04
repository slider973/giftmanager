import Foundation

/// Les seules opérations distantes dont l'écran d'ajout/modification de cadeau a besoin.
///
/// Comme `GiftDetailActions`, ce protocole reste volontairement étroit : un test n'a que
/// ces méthodes à fournir, et toute nouvelle dépendance de l'écran se signale à la compilation.
protocol AddGiftActions: Sendable {
    func childItems(childId: UUID, eventId: UUID?, kind: WishKind?) async throws -> [WishItem]
    func links(itemIds: [UUID]) async throws -> [ItemLink]
    func ideaAudience(childId: UUID) async throws -> Int
    func uploadImage(_ data: Data, userId: UUID) async throws -> URL
    func addItem(_ item: GiftRepository.NewItem, links: [DraftLink]) async throws -> UUID
    func updateItem(id: UUID, title: String, notes: String?, imageUrl: String?,
                    priority: Int, eventId: UUID?) async throws
    func replaceLinks(itemId: UUID, links: [DraftLink]) async throws
    func notifyNewItems(_ itemIds: [UUID]) async
}

/// `GiftRepository` possède déjà toutes ces méthodes : les valeurs par défaut de
/// `childItems` suffisent à satisfaire le protocole, rien à écrire.
extension GiftRepository: AddGiftActions {}

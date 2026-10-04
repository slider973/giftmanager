import Foundation

/// Les seules opérations distantes de l'écran « Mes achats ».
protocol MyPurchasesActions: Sendable {
    func myReservations() async throws -> [MyReservation]
    func myContributions() async throws -> [MyContribution]
    func myThanks() async throws -> [ThanksNote]
    func links(itemIds: [UUID]) async throws -> [ItemLink]
    func setPurchased(itemId: UUID, purchased: Bool) async throws
    func cancelReservation(itemId: UUID) async throws
    func leavePot(itemId: UUID) async throws
}

/// `GiftRepository` possède déjà toutes ces méthodes : rien à écrire.
extension GiftRepository: MyPurchasesActions {}

import Foundation

/// Les seules opérations distantes dont la fiche cadeau a besoin.
///
/// Protocole volontairement restreint aux 10 méthodes utilisées par `GiftDetailView` plutôt
/// qu'une abstraction de tout `GiftRepository` (~40 méthodes) : un test n'a ainsi qu'une
/// poignée de méthodes à fournir, et le jour où la fiche demande autre chose, le compilateur
/// le signale ici.
protocol GiftDetailActions: Sendable {
    func setPriority(itemId: UUID, favorite: Bool) async throws
    func setOwned(itemId: UUID, owned: Bool) async throws
    func deleteItem(_ itemId: UUID) async throws
    func reserve(itemId: UUID) async throws
    func cancelReservation(itemId: UUID) async throws
    func setPurchased(itemId: UUID, purchased: Bool) async throws
    func leavePot(itemId: UUID) async throws
    func potParticipants(itemId: UUID) async throws -> [PotParticipant]
    func itemDonors(itemId: UUID) async throws -> [ItemDonor]
    func notifyReservation(itemId: UUID) async
}

/// `GiftRepository` possède déjà toutes ces méthodes : rien à écrire.
extension GiftRepository: GiftDetailActions {}

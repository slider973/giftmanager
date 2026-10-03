import Foundation
import Supabase

// Suivi des prix des cadeaux réservés (#39) : l'historique n'est renvoyé qu'à la personne
// qui a réservé le cadeau ; pour tout autre utilisateur, parents compris, la liste est vide.

/// Relevé de prix d'un lien.
struct PriceCheck: Codable, Equatable, Hashable, Sendable {
    let linkId: UUID
    let url: String
    let price: Decimal?
    let currency: String?
    let inStock: Bool?
    let checkedAt: Date

    enum CodingKeys: String, CodingKey {
        case url, price, currency
        case linkId = "link_id"
        case inStock = "in_stock"
        case checkedAt = "checked_at"
    }
}

extension GiftRepository {
    // MARK: - Suivi des prix

    /// Historique des liens d'un cadeau, du plus récent au plus ancien. Vide si je ne l'ai pas réservé.
    func priceHistory(itemId: UUID) async throws -> [PriceCheck] {
        try await client.schema("public").rpc("my_price_history", params: ["p_item": itemId.uuidString]).execute().value
    }
}

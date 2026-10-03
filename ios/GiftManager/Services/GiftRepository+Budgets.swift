import Foundation
import Supabase

// Budgets privés (#40) : un budget n'est lu et modifié que par son auteur (RLS).

/// Budget d'un utilisateur pour un enfant et/ou un événement, dans une devise.
struct Budget: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    var childId: UUID?
    /// `nil` si l'enfant n'est plus visible (groupe quitté).
    var childName: String?
    var eventId: UUID?
    var eventTitle: String?
    var amount: Decimal
    var currency: String
    /// Déjà engagé dans cette devise : cadeaux réservés ou achetés + parts de cagnotte.
    var spent: Decimal

    enum CodingKeys: String, CodingKey {
        case id, amount, currency, spent
        case childId = "child_id"
        case childName = "child_name"
        case eventId = "event_id"
        case eventTitle = "event_title"
    }

    /// « Léo · Noël 2026 », « Léo », « Noël 2026 ».
    var scopeTitle: String {
        let parts = [childId == nil ? nil : (childName ?? "Enfant"), eventId == nil ? nil : (eventTitle ?? "Événement")]
        let text = parts.compactMap { $0 }.joined(separator: " · ")
        return text.isEmpty ? "Budget" : text
    }

    var remaining: Decimal { amount - spent }
    var isOver: Bool { spent > amount }
    /// Part consommée, plafonnée à 1 pour la jauge.
    var progress: Double {
        guard amount > 0 else { return 0 }
        return min(1, max(0, NSDecimalNumber(decimal: spent / amount).doubleValue))
    }
}

extension GiftRepository {
    // MARK: - Budgets

    func myBudgets() async throws -> [Budget] {
        try await client.schema("public").rpc("my_budgets").execute().value
    }

    @discardableResult
    func setBudget(childId: UUID?, eventId: UUID?, amount: Decimal, currency: String) async throws -> UUID {
        struct Params: Encodable {
            let p_child: UUID?
            let p_event: UUID?
            let p_amount: Decimal
            let p_currency: String
        }
        return try await client.schema("public")
            .rpc("set_budget", params: Params(p_child: childId, p_event: eventId, p_amount: amount, p_currency: currency.uppercased()))
            .execute().value
    }

    func deleteBudget(_ id: UUID) async throws {
        try await client.schema("public").from("budgets").delete().eq("id", value: id).execute()
    }
}

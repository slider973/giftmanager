import Foundation

// Modèles v2 : cagnotte (#35), équilibre (#41), remerciements (#42).
// Règle d'anonymat : ces types ne portent jamais l'identité d'un acheteur, sauf
// les participants d'une cagnotte entre eux et les donateurs qui se sont dévoilés.

// MARK: - Cagnotte

/// Participant d'une cagnotte, tel que renvoyé par `pot_participants` (réservé aux participants).
struct PotParticipant: Decodable, Equatable, Hashable, Sendable {
    let displayName: String
    let amount: Decimal
    let currency: String
    let isMe: Bool

    enum CodingKeys: String, CodingKey {
        case amount, currency
        case displayName = "display_name"
        case isMe = "is_me"
    }

    var amountText: String { Money.format(amount, currency: currency) ?? "" }
}

/// Une de mes participations, telle que renvoyée par `my_contributions`.
struct MyContribution: Decodable, Identifiable, Equatable, Hashable, Sendable {
    let itemId: UUID
    var title: String
    var imageUrl: String?
    var amount: Decimal
    var currency: String
    var owned: Bool
    var potTotal: Decimal?
    var potCount: Int?
    var childId: UUID
    var childName: String
    var eventId: UUID?
    var eventTitle: String?
    var eventDate: DayDate?

    var id: UUID { itemId }

    enum CodingKeys: String, CodingKey {
        case title, amount, currency, owned
        case itemId = "item_id"
        case imageUrl = "image_url"
        case potTotal = "pot_total"
        case potCount = "pot_count"
        case childId = "child_id"
        case childName = "child_name"
        case eventId = "event_id"
        case eventTitle = "event_title"
        case eventDate = "event_date"
    }
}

/// Progression d'une cagnotte : montant réuni face au prix du cadeau dans la même devise.
struct PotProgress: Equatable, Sendable {
    let collected: Decimal
    let currency: String
    /// Prix du cadeau dans la devise de la cagnotte ; `nil` si aucun lien ne le donne.
    let target: Decimal?
    let participants: Int

    /// Part réunie, entre 0 et 1 ; `nil` sans prix de référence.
    var fraction: Double? {
        guard let target, target > 0 else { return nil }
        let ratio = NSDecimalNumber(decimal: collected / target).doubleValue
        return min(max(ratio, 0), 1)
    }

    /// Reste à réunir (jamais négatif) ; `nil` sans prix de référence.
    var remaining: Decimal? {
        guard let target else { return nil }
        return max(target - collected, 0)
    }

    var isComplete: Bool { remaining == 0 }

    var collectedText: String { Money.format(collected, currency: currency) ?? "" }
    var targetText: String? { Money.format(target, currency: currency) }
    var remainingText: String? { Money.format(remaining, currency: currency) }

    var participantsText: String {
        participants > 1 ? "\(participants) participants" : "\(participants) participant"
    }

    /// Prix de référence : premier lien (dans l'ordre de préférence) affiché dans la devise de la cagnotte.
    static func target(currency: String, links: [ItemLink]) -> Decimal? {
        links.first { $0.currency?.uppercased() == currency.uppercased() && ($0.price ?? 0) > 0 }?.price
    }

    /// Progression d'un cadeau en cagnotte ; `nil` s'il n'y a pas de cagnotte (ou pour un parent).
    static func make(item: WishItem, links: [ItemLink]) -> PotProgress? {
        guard let total = item.potTotal, let currency = item.potCurrency, (item.potCount ?? 0) > 0 else { return nil }
        return PotProgress(collected: total, currency: currency, target: target(currency: currency, links: links),
                           participants: item.potCount ?? 0)
    }
}

/// Saisie d'un montant de participation (« 25 », « 12,50 », « 12.5 »).
enum PotAmount {
    static let maximum: Decimal = 99_999_999

    static func parse(_ text: String) -> Decimal? {
        let cleaned = text.trimmed
            .replacingOccurrences(of: "\u{2019}", with: "")
            .replacingOccurrences(of: "'", with: "")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\u{00A0}", with: "")
            .replacingOccurrences(of: ",", with: ".")
        guard !cleaned.isEmpty, cleaned.filter({ $0 == "." }).count <= 1,
              cleaned.allSatisfy({ $0.isNumber || $0 == "." }),
              let value = Decimal(string: cleaned, locale: Locale(identifier: "en_US_POSIX")) else { return nil }
        var rounded = Decimal()
        var source = value
        NSDecimalRound(&rounded, &source, 2, .plain)
        guard rounded > 0, rounded <= maximum else { return nil }
        return rounded
    }

    /// Texte éditable d'un montant (« 12,5 » → « 12.50 » n'est pas imposé : on garde la forme courte).
    static func editableText(_ amount: Decimal) -> String {
        NSDecimalNumber(decimal: amount).stringValue.replacingOccurrences(of: ".", with: ",")
    }

    /// Montants proposés en un geste : des montants ronds, et le reste à réunir s'il est connu.
    static func suggestions(remaining: Decimal?) -> [Decimal] {
        var values: [Decimal] = [10, 20, 50]
        if let remaining, remaining > 0 {
            values = values.filter { $0 < remaining }
            if !values.contains(remaining) { values.append(remaining) }
        }
        return values
    }
}

// MARK: - Équilibre entre enfants

/// Compteurs anonymes d'un enfant (`children_reservation_counts`). Le serveur n'en renvoie
/// jamais pour mes propres enfants : l'absence de compteur ne s'affiche pas.
struct ReservationCounts: Decodable, Equatable, Hashable, Sendable {
    let childId: UUID
    let reservedCount: Int
    let purchasedCount: Int
    let potCount: Int

    enum CodingKeys: String, CodingKey {
        case childId = "child_id"
        case reservedCount = "reserved_count"
        case purchasedCount = "purchased_count"
        case potCount = "pot_count"
    }

    var total: Int { reservedCount + purchasedCount + potCount }

    /// « 2 réservés · 1 acheté · 1 cagnotte », ou une invitation douce quand rien n'est prévu.
    var summaryText: String {
        guard total > 0 else { return "Rien de prévu pour l'instant" }
        var parts: [String] = []
        if reservedCount > 0 { parts.append("\(reservedCount) réservé\(reservedCount > 1 ? "s" : "")") }
        if purchasedCount > 0 { parts.append("\(purchasedCount) acheté\(purchasedCount > 1 ? "s" : "")") }
        if potCount > 0 { parts.append("\(potCount) cagnotte\(potCount > 1 ? "s" : "")") }
        return parts.joined(separator: " · ")
    }

    /// Lecture VoiceOver, sans abréviation.
    var accessibilityText: String {
        guard total > 0 else { return "Aucun cadeau prévu pour l'instant" }
        var parts: [String] = []
        if reservedCount > 0 { parts.append("\(reservedCount) cadeau\(reservedCount > 1 ? "x" : "") réservé\(reservedCount > 1 ? "s" : "")") }
        if purchasedCount > 0 { parts.append("\(purchasedCount) acheté\(purchasedCount > 1 ? "s" : "")") }
        if potCount > 0 { parts.append("\(potCount) en cagnotte") }
        return parts.joined(separator: ", ")
    }
}

// MARK: - Remerciements

/// Remerciement reçu pour un cadeau que j'ai offert (`my_thanks`).
struct ThanksNote: Decodable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    let itemId: UUID
    let title: String
    let childName: String
    /// Parent qui a écrit le message (son identité n'est pas protégée).
    let senderName: String?
    let message: String
    let photoUrl: String?
    let createdAt: String
    /// Je me suis fait connaître des parents pour ce cadeau.
    var revealed: Bool

    enum CodingKeys: String, CodingKey {
        case id, title, message, revealed
        case itemId = "item_id"
        case childName = "child_name"
        case senderName = "sender_name"
        case photoUrl = "photo_url"
        case createdAt = "created_at"
    }

    var photoURL: URL? { photoUrl.flatMap(URL.init(string:)) }

    var createdDate: Date? { Timestamp.parse(createdAt) }
}

/// Donateur qui s'est fait connaître (`item_donors`, réservé aux parents).
struct ItemDonor: Decodable, Equatable, Hashable, Sendable {
    let displayName: String

    enum CodingKeys: String, CodingKey {
        case displayName = "display_name"
    }
}

/// Règles d'affichage du « Reçu ! » et des remerciements (#42).
enum ReceiptRules {
    /// L'événement a commencé : le jour même ou après.
    static func hasStarted(_ eventDate: DayDate?, today: Date = .now) -> Bool {
        guard let eventDate else { return false }
        return eventDate.localDate <= Calendar.current.startOfDay(for: today)
    }

    /// « Reçu ! » : un parent, sur un souhait pas encore possédé, une fois la fête arrivée.
    static func canMarkReceived(isParent: Bool, kind: WishKind, owned: Bool, eventDate: DayDate?, today: Date = .now) -> Bool {
        isParent && kind == .wish && !owned && hasStarted(eventDate, today: today)
    }

    /// Remercier : un parent, sur un souhait reçu, une fois la fête arrivée.
    static func canSendThanks(isParent: Bool, kind: WishKind, owned: Bool, eventDate: DayDate?, today: Date = .now) -> Bool {
        isParent && kind == .wish && owned && hasStarted(eventDate, today: today)
    }

    /// Côté donateur : le cadeau est noté possédé après la fête, il a donc été reçu
    /// (avant la fête, c'est une alerte « déjà possédé »).
    static func isReceived(owned: Bool, eventDate: DayDate?, today: Date = .now) -> Bool {
        owned && hasStarted(eventDate, today: today)
    }
}

/// Horodatages PostgREST (`timestamptz`), avec ou sans fractions de seconde.
enum Timestamp {
    static func parse(_ raw: String) -> Date? {
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractional.date(from: raw) { return date }
        let plain = ISO8601DateFormatter()
        plain.formatOptions = [.withInternetDateTime]
        if let date = plain.date(from: raw) { return date }
        // Microsecondes (« .123456 ») : on retire les fractions plutôt que d'échouer.
        let trimmed = raw.replacingOccurrences(of: #"\.\d+"#, with: "", options: .regularExpression)
        return plain.date(from: trimmed)
    }
}

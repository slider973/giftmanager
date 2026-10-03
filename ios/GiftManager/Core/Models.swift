import Foundation

// Modèles alignés sur le schéma Supabase (supabase/migrations). Les dates SQL `date`
// sont transportées en chaîne "yyyy-MM-dd" et exposées via `DayDate`.

struct Profile: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    var displayName: String
    var country: String
    var currency: String
    var onboarded: Bool
    /// Rappels d'anniversaire J-30 / J-7 (#43), envoyés par le serveur.
    var notifyBirthdayReminders: Bool

    enum CodingKeys: String, CodingKey {
        case id, country, currency, onboarded
        case displayName = "display_name"
        case notifyBirthdayReminders = "notify_birthday_reminders"
    }

    init(id: UUID, displayName: String, country: String, currency: String, onboarded: Bool,
         notifyBirthdayReminders: Bool = true) {
        self.id = id
        self.displayName = displayName
        self.country = country
        self.currency = currency
        self.onboarded = onboarded
        self.notifyBirthdayReminders = notifyBirthdayReminders
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        displayName = try c.decode(String.self, forKey: .displayName)
        country = try c.decode(String.self, forKey: .country)
        currency = try c.decode(String.self, forKey: .currency)
        onboarded = try c.decode(Bool.self, forKey: .onboarded)
        notifyBirthdayReminders = try c.decodeIfPresent(Bool.self, forKey: .notifyBirthdayReminders) ?? true
    }
}

struct FamilyGroup: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    var name: String
    let inviteCode: String
    let createdBy: UUID?

    enum CodingKeys: String, CodingKey {
        case id, name
        case inviteCode = "invite_code"
        case createdBy = "created_by"
    }
}

struct Household: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    var name: String
    var country: String
    /// Code permettant à un second parent de rejoindre le foyer.
    let inviteCode: String?

    enum CodingKeys: String, CodingKey {
        case id, name, country
        case inviteCode = "invite_code"
    }
}

/// Partage d'un foyer vers une famille (#60) : un même foyer peut être visible dans plusieurs groupes.
struct HouseholdGroup: Codable, Equatable, Hashable, Sendable {
    let householdId: UUID
    let groupId: UUID

    enum CodingKeys: String, CodingKey {
        case householdId = "household_id"
        case groupId = "group_id"
    }
}

struct HouseholdMember: Codable, Equatable, Hashable, Sendable {
    let householdId: UUID
    let userId: UUID

    enum CodingKeys: String, CodingKey {
        case householdId = "household_id"
        case userId = "user_id"
    }
}

struct GroupMember: Codable, Equatable, Hashable, Sendable {
    let groupId: UUID
    let userId: UUID

    enum CodingKeys: String, CodingKey {
        case groupId = "group_id"
        case userId = "user_id"
    }
}

/// Date sans heure, au format SQL `date`.
struct DayDate: Codable, Equatable, Hashable, Comparable, Sendable {
    let date: Date

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    init(_ date: Date) {
        let comps = Calendar.current.dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        self.date = utc.date(from: comps) ?? date
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        guard let date = Self.formatter.date(from: String(raw.prefix(10))) else {
            throw DecodingError.dataCorrupted(.init(codingPath: decoder.codingPath, debugDescription: "Date invalide : \(raw)"))
        }
        self.date = date
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.singleValueContainer()
        try c.encode(Self.formatter.string(from: date))
    }

    /// Date locale correspondante (minuit local), pour l'affichage et les calculs.
    var localDate: Date {
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC")!
        let comps = utc.dateComponents([.year, .month, .day], from: date)
        return Calendar.current.date(from: comps) ?? date
    }

    static func < (lhs: DayDate, rhs: DayDate) -> Bool { lhs.date < rhs.date }
}

struct Child: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    var householdId: UUID
    var firstName: String
    var birthdate: DayDate?
    var avatarEmoji: String?
    var avatarColor: String?
    var avatarUrl: String?
    /// Liste d'un adulte du foyer (#37) : mêmes règles de surprise que pour un enfant.
    var isAdult: Bool

    enum CodingKeys: String, CodingKey {
        case id
        case householdId = "household_id"
        case firstName = "first_name"
        case birthdate
        case avatarEmoji = "avatar_emoji"
        case avatarColor = "avatar_color"
        case avatarUrl = "avatar_url"
        case isAdult = "is_adult"
    }

    init(id: UUID, householdId: UUID, firstName: String, birthdate: DayDate?, avatarEmoji: String?,
         avatarColor: String?, avatarUrl: String?, isAdult: Bool = false) {
        self.id = id
        self.householdId = householdId
        self.firstName = firstName
        self.birthdate = birthdate
        self.avatarEmoji = avatarEmoji
        self.avatarColor = avatarColor
        self.avatarUrl = avatarUrl
        self.isAdult = isAdult
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        householdId = try c.decode(UUID.self, forKey: .householdId)
        firstName = try c.decode(String.self, forKey: .firstName)
        birthdate = try c.decodeIfPresent(DayDate.self, forKey: .birthdate)
        avatarEmoji = try c.decodeIfPresent(String.self, forKey: .avatarEmoji)
        avatarColor = try c.decodeIfPresent(String.self, forKey: .avatarColor)
        avatarUrl = try c.decodeIfPresent(String.self, forKey: .avatarUrl)
        isAdult = try c.decodeIfPresent(Bool.self, forKey: .isAdult) ?? false
    }

    var age: Int? { age(on: .now) }

    func age(on date: Date) -> Int? {
        guard let birth = birthdate?.localDate else { return nil }
        return Calendar.current.dateComponents([.year], from: birth, to: date).year
    }

    /// Encode les champs optionnels à `null` (et non absents) pour pouvoir les effacer via upsert.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(householdId, forKey: .householdId)
        try c.encode(firstName, forKey: .firstName)
        try c.encode(birthdate, forKey: .birthdate)
        try c.encode(avatarEmoji, forKey: .avatarEmoji)
        try c.encode(avatarColor, forKey: .avatarColor)
        try c.encode(avatarUrl, forKey: .avatarUrl)
        try c.encode(isAdult, forKey: .isAdult)
    }
}

enum GiftEventKind: String, Codable, CaseIterable, Sendable {
    case christmas, birthday, other
}

struct GiftEvent: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    /// `nil` pour Noël (global) et les anniversaires (portés par leur enfant) — voir #60.
    let groupId: UUID?
    var kind: GiftEventKind
    var title: String
    var eventDate: DayDate
    var childId: UUID?
    /// Lu seulement : défini par la base à la création.
    var createdBy: UUID?

    enum CodingKeys: String, CodingKey {
        case id, kind, title
        case groupId = "group_id"
        case eventDate = "event_date"
        case childId = "child_id"
        case createdBy = "created_by"
    }

    init(id: UUID, groupId: UUID?, kind: GiftEventKind, title: String, eventDate: DayDate, childId: UUID?, createdBy: UUID? = nil) {
        self.id = id
        self.groupId = groupId
        self.kind = kind
        self.title = title
        self.eventDate = eventDate
        self.childId = childId
        self.createdBy = createdBy
    }

    /// `child_id` encodé à `null` pour pouvoir l'effacer ; `created_by` jamais envoyé.
    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(groupId, forKey: .groupId)
        try c.encode(kind, forKey: .kind)
        try c.encode(title, forKey: .title)
        try c.encode(eventDate, forKey: .eventDate)
        try c.encode(childId, forKey: .childId)
    }

    var isPast: Bool { eventDate.localDate < Calendar.current.startOfDay(for: .now) }

    var daysRemaining: Int {
        Calendar.current.dateComponents([.day], from: Calendar.current.startOfDay(for: .now), to: eventDate.localDate).day ?? 0
    }
}

enum WishKind: String, Codable, Sendable {
    case wish, idea
}

/// Statut public d'un cadeau, tel que renvoyé par `child_items`. `nil` = mode surprise (parent).
enum ItemStatus: String, Codable, Sendable {
    case available, taken, mine, owned
    /// Cadeau financé à plusieurs (#35).
    case pot
}

enum ReservationState: String, Codable, Sendable {
    case reserved, purchased
}

/// Validation d'une idée par les parents (#57).
///
/// Le parent est le seul à savoir si un cadeau convient (âge, doublon, règles de la maison) :
/// l'écarter ne protégeait rien, puisque le secret porte sur la réservation, pas sur l'objet.
enum IdeaReview: String, Codable, Sendable {
    /// Idée non soumise : invisible des parents, comme avant.
    case none
    case pending
    case accepted
    case rejected
    case ownedAlready = "owned_already"

    var isSubmitted: Bool { self != .none }

    var label: String {
        switch self {
        case .none: "Surprise totale"
        case .pending: "En attente"
        case .accepted: "Acceptée"
        case .rejected: "Refusée"
        case .ownedAlready: "Déjà possédé"
        }
    }
}

/// Idée soumise par un membre, en attente du verdict d'un parent (#57).
struct PendingIdea: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    let childId: UUID
    let childName: String
    let title: String
    let notes: String?
    let imageUrl: String?
    let createdBy: UUID?
    let authorName: String?
    let createdAt: Date

    enum CodingKeys: String, CodingKey {
        case id, title, notes
        case childId = "child_id"
        case childName = "child_name"
        case imageUrl = "image_url"
        case createdBy = "created_by"
        case authorName = "author_name"
        case createdAt = "created_at"
    }

    var imageURL: URL? { imageUrl.flatMap(URL.init(string:)) }
    var author: String { authorName?.isEmpty == false ? authorName! : "Quelqu'un de la famille" }
}

struct WishItem: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    let childId: UUID
    var eventId: UUID?
    var kind: WishKind
    var title: String
    var notes: String?
    var imageUrl: String?
    var priority: Int
    var position: Int
    var owned: Bool
    var createdBy: UUID?
    var status: ItemStatus?
    var myReservation: ReservationState?
    /// Cagnotte (#35) : jamais renseignée pour les parents.
    var potTotal: Decimal?
    var potCurrency: String?
    var potCount: Int?
    /// Ma part dans la cagnotte, si je participe.
    var myContribution: Decimal?
    /// Validation parentale d'une idée (#57).
    var reviewStatus: IdeaReview = .none
    var reviewNote: String?

    enum CodingKeys: String, CodingKey {
        case id, kind, title, notes, priority, position, owned, status
        case childId = "child_id"
        case eventId = "event_id"
        case imageUrl = "image_url"
        case createdBy = "created_by"
        case myReservation = "my_reservation"
        case potTotal = "pot_total"
        case potCurrency = "pot_currency"
        case potCount = "pot_count"
        case myContribution = "my_contribution"
        case reviewStatus = "review_status"
        case reviewNote = "review_note"
    }

    var imageURL: URL? { imageUrl.flatMap(URL.init(string:)) }
    var isFavorite: Bool { priority > 0 }
    /// Je participe à la cagnotte de ce cadeau.
    var isInMyPot: Bool { (myContribution ?? 0) > 0 }
}

extension WishItem {
    /// Décodage tolérant : les champs de validation (#57) et de cagnotte (#35) sont absents
    /// des réponses plus anciennes et des charges utiles réduites (widget, extension de partage).
    /// Déclaré en extension pour conserver l'initialiseur par défaut généré par Swift.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        childId = try c.decode(UUID.self, forKey: .childId)
        eventId = try c.decodeIfPresent(UUID.self, forKey: .eventId)
        kind = try c.decode(WishKind.self, forKey: .kind)
        title = try c.decode(String.self, forKey: .title)
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        imageUrl = try c.decodeIfPresent(String.self, forKey: .imageUrl)
        priority = try c.decodeIfPresent(Int.self, forKey: .priority) ?? 0
        position = try c.decodeIfPresent(Int.self, forKey: .position) ?? 0
        owned = try c.decodeIfPresent(Bool.self, forKey: .owned) ?? false
        createdBy = try c.decodeIfPresent(UUID.self, forKey: .createdBy)
        status = try c.decodeIfPresent(ItemStatus.self, forKey: .status)
        myReservation = try c.decodeIfPresent(ReservationState.self, forKey: .myReservation)
        potTotal = try c.decodeIfPresent(Decimal.self, forKey: .potTotal)
        potCurrency = try c.decodeIfPresent(String.self, forKey: .potCurrency)
        potCount = try c.decodeIfPresent(Int.self, forKey: .potCount)
        myContribution = try c.decodeIfPresent(Decimal.self, forKey: .myContribution)
        reviewStatus = try c.decodeIfPresent(IdeaReview.self, forKey: .reviewStatus) ?? .none
        reviewNote = try c.decodeIfPresent(String.self, forKey: .reviewNote)
    }
}

struct ItemLink: Codable, Identifiable, Equatable, Hashable, Sendable {
    let id: UUID
    let itemId: UUID
    var url: String
    var store: String?
    var country: String?
    var price: Decimal?
    var currency: String?

    enum CodingKeys: String, CodingKey {
        case id, url, store, country, price, currency
        case itemId = "item_id"
    }

    var priceText: String? { Money.format(price, currency: currency) }
}

struct MyReservation: Codable, Identifiable, Equatable, Hashable, Sendable {
    let itemId: UUID
    var title: String
    var imageUrl: String?
    var status: ReservationState
    var owned: Bool
    var childId: UUID
    var childName: String
    var eventId: UUID?
    var eventTitle: String?
    var eventDate: DayDate?

    var id: UUID { itemId }

    enum CodingKeys: String, CodingKey {
        case title, status, owned
        case itemId = "item_id"
        case imageUrl = "image_url"
        case childId = "child_id"
        case childName = "child_name"
        case eventId = "event_id"
        case eventTitle = "event_title"
        case eventDate = "event_date"
    }
}

/// Lien saisi avant enregistrement (formulaire d'ajout de cadeau).
struct DraftLink: Identifiable, Equatable, Sendable {
    var id = UUID()
    var url: String
    var store: String?
    var country: String?
    var price: Decimal?
    var currency: String?
}

enum Money {
    static func format(_ amount: Decimal?, currency: String?) -> String? {
        guard let amount else { return nil }
        let code = currency ?? "EUR"
        let style = Decimal.FormatStyle.Currency(code: code, locale: Locale(identifier: code == "CHF" ? "fr_CH" : "fr_FR"))
        return amount.formatted(style)
    }
}

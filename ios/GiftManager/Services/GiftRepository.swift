import Foundation
import Supabase

/// Accès aux données Supabase. Toutes les règles d'accès et d'anonymat sont appliquées côté serveur (RLS + RPC).
struct GiftRepository: Sendable {
    var client: SupabaseClient = SupabaseService.client

    private var db: PostgrestClient { client.schema("public") }

    // MARK: - Profil

    func myProfile(userId: UUID) async throws -> Profile {
        try await db.from("profiles").select().eq("id", value: userId).single().execute().value
    }

    func updateProfile(_ profile: Profile) async throws {
        struct Patch: Encodable {
            let display_name: String
            let country: String
            let currency: String
            let onboarded: Bool
        }
        try await db.from("profiles")
            .update(Patch(display_name: profile.displayName, country: profile.country,
                          currency: profile.currency, onboarded: profile.onboarded))
            .eq("id", value: profile.id)
            .execute()
    }

    func profiles(ids: [UUID]) async throws -> [Profile] {
        guard !ids.isEmpty else { return [] }
        return try await db.from("profiles").select().in("id", values: ids).execute().value
    }

    func deleteAccount() async throws {
        try await db.rpc("delete_account").execute()
    }

    // MARK: - Groupe

    func myGroups() async throws -> [FamilyGroup] {
        try await db.from("groups").select().order("created_at").execute().value
    }

    func createGroup(name: String, householdName: String, country: String) async throws -> UUID {
        try await db.rpc("create_group", params: ["p_name": name, "p_household_name": householdName, "p_country": country])
            .execute().value
    }

    func joinGroup(code: String) async throws -> UUID {
        try await db.rpc("join_group", params: ["p_code": code]).execute().value
    }

    func createHousehold(groupId: UUID, name: String, country: String) async throws -> UUID {
        try await db.rpc("create_household", params: ["p_group": groupId.uuidString, "p_name": name, "p_country": country])
            .execute().value
    }

    func joinHousehold(code: String) async throws -> UUID {
        try await db.rpc("join_household", params: ["p_code": code]).execute().value
    }

    func renameGroup(_ groupId: UUID, name: String) async throws {
        try await db.from("groups").update(["name": name]).eq("id", value: groupId).execute()
    }

    func leaveGroup(_ groupId: UUID, userId: UUID) async throws {
        try await db.from("group_members").delete().eq("group_id", value: groupId).eq("user_id", value: userId).execute()
    }

    func households(groupId: UUID) async throws -> [Household] {
        try await db.from("households").select().eq("group_id", value: groupId).order("created_at").execute().value
    }

    func householdMembers(groupId: UUID) async throws -> [HouseholdMember] {
        try await db.from("household_members").select().eq("group_id", value: groupId).execute().value
    }

    func groupMembers(groupId: UUID) async throws -> [GroupMember] {
        try await db.from("group_members").select("group_id, user_id").eq("group_id", value: groupId).execute().value
    }

    // MARK: - Enfants

    func children(groupId: UUID) async throws -> [Child] {
        try await db.from("children")
            .select("id, household_id, first_name, birthdate, avatar_emoji, avatar_color, avatar_url, households!inner(group_id)")
            .eq("households.group_id", value: groupId)
            .order("first_name")
            .execute().value
    }

    func saveChild(_ child: Child) async throws {
        try await db.from("children").upsert(child).execute()
    }

    func deleteChild(_ childId: UUID) async throws {
        try await db.from("children").delete().eq("id", value: childId).execute()
    }

    // MARK: - Événements

    func events(groupId: UUID) async throws -> [GiftEvent] {
        try await db.from("events").select("id, group_id, kind, title, event_date, child_id, created_by")
            .eq("group_id", value: groupId).order("event_date").execute().value
    }

    func saveEvent(_ event: GiftEvent) async throws {
        try await db.from("events").upsert(event).execute()
    }

    func deleteEvent(_ eventId: UUID) async throws {
        try await db.from("events").delete().eq("id", value: eventId).execute()
    }

    // MARK: - Cadeaux

    func childItems(childId: UUID, eventId: UUID? = nil, kind: WishKind? = nil) async throws -> [WishItem] {
        struct Params: Encodable {
            let p_child: UUID
            let p_event: UUID?
            let p_kind: String?
        }
        return try await db.rpc("child_items", params: Params(p_child: childId, p_event: eventId, p_kind: kind?.rawValue))
            .execute().value
    }

    func links(itemIds: [UUID]) async throws -> [ItemLink] {
        guard !itemIds.isEmpty else { return [] }
        return try await db.from("item_links").select().in("item_id", values: itemIds).order("created_at").execute().value
    }

    struct NewItem: Encodable {
        let child_id: UUID
        let event_id: UUID?
        let kind: String
        let title: String
        let notes: String?
        let image_url: String?
        let priority: Int
        let owned: Bool
        let created_by: UUID
    }

    @discardableResult
    func addItem(_ item: NewItem, links: [DraftLink]) async throws -> UUID {
        struct Inserted: Decodable { let id: UUID }
        let inserted: Inserted = try await db.from("wish_items").insert(item).select("id").single().execute().value
        try await replaceLinks(itemId: inserted.id, links: links)
        return inserted.id
    }

    func updateItem(id: UUID, title: String, notes: String?, imageUrl: String?, priority: Int, eventId: UUID?) async throws {
        struct Patch: Encodable {
            let title: String
            let notes: String?
            let image_url: String?
            let priority: Int
            let event_id: UUID?
        }
        try await db.from("wish_items")
            .update(Patch(title: title, notes: notes, image_url: imageUrl, priority: priority, event_id: eventId))
            .eq("id", value: id).execute()
    }

    func setPriority(itemId: UUID, favorite: Bool) async throws {
        try await db.from("wish_items").update(["priority": favorite ? 1 : 0]).eq("id", value: itemId).execute()
    }

    func setOwned(itemId: UUID, owned: Bool) async throws {
        try await db.from("wish_items").update(["owned": owned]).eq("id", value: itemId).execute()
    }

    func reorder(itemIds: [UUID]) async throws {
        for (index, id) in itemIds.enumerated() {
            try await db.from("wish_items").update(["position": index]).eq("id", value: id).execute()
        }
    }

    func deleteItem(_ itemId: UUID) async throws {
        try await db.from("wish_items").delete().eq("id", value: itemId).execute()
    }

    func replaceLinks(itemId: UUID, links: [DraftLink]) async throws {
        try await db.from("item_links").delete().eq("item_id", value: itemId).execute()
        struct NewLink: Encodable {
            let item_id: UUID
            let url: String
            let store: String?
            let country: String?
            let price: Decimal?
            let currency: String?
        }
        let rows = links.filter { !$0.url.isEmpty }.map {
            NewLink(item_id: itemId, url: $0.url, store: $0.store, country: $0.country, price: $0.price, currency: $0.currency)
        }
        guard !rows.isEmpty else { return }
        try await db.from("item_links").insert(rows).execute()
    }

    // MARK: - Réservations

    func reserve(itemId: UUID) async throws {
        try await db.rpc("reserve_item", params: ["p_item": itemId.uuidString]).execute()
    }

    func cancelReservation(itemId: UUID) async throws {
        try await db.rpc("cancel_reservation", params: ["p_item": itemId.uuidString]).execute()
    }

    func setPurchased(itemId: UUID, purchased: Bool) async throws {
        struct Params: Encodable {
            let p_item: UUID
            let p_purchased: Bool
        }
        try await db.rpc("set_reservation_purchased", params: Params(p_item: itemId, p_purchased: purchased)).execute()
    }

    func myReservations() async throws -> [MyReservation] {
        try await db.rpc("my_reservations").execute().value
    }

    // MARK: - Notifications

    func registerDevice(token: String, environment: String) async throws {
        try await db.rpc("register_device", params: ["p_token": token, "p_environment": environment]).execute()
    }

    /// Prévient la famille des nouveaux cadeaux (les parents ne sont jamais notifiés des idées).
    func notifyNewItems(_ itemIds: [UUID]) async {
        struct Body: Encodable { let item_ids: [UUID] }
        try? await client.functions.invoke("notify-new-items", options: FunctionInvokeOptions(body: Body(item_ids: itemIds)))
    }

    // MARK: - Images

    /// Envoie une image JPEG dans le bucket `images` (dossier de l'utilisateur) et renvoie son URL publique.
    func uploadImage(_ data: Data, userId: UUID) async throws -> URL {
        let path = "\(userId.uuidString.lowercased())/\(UUID().uuidString.lowercased()).jpg"
        try await client.storage.from("images").upload(path, data: data, options: FileOptions(contentType: "image/jpeg"))
        return try client.storage.from("images").getPublicURL(path: path)
    }
}

/// Erreurs métier renvoyées par les fonctions SQL, traduites pour l'utilisateur.
enum GiftError: LocalizedError {
    case unavailable, owned, notFound, invalidInvite, alreadyInHousehold, other(String)

    init(_ error: Error) {
        let message = String(describing: error)
        if message.contains("ITEM_UNAVAILABLE") { self = .unavailable }
        else if message.contains("ITEM_OWNED") { self = .owned }
        else if message.contains("ITEM_NOT_FOUND") || message.contains("RESERVATION_NOT_FOUND") { self = .notFound }
        else if message.contains("INVALID_INVITE_CODE") { self = .invalidInvite }
        else if message.contains("ALREADY_IN_HOUSEHOLD") { self = .alreadyInHousehold }
        else { self = .other(error.localizedDescription) }
    }

    var errorDescription: String? {
        switch self {
        case .unavailable: "Ce cadeau n'est plus disponible."
        case .owned: "L'enfant possède déjà ce cadeau."
        case .notFound: "Ce cadeau n'existe plus."
        case .invalidInvite: "Ce code d'invitation n'est pas valide."
        case .alreadyInHousehold: "Tu fais déjà partie d'un foyer dans cette famille."
        case .other(let message): message
        }
    }
}

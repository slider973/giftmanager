import Foundation
import Observation

/// Logique d'enregistrement d'un cadeau, séparée de son formulaire.
///
/// `AddGiftView` enchaînait recherche de doublon, envoi d'image, création ou mise à jour et
/// notification dans une méthode de 35 lignes, directement dans la vue. Ces règles — en
/// particulier « une idée soumise aux parents part en `pending` » (#57) et « un cadeau déjà
/// possédé n'est rattaché à aucun événement » — n'étaient vérifiables d'aucune façon.
@Observable
@MainActor
final class AddGiftModel {
    /// Doublon trouvé avant enregistrement ; la vue demande confirmation (#61).
    var duplicate: WishItem?
    private(set) var isSaving = false
    /// Nombre de personnes qui verront l'idée ; `nil` tant que non chargé.
    private(set) var audience: Int?
    var lastError: Error?

    private let actions: any AddGiftActions
    private let child: Child
    private let kind: WishKind
    private let existing: WishItem?
    private let owned: Bool

    var isEditing: Bool { existing != nil }

    init(actions: any AddGiftActions, child: Child, kind: WishKind,
         existing: WishItem?, owned: Bool) {
        self.actions = actions
        self.child = child
        self.kind = kind
        self.existing = existing
        self.owned = owned
    }

    /// Champs du formulaire au moment de l'enregistrement.
    struct Draft {
        var title: String
        var notes: String
        var imageURL: URL?
        var imageData: Data?
        var isFavorite: Bool
        var eventId: UUID?
        var links: [DraftLink]
        /// #57 : soumettre l'idée aux parents plutôt que la garder invisible.
        var submitToParents: Bool
    }

    // MARK: - Enregistrement

    /// Enregistre, après recherche de doublon pour une création (#61).
    /// Renvoie `true` si la vue doit se fermer ; `false` si un doublon demande confirmation.
    func saveChecked(_ draft: Draft, userId: UUID) async -> Bool {
        if !isEditing, let found = await findDuplicate(draft) {
            duplicate = found
            return false
        }
        return await save(draft, userId: userId)
    }

    /// Enregistre sans vérifier les doublons (l'utilisateur a confirmé, ou c'est une édition).
    func save(_ draft: Draft, userId: UUID) async -> Bool {
        isSaving = true
        defer { isSaving = false }
        do {
            let note = draft.notes.trimmed.isEmpty ? nil : draft.notes.trimmed
            var image = draft.imageURL?.absoluteString
            if let data = draft.imageData {
                image = try await actions.uploadImage(data, userId: userId).absoluteString
            }

            if let existing {
                try await actions.updateItem(id: existing.id, title: draft.title.trimmed, notes: note,
                                             imageUrl: image, priority: draft.isFavorite ? 1 : 0,
                                             eventId: draft.eventId)
                try await actions.replaceLinks(itemId: existing.id, links: draft.links)
            } else {
                let item = GiftRepository.NewItem(
                    child_id: child.id,
                    // Un cadeau déjà possédé n'appartient à aucun événement : il ne doit pas
                    // réapparaître dans la liste de Noël ou d'un anniversaire.
                    event_id: owned ? nil : draft.eventId,
                    kind: kind.rawValue, title: draft.title.trimmed, notes: note,
                    image_url: image, priority: draft.isFavorite ? 1 : 0,
                    owned: owned, created_by: userId,
                    review_status: reviewStatus(submitToParents: draft.submitToParents).rawValue)
                let newId = try await actions.addItem(item, links: draft.links)
                // Rien à annoncer pour un cadeau déjà possédé.
                if !owned {
                    let actions = self.actions
                    Task.detached { await actions.notifyNewItems([newId]) }
                }
            }
            return true
        } catch {
            lastError = error
            return false
        }
    }

    /// #57 : seule une idée peut être soumise aux parents ; un souhait est déjà visible d'eux.
    func reviewStatus(submitToParents: Bool) -> IdeaReview {
        kind == .idea && submitToParents ? .pending : .none
    }

    // MARK: - Doublon

    /// Doublon = même enfant et titre identique (casse et espaces ignorés), ou même lien d'achat.
    func findDuplicate(_ draft: Draft) async -> WishItem? {
        let needle = DuplicateMatch.normalize(draft.title)
        guard !needle.isEmpty else { return nil }
        do {
            // `eventId` omis volontairement : on cherche dans toute la liste de l'enfant,
            // pas seulement l'événement affiché — c'est l'origine du doublon signalé (#61).
            let all = try await actions.childItems(childId: child.id, eventId: nil, kind: nil)
            if let byTitle = all.first(where: { DuplicateMatch.normalize($0.title) == needle }) {
                return byTitle
            }
            guard let url = draft.links.first?.url else { return nil }
            let links = try await actions.links(itemIds: all.map(\.id))
            guard let match = links.first(where: { DuplicateMatch.sameURL($0.url, url) }) else { return nil }
            return all.first { $0.id == match.itemId }
        } catch {
            // La détection est un confort : en cas d'échec réseau, on laisse enregistrer.
            return nil
        }
    }

    // MARK: - Audience

    /// Combien de personnes verront cette idée (#57) ; ignoré pour un souhait.
    func loadAudience() async {
        guard kind == .idea else { return }
        audience = try? await actions.ideaAudience(childId: child.id)
    }
}

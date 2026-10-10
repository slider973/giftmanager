import Foundation

/// Branche la session du Père Noël sur le dépôt réel.
///
/// Le modèle de session ne connaît que `SantaSessionActions` : il reste donc
/// testable sans réseau, et c'est ici — et seulement ici — que les suggestions
/// validées par le parent deviennent des idées de cadeau en base.
@MainActor
struct LiveSantaSessionActions: SantaSessionActions {
    let repository: GiftRepository
    let createdBy: UUID

    func createGiftIdea(childID: UUID, title: String, link: URL?) async throws {
        // `review_status` reste à sa valeur par défaut : une suggestion retenue
        // par le parent est déjà validée, elle n'a pas à repasser en revue.
        let item = GiftRepository.NewItem(
            child_id: childID,
            event_id: nil,
            kind: "wish",
            title: title,
            notes: "Recueilli avec le Père Noël",
            image_url: nil,
            priority: 0,
            owned: false,
            created_by: createdBy
        )

        var links: [DraftLink] = []
        if let link {
            let url = link.absoluteString
            let store = StoreCatalog.store(for: url)
            links.append(DraftLink(url: url,
                                   store: store?.name,
                                   country: store?.country,
                                   price: nil,
                                   currency: store?.currency))
        }

        _ = try await repository.addItem(item, links: links)
    }
}

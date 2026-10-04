import Foundation
import Observation

/// Logique de la fiche cadeau, séparée de son affichage.
///
/// Avant, `GiftDetailView` appelait le repository à 10 endroits et répétait partout le même
/// `do / catch / report`. Les règles d'état — ce que devient le cadeau après une réservation,
/// une annulation, un achat — étaient donc invérifiables sans réseau ni interface.
///
/// Ici elles sont exécutées sur un `GiftDetailActions` injecté : les tests passent un double
/// en mémoire, l'app passe le vrai `GiftRepository`.
@Observable
@MainActor
final class GiftDetailModel {
    private(set) var item: WishItem
    private(set) var isWorking = false
    private(set) var participants: [PotParticipant] = []
    private(set) var donors: [ItemDonor] = []
    /// Célébration à afficher, consommée par la vue puis remise à `nil`.
    var celebration: Celebration?
    /// Erreur à signaler à l'utilisateur ; la vue la transmet à `AppState.report`.
    var lastError: Error?

    private let actions: any GiftDetailActions
    private let isParent: Bool

    init(item: WishItem, actions: any GiftDetailActions, isParent: Bool) {
        self.item = item
        self.actions = actions
        self.isParent = isParent
    }

    /// Statut affiché quand le cadeau redevient libre : un parent ne voit jamais de statut.
    private var availableStatus: ItemStatus? { isParent ? nil : .available }

    // MARK: - Actions

    func reserve() async {
        await perform {
            try await self.actions.reserve(itemId: self.item.id)
            self.item.status = self.isParent ? nil : .mine
            self.item.myReservation = .reserved
            self.celebration = .reserved
            // #58 : prévient anonymement les autres foyers, pour éviter un second achat.
            // Détaché : l'utilisateur n'attend pas l'envoi, et un échec ne doit rien annuler.
            let actions = self.actions
            let itemId = self.item.id
            Task.detached { await actions.notifyReservation(itemId: itemId) }
        }
    }

    func cancelReservation() async {
        await perform {
            try await self.actions.cancelReservation(itemId: self.item.id)
            self.item.status = self.availableStatus
            self.item.myReservation = nil
        }
    }

    func setPurchased(_ purchased: Bool) async {
        await perform {
            try await self.actions.setPurchased(itemId: self.item.id, purchased: purchased)
            self.item.myReservation = purchased ? .purchased : .reserved
        }
    }

    func toggleFavorite() async {
        await perform {
            let next = !self.item.isFavorite
            try await self.actions.setPriority(itemId: self.item.id, favorite: next)
            self.item.priority = next ? 1 : 0
        }
    }

    func toggleOwned() async {
        await perform {
            let next = !self.item.owned
            try await self.actions.setOwned(itemId: self.item.id, owned: next)
            self.item.owned = next
        }
    }

    /// Marque le cadeau comme reçu (#42). Renvoie `true` si l'écran de remerciement doit s'ouvrir.
    func markReceived() async -> Bool {
        await perform {
            try await self.actions.setOwned(itemId: self.item.id, owned: true)
            self.item.owned = true
        }
        return item.owned
    }

    /// Renvoie `true` si la suppression a réussi et que la vue doit se fermer.
    func delete() async -> Bool {
        var deleted = false
        await perform {
            try await self.actions.deleteItem(self.item.id)
            deleted = true
        }
        return deleted
    }

    func leavePot() async {
        await perform {
            try await self.actions.leavePot(itemId: self.item.id)
        }
    }

    // MARK: - Chargements

    func loadParticipants() async {
        // Un parent ne doit jamais voir qui participe à la cagnotte de son enfant.
        guard item.isInMyPot, !isParent else {
            participants = []
            return
        }
        do {
            participants = try await actions.potParticipants(itemId: item.id)
        } catch {
            lastError = error
        }
    }

    func loadDonors() async {
        guard isParent, item.owned else { return }
        // Seuls les donateurs qui ont choisi de se faire connaître sont renvoyés.
        donors = (try? await actions.itemDonors(itemId: item.id)) ?? []
    }

    /// Remplace le cadeau après un rechargement externe (cagnotte, liste rafraîchie).
    func replace(with fresh: WishItem) {
        item = fresh
    }

    // MARK: - Exécution

    /// Enveloppe commune : indicateur d'activité, erreur remontée, et règle du cadeau pris.
    ///
    /// C'est ce bloc qui était recopié à l'identique dans chaque action de la vue.
    private func perform(_ action: () async throws -> Void) async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await action()
        } catch {
            lastError = error
            // Quelqu'un d'autre a réservé entre-temps : le cadeau est pris, pas disponible.
            if case .unavailable = GiftError(error) {
                item.status = isParent ? nil : .taken
            }
        }
    }
}

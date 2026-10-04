import Foundation
import Observation

/// Données et calculs de l'écran « Mes achats », séparés de son affichage.
///
/// Deux choses vivaient dans la vue : les appels distants, et surtout des calculs — le
/// regroupement par événement puis par enfant, et le total par devise. Ce dernier mélange
/// prix des liens et parts de cagnotte ; une erreur y fausserait le budget sans rien casser
/// visiblement, donc sans que personne s'en aperçoive.
@Observable
@MainActor
final class MyPurchasesModel {
    private(set) var reservations: [MyReservation] = []
    private(set) var contributions: [MyContribution] = []
    private(set) var links: [UUID: [ItemLink]] = [:]
    private(set) var thanksCount = 0
    private(set) var hasLoaded = false
    var lastError: Error?

    private let actions: any MyPurchasesActions
    /// Pays de l'utilisateur, pour choisir le lien le plus pertinent.
    private let preferredCountry: String?

    init(actions: any MyPurchasesActions, preferredCountry: String?) {
        self.actions = actions
        self.preferredCountry = preferredCountry
    }

    // MARK: - Regroupement

    /// Un événement, ses enfants, et les cadeaux réservés pour chacun.
    struct EventGroup: Identifiable {
        let id: String
        let title: String
        let date: DayDate?
        let children: [(name: String, items: [MyReservation])]
    }

    /// Réservations groupées par événement puis par enfant, les deux triés.
    /// Les événements les plus proches d'abord ; ceux sans date passent en dernier.
    var groups: [EventGroup] {
        Dictionary(grouping: reservations) { $0.eventId?.uuidString ?? "none" }
            .map { key, items in
                let byChild = Dictionary(grouping: items, by: \.childName)
                    .sorted { $0.key < $1.key }
                    .map { (name: $0.key, items: $0.value.sorted { $0.title < $1.title }) }
                return EventGroup(id: key, title: items.first?.eventTitle ?? "Sans événement",
                                  date: items.first?.eventDate, children: byChild)
            }
            .sorted { ($0.date?.date ?? .distantFuture) < ($1.date?.date ?? .distantFuture) }
    }

    /// Cadeaux réservés qu'il reste à acheter.
    var remainingCount: Int { reservations.filter { $0.status == .reserved }.count }

    /// Sans réservation, le résumé compte les cagnottes.
    var summaryCountLabel: String {
        if reservations.isEmpty { return contributions.count > 1 ? "cagnottes" : "cagnotte" }
        return remainingCount > 1 ? "cadeaux à acheter" : "cadeau à acheter"
    }

    /// Total par devise : prix du meilleur lien de chaque réservation, plus ma part de cagnotte.
    ///
    /// Les devises ne sont jamais converties — additionner des CHF et des EUR donnerait un
    /// montant faux. Un cadeau sans prix connu est simplement ignoré.
    var totals: [(currency: String, amount: Decimal)] {
        var sums: [String: Decimal] = [:]
        for reservation in reservations {
            guard let link = bestLink(reservation.itemId), let price = link.price else { continue }
            sums[link.currency ?? "EUR", default: 0] += price
        }
        // Cagnottes : seule ma part compte dans mon budget, pas le total du cadeau.
        for contribution in contributions {
            sums[contribution.currency, default: 0] += contribution.amount
        }
        return sums.sorted { $0.key < $1.key }.map { (currency: $0.key, amount: $0.value) }
    }

    /// Lien le plus pertinent pour ce cadeau (boutique du pays de l'utilisateur en priorité).
    func bestLink(_ itemId: UUID) -> ItemLink? {
        StoreCatalog.sorted(links[itemId] ?? [], preferredCountry: preferredCountry).first
    }

    // MARK: - Chargement et actions

    func load() async {
        do {
            async let pots = actions.myContributions()
            reservations = try await actions.myReservations()
            contributions = try await pots
            thanksCount = (try? await actions.myThanks().count) ?? thanksCount
            let fetched = try await actions.links(itemIds: reservations.map(\.itemId))
            links = Dictionary(grouping: fetched, by: \.itemId)
        } catch {
            lastError = error
        }
        hasLoaded = true
    }

    /// Bascule acheté / pas encore acheté, puis recharge.
    func togglePurchased(_ reservation: MyReservation) async {
        do {
            try await actions.setPurchased(itemId: reservation.itemId,
                                           purchased: reservation.status != .purchased)
            await load()
        } catch {
            lastError = error
        }
    }

    func cancel(_ reservation: MyReservation) async {
        do {
            try await actions.cancelReservation(itemId: reservation.itemId)
        } catch {
            lastError = error
        }
    }

    func leavePot(_ contribution: MyContribution) async {
        do {
            try await actions.leavePot(itemId: contribution.itemId)
        } catch {
            lastError = error
        }
    }
}

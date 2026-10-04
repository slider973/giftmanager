import Testing
import Foundation
@testable import GiftManager

/// Calculs de l'écran « Mes achats », vérifiés sans réseau ni interface.
///
/// Le total par devise est le point sensible : il mélange prix des liens et parts de
/// cagnotte, et une erreur y fausserait le budget sans rien casser visiblement.
@MainActor
struct MyPurchasesModelTests {

    final class FakeActions: MyPurchasesActions, @unchecked Sendable {
        var reservations: [MyReservation] = []
        var contributions: [MyContribution] = []
        var thanks: [ThanksNote] = []
        var itemLinks: [ItemLink] = []
        var errorToThrow: Error?
        var purchasedCalls: [(UUID, Bool)] = []
        var cancelled: [UUID] = []
        var leftPots: [UUID] = []

        func myReservations() async throws -> [MyReservation] {
            if let errorToThrow { throw errorToThrow }
            return reservations
        }

        func myContributions() async throws -> [MyContribution] {
            if let errorToThrow { throw errorToThrow }
            return contributions
        }

        func myThanks() async throws -> [ThanksNote] {
            if let errorToThrow { throw errorToThrow }
            return thanks
        }

        func links(itemIds: [UUID]) async throws -> [ItemLink] {
            if let errorToThrow { throw errorToThrow }
            return itemLinks
        }

        func setPurchased(itemId: UUID, purchased: Bool) async throws {
            if let errorToThrow { throw errorToThrow }
            purchasedCalls.append((itemId, purchased))
        }

        func cancelReservation(itemId: UUID) async throws {
            if let errorToThrow { throw errorToThrow }
            cancelled.append(itemId)
        }

        func leavePot(itemId: UUID) async throws {
            if let errorToThrow { throw errorToThrow }
            leftPots.append(itemId)
        }
    }

    private struct Boom: Error {}

    private func reservation(title: String, child: String, status: ReservationState = .reserved,
                             eventId: UUID? = nil, eventTitle: String? = nil,
                             eventDate: DayDate? = nil, itemId: UUID = UUID()) -> MyReservation {
        MyReservation(itemId: itemId, title: title, imageUrl: nil, status: status, owned: false,
                      childId: UUID(), childName: child, eventId: eventId,
                      eventTitle: eventTitle, eventDate: eventDate)
    }

    private func contribution(amount: Decimal, currency: String,
                              itemId: UUID = UUID()) -> MyContribution {
        MyContribution(itemId: itemId, title: "Vélo", imageUrl: nil, amount: amount,
                       currency: currency, owned: false, potTotal: nil, potCount: nil,
                       childId: UUID(), childName: "Epril")
    }

    private func link(_ itemId: UUID, price: Decimal?, currency: String?,
                      country: String? = nil) -> ItemLink {
        ItemLink(id: UUID(), itemId: itemId, url: "https://example.test/p",
                 store: "Boutique", country: country, price: price, currency: currency)
    }

    private func model(_ fake: FakeActions, country: String? = "CH") -> MyPurchasesModel {
        MyPurchasesModel(actions: fake, preferredCountry: country)
    }

    // MARK: - Totaux

    @Test func leTotalAdditionneLesPrixDeLaMemeDevise() async {
        let fake = FakeActions()
        let a = UUID(), b = UUID()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", itemId: a),
                             reservation(title: "Livre", child: "Elliot", itemId: b)]
        fake.itemLinks = [link(a, price: 120, currency: "CHF"),
                          link(b, price: 30, currency: "CHF")]
        let m = model(fake)

        await m.load()

        #expect(m.totals.count == 1)
        #expect(m.totals.first?.amount == 150)
        #expect(m.totals.first?.currency == "CHF")
    }

    @Test func lesDevisesNeSontJamaisMelangees() async {
        // Additionner CHF et EUR donnerait un montant faux : deux lignes distinctes.
        let fake = FakeActions()
        let a = UUID(), b = UUID()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", itemId: a),
                             reservation(title: "Livre", child: "Elliot", itemId: b)]
        fake.itemLinks = [link(a, price: 100, currency: "CHF"),
                          link(b, price: 40, currency: "EUR")]
        let m = model(fake)

        await m.load()

        #expect(m.totals.count == 2)
        #expect(m.totals.first(where: { $0.currency == "CHF" })?.amount == 100)
        #expect(m.totals.first(where: { $0.currency == "EUR" })?.amount == 40)
    }

    @Test func seuleMaPartDeCagnotteCompteDansLeTotal() async {
        // Le cadeau vaut peut-être 300, mais mon budget ne supporte que ma part.
        let fake = FakeActions()
        fake.contributions = [contribution(amount: 50, currency: "CHF")]
        let m = model(fake)

        await m.load()

        #expect(m.totals.first?.amount == 50)
    }

    @Test func unCadeauSansPrixEstIgnoreSansFausserLeTotal() async {
        let fake = FakeActions()
        let a = UUID(), b = UUID()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", itemId: a),
                             reservation(title: "Surprise", child: "Elliot", itemId: b)]
        fake.itemLinks = [link(a, price: 60, currency: "CHF"),
                          link(b, price: nil, currency: "CHF")]
        let m = model(fake)

        await m.load()

        #expect(m.totals.first?.amount == 60)
    }

    @Test func reservationsEtCagnottesSAdditionnentDansLaMemeDevise() async {
        let fake = FakeActions()
        let a = UUID()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", itemId: a)]
        fake.itemLinks = [link(a, price: 100, currency: "CHF")]
        fake.contributions = [contribution(amount: 25, currency: "CHF")]
        let m = model(fake)

        await m.load()

        #expect(m.totals.count == 1)
        #expect(m.totals.first?.amount == 125)
    }

    // MARK: - Regroupement

    @Test func lesEvenementsLesPlusProchesDAbord() async {
        let fake = FakeActions()
        let noel = UUID(), anniv = UUID()
        fake.reservations = [
            reservation(title: "Vélo", child: "Epril", eventId: noel, eventTitle: "Noël",
                        eventDate: DayDate(Date(timeIntervalSince1970: 2_000_000_000))),
            reservation(title: "Livre", child: "Elliot", eventId: anniv, eventTitle: "Anniversaire",
                        eventDate: DayDate(Date(timeIntervalSince1970: 1_900_000_000))),
        ]
        let m = model(fake)

        await m.load()

        #expect(m.groups.first?.title == "Anniversaire")
        #expect(m.groups.last?.title == "Noël")
    }

    @Test func lesCadeauxSansEvenementPassentEnDernier() async {
        let fake = FakeActions()
        let noel = UUID()
        fake.reservations = [
            reservation(title: "Surprise", child: "Molly"),
            reservation(title: "Vélo", child: "Epril", eventId: noel, eventTitle: "Noël",
                        eventDate: DayDate(Date(timeIntervalSince1970: 1_900_000_000))),
        ]
        let m = model(fake)

        await m.load()

        #expect(m.groups.first?.title == "Noël")
        #expect(m.groups.last?.title == "Sans événement")
    }

    @Test func lesEnfantsSontTriesEtLeursCadeauxAussi() async {
        let fake = FakeActions()
        let noel = UUID()
        fake.reservations = [
            reservation(title: "Vélo", child: "Molly", eventId: noel, eventTitle: "Noël"),
            reservation(title: "Ballon", child: "Elliot", eventId: noel, eventTitle: "Noël"),
            reservation(title: "Album", child: "Elliot", eventId: noel, eventTitle: "Noël"),
        ]
        let m = model(fake)

        await m.load()

        let children = m.groups.first?.children ?? []
        #expect(children.map(\.name) == ["Elliot", "Molly"])
        #expect(children.first?.items.map(\.title) == ["Album", "Ballon"])
    }

    // MARK: - Résumé

    @Test func leResumeNeCompteQueCeQuiResteAAcheter() async {
        let fake = FakeActions()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", status: .reserved),
                             reservation(title: "Livre", child: "Elliot", status: .purchased)]
        let m = model(fake)

        await m.load()

        #expect(m.remainingCount == 1)
        #expect(m.summaryCountLabel == "cadeau à acheter")
    }

    @Test func sansReservationLeResumeParleDeCagnottes() async {
        let fake = FakeActions()
        fake.contributions = [contribution(amount: 20, currency: "CHF"),
                              contribution(amount: 30, currency: "CHF")]
        let m = model(fake)

        await m.load()

        #expect(m.summaryCountLabel == "cagnottes")
    }

    // MARK: - Actions

    @Test func basculerVersAchete() async {
        let fake = FakeActions()
        let item = UUID()
        let res = reservation(title: "Vélo", child: "Epril", status: .reserved, itemId: item)
        let m = model(fake)

        await m.togglePurchased(res)

        #expect(fake.purchasedCalls.first?.0 == item)
        #expect(fake.purchasedCalls.first?.1 == true)
    }

    @Test func basculerDepuisAcheteRevientEnReserve() async {
        let fake = FakeActions()
        let res = reservation(title: "Vélo", child: "Epril", status: .purchased)
        let m = model(fake)

        await m.togglePurchased(res)

        #expect(fake.purchasedCalls.first?.1 == false)
    }

    @Test func uneErreurEstRemonteeSansVider() async {
        // L'écran doit garder ce qu'il affichait plutôt que se vider d'un coup.
        let fake = FakeActions()
        let a = UUID()
        fake.reservations = [reservation(title: "Vélo", child: "Epril", itemId: a)]
        let m = model(fake)
        await m.load()

        fake.errorToThrow = Boom()
        await m.load()

        #expect(m.lastError != nil)
        #expect(m.reservations.count == 1)
    }

    @Test func leChargementEstMarqueMemeApresUneErreur() async {
        // Sinon l'écran reste bloqué sur son indicateur de chargement.
        let fake = FakeActions()
        fake.errorToThrow = Boom()
        let m = model(fake)

        await m.load()

        #expect(m.hasLoaded)
    }
}

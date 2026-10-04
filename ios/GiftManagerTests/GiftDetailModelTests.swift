import Testing
import Foundation
@testable import GiftManager

/// Règles de la fiche cadeau, vérifiées sans réseau ni interface.
///
/// Ces comportements vivaient dans `GiftDetailView` et n'étaient donc testables d'aucune façon.
/// Le point le plus sensible est le secret : un parent ne doit jamais apprendre, par un statut
/// ou une liste de participants, que son enfant reçoit tel cadeau.
@MainActor
struct GiftDetailModelTests {

    // MARK: - Double de test

    /// Enregistre les appels et rejoue des réponses décidées par le test.
    final class FakeActions: GiftDetailActions, @unchecked Sendable {
        var calls: [String] = []
        var errorToThrow: Error?
        var participantsToReturn: [PotParticipant] = []
        var donorsToReturn: [ItemDonor] = []
        var notifiedReservations: [UUID] = []

        private func maybeThrow(_ name: String) throws {
            calls.append(name)
            if let errorToThrow { throw errorToThrow }
        }

        func setPriority(itemId: UUID, favorite: Bool) async throws { try maybeThrow("setPriority(\(favorite))") }
        func setOwned(itemId: UUID, owned: Bool) async throws { try maybeThrow("setOwned(\(owned))") }
        func deleteItem(_ itemId: UUID) async throws { try maybeThrow("deleteItem") }
        func reserve(itemId: UUID) async throws { try maybeThrow("reserve") }
        func cancelReservation(itemId: UUID) async throws { try maybeThrow("cancelReservation") }
        func setPurchased(itemId: UUID, purchased: Bool) async throws { try maybeThrow("setPurchased(\(purchased))") }
        func leavePot(itemId: UUID) async throws { try maybeThrow("leavePot") }

        func potParticipants(itemId: UUID) async throws -> [PotParticipant] {
            try maybeThrow("potParticipants")
            return participantsToReturn
        }

        func itemDonors(itemId: UUID) async throws -> [ItemDonor] {
            try maybeThrow("itemDonors")
            return donorsToReturn
        }

        func notifyReservation(itemId: UUID) async {
            calls.append("notifyReservation")
            notifiedReservations.append(itemId)
        }
    }

    /// Erreur « déjà réservé par quelqu'un d'autre », telle que la renvoie le serveur.
    private struct Unavailable: Error, CustomStringConvertible {
        let description = "ITEM_UNAVAILABLE"
    }

    private func item(owned: Bool = false, priority: Int = 0,
                      myContribution: Decimal? = nil,
                      reservation: ReservationState? = nil) -> WishItem {
        WishItem(id: UUID(), childId: UUID(), eventId: nil, kind: .wish, title: "Vélo",
                 notes: nil, imageUrl: nil, priority: priority, position: 0, owned: owned,
                 createdBy: nil, status: .available, myReservation: reservation,
                 potTotal: nil, potCurrency: nil, potCount: nil, myContribution: myContribution)
    }

    private func model(_ item: WishItem, _ actions: FakeActions, isParent: Bool = false) -> GiftDetailModel {
        GiftDetailModel(item: item, actions: actions, isParent: isParent)
    }

    // MARK: - Réservation

    @Test func reserverMarqueLeCadeauCommeMienEtPrevientLesAutres() async {
        let fake = FakeActions()
        let m = model(item(), fake)

        await m.reserve()

        #expect(m.item.myReservation == .reserved)
        #expect(m.item.status == .mine)
        #expect(m.celebration == .reserved)
        #expect(fake.calls.contains("reserve"))
    }

    @Test func annulerRendLeCadeauDisponible() async {
        let fake = FakeActions()
        let m = model(item(reservation: .reserved), fake)

        await m.cancelReservation()

        #expect(m.item.myReservation == nil)
        #expect(m.item.status == .available)
    }

    @Test func unParentNeVoitAucunStatutApresAnnulation() async {
        // Le secret : afficher « disponible » à un parent lui apprendrait qu'un cadeau
        // vient d'être libéré, donc qu'il avait été réservé.
        let fake = FakeActions()
        let m = model(item(reservation: .reserved), fake, isParent: true)

        await m.cancelReservation()

        #expect(m.item.status == nil)
    }

    @Test func marquerAchete() async {
        let fake = FakeActions()
        let m = model(item(reservation: .reserved), fake)

        await m.setPurchased(true)

        #expect(m.item.myReservation == .purchased)
        #expect(fake.calls.contains("setPurchased(true)"))
    }

    // MARK: - Conflit : quelqu'un a réservé avant moi

    @Test func cadeauDejaPrisParUnAutreDevientPris() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(), fake)

        await m.reserve()

        #expect(m.item.status == .taken)
        #expect(m.item.myReservation == nil, "la réservation ne doit pas être appliquée en cas d'échec")
        #expect(m.lastError != nil)
    }

    @Test func leParentNeVoitPasLeStatutPrisNonPlus() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(), fake, isParent: true)

        await m.reserve()

        #expect(m.item.status == nil)
    }

    // MARK: - Favori et possession

    @Test func basculerLeFavori() async {
        let fake = FakeActions()
        let m = model(item(priority: 0), fake, isParent: true)

        await m.toggleFavorite()
        #expect(m.item.isFavorite)

        await m.toggleFavorite()
        #expect(m.item.isFavorite == false)
    }

    @Test func leFavoriNeChangePasSiLAppelEchoue() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(priority: 0), fake, isParent: true)

        await m.toggleFavorite()

        #expect(m.item.isFavorite == false)
        #expect(m.lastError != nil)
    }

    @Test func marquerRecuOuvreLesRemerciements() async {
        let fake = FakeActions()
        let m = model(item(), fake, isParent: true)

        let shouldThank = await m.markReceived()

        #expect(shouldThank)
        #expect(m.item.owned)
    }

    @Test func marquerRecuNOuvreRienSiLAppelEchoue() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(), fake, isParent: true)

        let shouldThank = await m.markReceived()

        #expect(shouldThank == false)
        #expect(m.item.owned == false)
    }

    // MARK: - Suppression

    @Test func supprimerFermeLaFiche() async {
        let fake = FakeActions()
        let m = model(item(), fake, isParent: true)

        #expect(await m.delete())
    }

    @Test func supprimerNeFermePasSiLAppelEchoue() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(), fake, isParent: true)

        #expect(await m.delete() == false)
    }

    // MARK: - Cagnotte : le parent ne doit rien savoir

    @Test func unParentNeChargeJamaisLesParticipants() async {
        let fake = FakeActions()
        fake.participantsToReturn = [PotParticipant(displayName: "Kenny", amount: 20, currency: "EUR", isMe: false)]
        let m = model(item(myContribution: 10), fake, isParent: true)

        await m.loadParticipants()

        #expect(m.participants.isEmpty)
        #expect(fake.calls.contains("potParticipants") == false, "aucun appel ne doit partir pour un parent")
    }

    @Test func lesParticipantsSeChargentSiJeContribue() async {
        let fake = FakeActions()
        fake.participantsToReturn = [PotParticipant(displayName: "Kenny", amount: 20, currency: "EUR", isMe: false)]
        let m = model(item(myContribution: 10), fake)

        await m.loadParticipants()

        #expect(m.participants.count == 1)
    }

    @Test func aucunParticipantSiJeNeContribuePas() async {
        let fake = FakeActions()
        let m = model(item(), fake)

        await m.loadParticipants()

        #expect(m.participants.isEmpty)
        #expect(fake.calls.contains("potParticipants") == false)
    }

    // MARK: - Donateurs

    @Test func lesDonateursNeSeChargentQuePourUnParentEtUnCadeauRecu() async {
        let fake = FakeActions()
        fake.donorsToReturn = [ItemDonor(displayName: "Arthur")]

        let pasParent = model(item(owned: true), fake)
        await pasParent.loadDonors()
        #expect(pasParent.donors.isEmpty)

        let pasRecu = model(item(owned: false), fake, isParent: true)
        await pasRecu.loadDonors()
        #expect(pasRecu.donors.isEmpty)

        let ok = model(item(owned: true), fake, isParent: true)
        await ok.loadDonors()
        #expect(ok.donors.count == 1)
    }

    // MARK: - Indicateur d'activité

    @Test func lIndicateurRetombeMemeApresUneErreur() async {
        let fake = FakeActions()
        fake.errorToThrow = Unavailable()
        let m = model(item(), fake)

        await m.reserve()

        #expect(m.isWorking == false)
    }
}

import Testing
import Foundation
@testable import GiftManager

/// Règles d'enregistrement d'un cadeau, vérifiées sans réseau ni formulaire.
///
/// Trois d'entre elles sont invisibles à l'écran et n'étaient couvertes par rien :
/// une idée soumise aux parents part en `pending` (#57), un cadeau déjà possédé n'est
/// rattaché à aucun événement, et la recherche de doublon balaie toute la liste de
/// l'enfant et non le seul événement affiché (#61).
@MainActor
struct AddGiftModelTests {

    final class FakeActions: AddGiftActions, @unchecked Sendable {
        var items: [WishItem] = []
        var itemLinks: [ItemLink] = []
        var audienceToReturn = 0
        var errorToThrow: Error?
        /// Dernier cadeau transmis au serveur : c'est lui qu'on inspecte.
        var addedItem: GiftRepository.NewItem?
        var addedLinks: [DraftLink] = []
        var updatedEventId: UUID??
        var notified: [UUID] = []
        var uploadedImages = 0
        /// Paramètres reçus par `childItems`, pour vérifier la portée de la recherche.
        var childItemsEventId: UUID??

        func childItems(childId: UUID, eventId: UUID?, kind: WishKind?) async throws -> [WishItem] {
            childItemsEventId = .some(eventId)
            if let errorToThrow { throw errorToThrow }
            return items
        }

        func links(itemIds: [UUID]) async throws -> [ItemLink] {
            if let errorToThrow { throw errorToThrow }
            return itemLinks
        }

        func ideaAudience(childId: UUID) async throws -> Int {
            if let errorToThrow { throw errorToThrow }
            return audienceToReturn
        }

        func uploadImage(_ data: Data, userId: UUID) async throws -> URL {
            uploadedImages += 1
            if let errorToThrow { throw errorToThrow }
            return URL(string: "https://example.test/image.jpg")!
        }

        func addItem(_ item: GiftRepository.NewItem, links: [DraftLink]) async throws -> UUID {
            if let errorToThrow { throw errorToThrow }
            addedItem = item
            addedLinks = links
            return UUID()
        }

        func updateItem(id: UUID, title: String, notes: String?, imageUrl: String?,
                        priority: Int, eventId: UUID?) async throws {
            if let errorToThrow { throw errorToThrow }
            updatedEventId = .some(eventId)
        }

        func replaceLinks(itemId: UUID, links: [DraftLink]) async throws {
            if let errorToThrow { throw errorToThrow }
            addedLinks = links
        }

        func notifyNewItems(_ itemIds: [UUID]) async { notified.append(contentsOf: itemIds) }
    }

    private struct Boom: Error {}

    private let childId = UUID()
    private let userId = UUID()

    private var child: Child {
        Child(id: childId, householdId: UUID(), firstName: "Epril", birthdate: nil,
              avatarEmoji: nil, avatarColor: nil, avatarUrl: nil, isAdult: false)
    }

    private func draft(title: String = "Vélo rouge", links: [DraftLink] = [],
                       eventId: UUID? = nil, submitToParents: Bool = true,
                       imageData: Data? = nil) -> AddGiftModel.Draft {
        AddGiftModel.Draft(title: title, notes: "", imageURL: nil, imageData: imageData,
                           isFavorite: false, eventId: eventId, links: links,
                           submitToParents: submitToParents)
    }

    private func item(title: String) -> WishItem {
        WishItem(id: UUID(), childId: childId, eventId: nil, kind: .wish, title: title,
                 notes: nil, imageUrl: nil, priority: 0, position: 0, owned: false,
                 createdBy: nil, status: nil, myReservation: nil,
                 potTotal: nil, potCurrency: nil, potCount: nil, myContribution: nil)
    }

    private func model(_ fake: FakeActions, kind: WishKind = .wish,
                       existing: WishItem? = nil, owned: Bool = false) -> AddGiftModel {
        AddGiftModel(actions: fake, child: child, kind: kind, existing: existing, owned: owned)
    }

    // MARK: - Validation parentale des idées (#57)

    @Test func uneIdeeSoumiseAuxParentsPartEnAttente() async {
        let fake = FakeActions()
        let m = model(fake, kind: .idea)

        #expect(await m.save(draft(submitToParents: true), userId: userId))
        #expect(fake.addedItem?.review_status == IdeaReview.pending.rawValue)
    }

    @Test func uneIdeeNonSoumiseResteInvisible() async {
        let fake = FakeActions()
        let m = model(fake, kind: .idea)

        #expect(await m.save(draft(submitToParents: false), userId: userId))
        #expect(fake.addedItem?.review_status == IdeaReview.none.rawValue)
    }

    @Test func unSouhaitNEstJamaisSoumisAValidation() async {
        // Un souhait est déjà visible des parents : le soumettre n'aurait pas de sens.
        let fake = FakeActions()
        let m = model(fake, kind: .wish)

        #expect(await m.save(draft(submitToParents: true), userId: userId))
        #expect(fake.addedItem?.review_status == IdeaReview.none.rawValue)
    }

    // MARK: - Cadeau déjà possédé

    @Test func unCadeauDejaPossedeNEstRattacheAAucunEvenement() async {
        let fake = FakeActions()
        let m = model(fake, owned: true)
        let event = UUID()

        #expect(await m.save(draft(eventId: event), userId: userId))
        #expect(fake.addedItem?.event_id == nil, "sinon il réapparaît dans la liste de Noël")
        #expect(fake.addedItem?.owned == true)
    }

    @Test func unCadeauDejaPossedeNeDeclencheAucuneNotification() async {
        let fake = FakeActions()
        let m = model(fake, owned: true)

        _ = await m.save(draft(), userId: userId)

        #expect(fake.notified.isEmpty)
    }

    @Test func unCadeauNormalConserveSonEvenement() async {
        let fake = FakeActions()
        let m = model(fake)
        let event = UUID()

        #expect(await m.save(draft(eventId: event), userId: userId))
        #expect(fake.addedItem?.event_id == event)
    }

    // MARK: - Doublons (#61)

    @Test func laRechercheDeDoublonBalaieToutLaListe() async {
        // Le bug d'origine : la recherche était limitée à l'événement affiché, donc
        // un même cadeau pouvait exister dans Noël et dans l'anniversaire.
        let fake = FakeActions()
        let m = model(fake)

        _ = await m.findDuplicate(draft(eventId: UUID()))

        #expect(fake.childItemsEventId == .some(nil), "aucun filtre d'événement ne doit être transmis")
    }

    @Test func doublonDetecteParLeTitreQuelleQueSoitLaCasse() async {
        let fake = FakeActions()
        fake.items = [item(title: "  VÉLO Rouge ")]
        let m = model(fake)

        #expect(await m.findDuplicate(draft(title: "vélo rouge")) != nil)
    }

    @Test func pasDeDoublonSurUnTitreDifferent() async {
        let fake = FakeActions()
        fake.items = [item(title: "Trottinette")]
        let m = model(fake)

        #expect(await m.findDuplicate(draft(title: "Vélo rouge")) == nil)
    }

    @Test func enregistrerSArreteSiUnDoublonExiste() async {
        let fake = FakeActions()
        fake.items = [item(title: "Vélo rouge")]
        let m = model(fake)

        let saved = await m.saveChecked(draft(title: "Vélo rouge"), userId: userId)

        #expect(saved == false)
        #expect(m.duplicate != nil)
        #expect(fake.addedItem == nil, "rien ne doit être envoyé tant que l'utilisateur n'a pas tranché")
    }

    @Test func uneModificationNeCherchePasDeDoublon() async {
        // Sinon modifier un cadeau le détecterait comme son propre doublon.
        let fake = FakeActions()
        let existing = item(title: "Vélo rouge")
        fake.items = [existing]
        let m = model(fake, existing: existing)

        #expect(await m.saveChecked(draft(title: "Vélo rouge"), userId: userId))
        #expect(m.duplicate == nil)
    }

    @Test func unEchecReseauNEmpechePasDEnregistrer() async {
        // La détection est un confort : elle ne doit jamais bloquer l'utilisateur.
        let fake = FakeActions()
        fake.errorToThrow = Boom()
        let m = model(fake)

        #expect(await m.findDuplicate(draft()) == nil)
    }

    @Test func titreVideNeDeclenchePasDeRecherche() async {
        let fake = FakeActions()
        let m = model(fake)

        #expect(await m.findDuplicate(draft(title: "   ")) == nil)
        #expect(fake.childItemsEventId == nil, "aucun appel ne doit partir")
    }

    // MARK: - Image et erreurs

    @Test func lImageNEstEnvoyeeQueSiElleExiste() async {
        let fake = FakeActions()
        let m = model(fake)

        _ = await m.save(draft(), userId: userId)
        #expect(fake.uploadedImages == 0)

        _ = await m.save(draft(imageData: Data([1, 2, 3])), userId: userId)
        #expect(fake.uploadedImages == 1)
    }

    @Test func unEchecDEnregistrementRemonteLErreurEtNeFermeRien() async {
        let fake = FakeActions()
        fake.errorToThrow = Boom()
        let m = model(fake)

        #expect(await m.save(draft(), userId: userId) == false)
        #expect(m.lastError != nil)
    }

    @Test func lIndicateurRetombeApresUneErreur() async {
        let fake = FakeActions()
        fake.errorToThrow = Boom()
        let m = model(fake)

        _ = await m.save(draft(), userId: userId)

        #expect(m.isSaving == false)
    }

    // MARK: - Audience d'une idée

    @Test func lAudienceNEstChargeeQuePourUneIdee() async {
        let fake = FakeActions()
        fake.audienceToReturn = 3

        let souhait = model(fake, kind: .wish)
        await souhait.loadAudience()
        #expect(souhait.audience == nil)

        let idee = model(fake, kind: .idea)
        await idee.loadAudience()
        #expect(idee.audience == 3)
    }
}

import Foundation
import Testing

@testable import GiftManager

/// Double de test : enregistre ce qui aurait été créé, sans réseau.
@MainActor
final class SantaActionsSpy: SantaSessionActions {
    struct Created: Equatable {
        let childID: UUID
        let title: String
        let link: URL?
    }

    var created: [Created] = []
    var erreurAJeter: Error?

    func createGiftIdea(childID: UUID, title: String, link: URL?) async throws {
        if let e = erreurAJeter { throw e }
        created.append(Created(childID: childID, title: title, link: link))
    }
}

private struct ErreurReseau: Error {}

@MainActor
@Suite("Session du Père Noël")
struct SantaSessionModelTests {

    private func script() -> SantaScript {
        let json = """
        {
          "voix": { "nom": "Sylvestre", "voice_id": "2vBkYF6XwnA9E7Osnt2m",
                    "model_id": "eleven_multilingual_v2" },
          "repliques": [
            { "id": "accueil", "texte": "Ho ho ho !", "attend_reponse": false },
            { "id": "demande_prenom", "texte": "Ton prénom ?",
              "attend_reponse": true, "champ": "prenom" },
            { "id": "question_envie", "texte": "Ton envie ?",
              "attend_reponse": true, "champ": "envie_principale" },
            { "id": "cloture", "texte": "Au revoir", "attend_reponse": false }
          ]
        }
        """
        return try! JSONDecoder().decode(SantaScript.self, from: Data(json.utf8))
    }

    private func modele(pays: String? = "CH",
                        modeEnfant: Bool = false,
                        spy: SantaActionsSpy? = nil) -> SantaSessionModel {
        // `SantaActionsSpy()` ne peut pas servir de valeur par défaut : son
        // initialiseur est isolé sur l'acteur principal, ce qu'une signature
        // de fonction n'est pas.
        SantaSessionModel(script: script(), actions: spy ?? SantaActionsSpy(), childID: UUID(),
                          householdCountry: pays, childModeActive: modeEnfant)
    }

    // MARK: - Le garde-fou qui compte

    @Test("en mode enfant, la session refuse de démarrer")
    func modeEnfantBloque() {
        // L'exigence est que l'enfant ne puisse jamais lancer le Père Noël seul.
        // La règle est testée ici, pas supposée depuis l'absence de bouton.
        let m = modele(modeEnfant: true)
        m.start()

        guard case .blocked(let raison) = m.stage else {
            Issue.record("la session a démarré en mode enfant : \(m.stage)")
            return
        }
        #expect(raison.contains("parent"))
    }

    @Test("depuis l'espace parent, la session démarre")
    func parentPeutDemarrer() {
        let m = modele(modeEnfant: false)
        m.start()
        #expect(m.stage == .speaking(lineIndex: 0))
        #expect(m.currentLine?.id == "accueil")
    }

    // MARK: - Déroulé

    @Test("le déroulé enchaîne paroles et réponses jusqu'aux suggestions")
    func derouleComplet() {
        let m = modele()
        m.start()
        #expect(m.currentLine?.id == "accueil")

        // Une réplique sans question enchaîne directement.
        m.advanceAfterSpeaking()
        #expect(m.stage == .speaking(lineIndex: 1))

        // Une réplique avec question attend la saisie du parent.
        m.advanceAfterSpeaking()
        #expect(m.stage == .awaitingAnswer(lineIndex: 1))

        m.submitAnswer("Lina")
        #expect(m.wishes.prenom == "Lina")
        #expect(m.stage == .speaking(lineIndex: 2))

        m.advanceAfterSpeaking()
        m.submitAnswer("une trottinette")
        #expect(m.wishes.enviePrincipale == "une trottinette")

        // Dernière réplique, puis bascule sur les suggestions.
        #expect(m.stage == .speaking(lineIndex: 3))
        m.advanceAfterSpeaking()
        #expect(m.stage == .reviewingSuggestions)
    }

    @Test("les espaces autour d'une réponse sont nettoyés")
    func reponseNettoyee() {
        let m = modele()
        m.start()
        m.advanceAfterSpeaking()
        m.advanceAfterSpeaking()
        m.submitAnswer("  Lina  ")
        #expect(m.wishes.prenom == "Lina")
    }

    @Test("une question facultative peut être passée")
    func questionPassee() {
        let m = modele()
        m.start()
        m.advanceAfterSpeaking()
        m.advanceAfterSpeaking()
        #expect(m.stage == .awaitingAnswer(lineIndex: 1))
        m.skipAnswer()
        #expect(m.wishes.prenom.isEmpty)
        #expect(m.stage == .speaking(lineIndex: 2))
    }

    @Test("les activités se cumulent et se retirent")
    func activitesMultiples() {
        let m = modele()
        m.toggleActivity("construire")
        m.toggleActivity("dehors")
        #expect(m.wishes.activites == ["construire", "dehors"])
        m.toggleActivity("dehors")
        #expect(m.wishes.activites == ["construire"])
    }

    // MARK: - Suggestions filtrées par pays du foyer

    @Test("un foyer suisse ne reçoit que des suggestions suisses")
    func suggestionsSuisses() {
        let m = modele(pays: "CH")
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        #expect(!m.suggestions.isEmpty)
        #expect(m.suggestions.allSatisfy { $0.store.country == "CH" })
        #expect(m.suggestions.allSatisfy { $0.url.absoluteString.contains(".ch")
                                            || $0.store.domain.hasSuffix(".ch") })
    }

    @Test("un foyer français ne reçoit que des suggestions françaises")
    func suggestionsFrancaises() {
        // Le foyer du frère : même code, autre pays, autres boutiques.
        let m = modele(pays: "FR")
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Arthur")
        m.advanceAfterSpeaking()
        m.submitAnswer("Lego pompiers")
        m.advanceAfterSpeaking()

        #expect(!m.suggestions.isEmpty)
        #expect(m.suggestions.allSatisfy { $0.store.country == "FR" })
        let domaines = Set(m.suggestions.map(\.store.domain))
        #expect(!domaines.contains("galaxus.ch"), "une boutique suisse a fuité dans un foyer FR")
    }

    @Test("sans pays de foyer, aucune suggestion n'est inventée")
    func sansPays() {
        let m = modele(pays: nil)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        guard case .blocked(let raison) = m.stage else {
            Issue.record("des suggestions ont été produites sans pays : \(m.stage)")
            return
        }
        #expect(raison.contains("pays"))
        #expect(m.suggestions.isEmpty)
    }

    @Test("sans envie exprimée, pas de suggestion")
    func sansEnvie() {
        let m = modele()
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.skipAnswer()            // aucune envie saisie
        m.advanceAfterSpeaking()

        guard case .blocked = m.stage else {
            Issue.record("suggestions produites sans envie : \(m.stage)")
            return
        }
    }

    // MARK: - Validation parentale

    @Test("rien n'est enregistré avant validation du parent")
    func rienSansValidation() async {
        let spy = SantaActionsSpy()
        let m = modele(spy: spy)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        // Les suggestions existent, mais aucune idée n'a été créée.
        #expect(!m.suggestions.isEmpty)
        #expect(spy.created.isEmpty, "une idée a été créée sans validation parentale")
    }

    @Test("une suggestion validée devient une idée de cadeau")
    func validationCreeIdee() async throws {
        let spy = SantaActionsSpy()
        let m = modele(spy: spy)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        let premiere = try #require(m.suggestions.first)
        let avant = m.suggestions.count
        await m.approve(premiere)

        #expect(spy.created.count == 1)
        #expect(spy.created.first?.title == "trottinette")
        #expect(spy.created.first?.link == premiere.url)
        #expect(m.savedCount == 1)
        #expect(m.suggestions.count == avant - 1, "la suggestion validée doit disparaître")
    }

    @Test("une suggestion écartée ne crée rien")
    func rejetNeCreeRien() async throws {
        let spy = SantaActionsSpy()
        let m = modele(spy: spy)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        let premiere = try #require(m.suggestions.first)
        m.reject(premiere)

        #expect(spy.created.isEmpty)
        #expect(m.savedCount == 0)
        #expect(!m.suggestions.contains(premiere))
    }

    @Test("un échec réseau est rapporté sans perdre la suggestion")
    func echecReseau() async throws {
        let spy = SantaActionsSpy()
        spy.erreurAJeter = ErreurReseau()
        let m = modele(spy: spy)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        let premiere = try #require(m.suggestions.first)
        await m.approve(premiere)

        #expect(m.lastError != nil)
        #expect(m.savedCount == 0)
        // La suggestion doit rester : le parent doit pouvoir réessayer.
        #expect(m.suggestions.contains(premiere))
    }

    @Test("clôturer la session abandonne les suggestions non validées")
    func clotureAbandonne() async {
        let spy = SantaActionsSpy()
        let m = modele(spy: spy)
        m.start()
        m.advanceAfterSpeaking(); m.advanceAfterSpeaking()
        m.submitAnswer("Lina")
        m.advanceAfterSpeaking()
        m.submitAnswer("trottinette")
        m.advanceAfterSpeaking()

        m.finish()
        #expect(m.stage == .done)
        #expect(m.suggestions.isEmpty)
        #expect(spy.created.isEmpty)
    }
}

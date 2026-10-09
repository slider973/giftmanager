import Foundation
import Observation

/// Ce dont la session du Père Noël a besoin du dépôt distant.
///
/// Protocole étroit, pour que le modèle soit testable sans réseau : la session
/// ne crée des idées de cadeau qu'après validation explicite du parent.
@MainActor
protocol SantaSessionActions {
    /// Crée une idée de cadeau pour un enfant, à partir d'une suggestion validée.
    func createGiftIdea(childID: UUID, title: String, link: URL?) async throws
}

/// Déroulé d'une session du Père Noël, séparé de son affichage.
///
/// Trois invariants portés ici plutôt que dans la vue, parce qu'ils ne doivent
/// pas dépendre de la façon dont l'écran est dessiné :
///
/// 1. **Seul un parent déclenche la session.** Il n'existe aucun point d'entrée
///    enfant, et `childModeActive` la bloque : si l'appareil est en mode enfant,
///    la session refuse de démarrer.
/// 2. **Les boutiques viennent du pays du foyer** (`households.country`), jamais
///    de la locale de l'appareil — un parent en voyage garde ses boutiques.
/// 3. **Rien n'est enregistré sans validation parentale.** Les suggestions
///    restent en mémoire jusqu'à ce que le parent en retienne une.
@Observable
@MainActor
final class SantaSessionModel {
    /// Étape courante du déroulé.
    enum Stage: Equatable {
        case notStarted
        case speaking(lineIndex: Int)
        case awaitingAnswer(lineIndex: Int)
        case reviewingSuggestions
        case done
        /// La session ne peut pas démarrer (mode enfant, pays absent…).
        case blocked(reason: String)
    }

    /// Une piste de cadeau à soumettre au parent.
    struct Suggestion: Identifiable, Equatable {
        let id = UUID()
        let query: String
        let store: StoreCatalog.SearchableStore
        let url: URL

        var storeName: String { store.name }
    }

    private(set) var stage: Stage = .notStarted
    private(set) var wishes = SantaWishes()
    private(set) var suggestions: [Suggestion] = []
    private(set) var savedCount = 0
    var lastError: Error?

    let script: SantaScript
    private let actions: any SantaSessionActions
    private let childID: UUID
    /// Pays du foyer — source unique des boutiques proposées.
    private let householdCountry: String?
    private let childModeActive: Bool

    init(script: SantaScript,
         actions: any SantaSessionActions,
         childID: UUID,
         householdCountry: String?,
         childModeActive: Bool) {
        self.script = script
        self.actions = actions
        self.childID = childID
        self.householdCountry = householdCountry
        self.childModeActive = childModeActive
    }

    // MARK: - Déroulé

    var currentLine: SantaScript.Line? {
        switch stage {
        case .speaking(let i), .awaitingAnswer(let i):
            return script.repliques.indices.contains(i) ? script.repliques[i] : nil
        default:
            return nil
        }
    }

    /// Démarre la session. Refuse si l'appareil est en mode enfant.
    func start() {
        guard !childModeActive else {
            // Garde-fou central : un enfant ne doit pas pouvoir lancer le Père
            // Noël seul. L'absence de bouton dans l'interface enfant ne suffit
            // pas — la règle vit ici, où elle est testable.
            stage = .blocked(reason: "La session du Père Noël se lance depuis l'espace parent.")
            return
        }
        guard !script.repliques.isEmpty else {
            stage = .blocked(reason: "Le script du Père Noël est vide.")
            return
        }
        advance(to: 0)
    }

    /// Passe à la réplique suivante, ou à la revue des suggestions à la fin.
    func advanceAfterSpeaking() {
        guard case .speaking(let i) = stage else { return }
        let line = script.repliques[i]
        if line.attendReponse {
            stage = .awaitingAnswer(lineIndex: i)
        } else {
            advance(to: i + 1)
        }
    }

    /// Enregistre la réponse saisie par le parent et avance.
    func submitAnswer(_ value: String) {
        guard case .awaitingAnswer(let i) = stage else { return }
        let line = script.repliques[i]
        if let champ = line.champ {
            apply(value: value, to: champ)
        }
        advance(to: i + 1)
    }

    /// Bascule un choix d'activité (plusieurs réponses possibles).
    func toggleActivity(_ key: String) {
        if wishes.activites.contains(key) {
            wishes.activites.remove(key)
        } else {
            wishes.activites.insert(key)
        }
    }

    /// Passe une question facultative sans rien enregistrer.
    func skipAnswer() {
        guard case .awaitingAnswer(let i) = stage else { return }
        advance(to: i + 1)
    }

    private func advance(to index: Int) {
        guard script.repliques.indices.contains(index) else {
            buildSuggestions()
            return
        }
        stage = .speaking(lineIndex: index)
    }

    private func apply(value: String, to champ: String) {
        let propre = value.trimmingCharacters(in: .whitespacesAndNewlines)
        switch champ {
        case "prenom": wishes.prenom = propre
        case "envie_principale": wishes.enviePrincipale = propre
        case "preferences": wishes.preferences = propre
        case "envie_secondaire": wishes.envieSecondaire = propre
        case "activite": break  // géré par toggleActivity
        default: break
        }
    }

    // MARK: - Suggestions

    /// Construit les pistes de cadeaux dans les boutiques du pays du foyer.
    func buildSuggestions() {
        let stores = StoreCatalog.searchableStores(for: householdCountry)
        guard !stores.isEmpty else {
            stage = .blocked(reason: "Aucune boutique n'est configurée pour le pays du foyer.")
            return
        }
        guard wishes.estExploitable else {
            stage = .blocked(reason: "L'enfant n'a exprimé aucune envie.")
            return
        }

        // Les requêtes vont de la plus précise à la plus large ; on croise avec
        // les trois premières boutiques du pays pour rester lisible à l'écran.
        var sorties: [Suggestion] = []
        for query in wishes.requetes {
            for store in stores.prefix(3) {
                if let url = StoreCatalog.searchURL(for: query, in: store) {
                    sorties.append(Suggestion(query: query, store: store, url: url))
                }
            }
        }
        suggestions = sorties
        stage = .reviewingSuggestions
    }

    /// Le parent retient une suggestion : elle devient une idée de cadeau.
    func approve(_ suggestion: Suggestion) async {
        let titre = suggestion.query
        do {
            try await actions.createGiftIdea(childID: childID, title: titre, link: suggestion.url)
            savedCount += 1
            suggestions.removeAll { $0.id == suggestion.id }
            if suggestions.isEmpty { stage = .done }
        } catch {
            lastError = error
        }
    }

    /// Le parent écarte une suggestion sans rien enregistrer.
    func reject(_ suggestion: Suggestion) {
        suggestions.removeAll { $0.id == suggestion.id }
        if suggestions.isEmpty { stage = .done }
    }

    /// Clôt la session en laissant tomber ce qui n'a pas été validé.
    func finish() {
        suggestions = []
        stage = .done
    }
}

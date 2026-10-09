import Foundation

/// Script du Père Noël : répliques et questions, chargés depuis le bundle.
///
/// Le script est **fermé et versionné** : aucune génération de texte à
/// l'exécution. On sait donc exactement ce que l'enfant va entendre, ce qui
/// est la condition pour faire parler une voix à un mineur sans ouvrir la
/// porte à une sortie imprévisible.
struct SantaScript: Decodable, Equatable {
    struct Voice: Decodable, Equatable {
        let nom: String
        let voiceID: String
        let modelID: String

        private enum CodingKeys: String, CodingKey {
            case nom
            case voiceID = "voice_id"
            case modelID = "model_id"
        }
    }

    struct Choice: Decodable, Equatable, Identifiable {
        let cle: String
        let libelle: String

        var id: String { cle }
    }

    struct Line: Decodable, Equatable, Identifiable {
        let id: String
        let texte: String
        let attendReponse: Bool
        let champ: String?
        let choix: [Choice]?
        let aideParent: String?
        let facultatif: Bool

        /// Nom du fichier audio pré-généré correspondant.
        var audioResource: String { id }

        private enum CodingKeys: String, CodingKey {
            case id, texte, champ, choix
            case attendReponse = "attend_reponse"
            case aideParent = "aide_parent"
            case facultatif
        }

        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            id = try c.decode(String.self, forKey: .id)
            texte = try c.decode(String.self, forKey: .texte)
            attendReponse = try c.decodeIfPresent(Bool.self, forKey: .attendReponse) ?? false
            champ = try c.decodeIfPresent(String.self, forKey: .champ)
            choix = try c.decodeIfPresent([Choice].self, forKey: .choix)
            aideParent = try c.decodeIfPresent(String.self, forKey: .aideParent)
            // Ajouter un champ non optionnel casserait le décodage des scripts
            // déjà livrés : toute nouveauté passe par decodeIfPresent.
            facultatif = try c.decodeIfPresent(Bool.self, forKey: .facultatif) ?? false
        }
    }

    let voix: Voice
    let repliques: [Line]

    private enum CodingKeys: String, CodingKey {
        case voix, repliques
    }

    /// Charge le script livré dans le bundle.
    static func load(bundle: Bundle = .main) throws -> SantaScript {
        guard let url = bundle.url(forResource: "santa_script_fr", withExtension: "json") else {
            throw SantaScriptError.scriptIntrouvable
        }
        let data = try Data(contentsOf: url)
        return try JSONDecoder().decode(SantaScript.self, from: data)
    }
}

enum SantaScriptError: LocalizedError {
    case scriptIntrouvable

    var errorDescription: String? {
        switch self {
        case .scriptIntrouvable:
            return "Le script du Père Noël est introuvable dans l'application."
        }
    }
}

/// Ce que l'enfant a exprimé pendant la session, tel que le parent l'a saisi.
struct SantaWishes: Equatable {
    var prenom: String = ""
    var activites: Set<String> = []
    var enviePrincipale: String = ""
    var preferences: String = ""
    var envieSecondaire: String = ""

    /// Une session n'a d'intérêt que si l'enfant a exprimé au moins une envie.
    var estExploitable: Bool {
        !enviePrincipale.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Requêtes à lancer dans les boutiques, de la plus précise à la plus large.
    ///
    /// Les préférences (couleur, personnage) affinent la requête principale :
    /// « trottinette » devient « trottinette rouge », bien plus utile.
    var requetes: [String] {
        var sorties: [String] = []
        let principale = enviePrincipale.trimmingCharacters(in: .whitespacesAndNewlines)
        let prefs = preferences.trimmingCharacters(in: .whitespacesAndNewlines)
        let secondaire = envieSecondaire.trimmingCharacters(in: .whitespacesAndNewlines)

        if !principale.isEmpty {
            if !prefs.isEmpty { sorties.append("\(principale) \(prefs)") }
            sorties.append(principale)
        }
        if !secondaire.isEmpty { sorties.append(secondaire) }
        return sorties
    }
}

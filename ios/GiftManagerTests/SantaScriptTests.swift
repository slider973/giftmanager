import Foundation
import Testing

@testable import GiftManager

/// Le script est la source de vérité de ce que l'enfant entendra : s'il ne se
/// décode pas, la session ne démarre pas. Ces tests verrouillent le contrat
/// entre le JSON livré et le code qui le lit.
@Suite("Script du Père Noël")
struct SantaScriptTests {

    /// JSON minimal reproduisant la forme du fichier livré.
    private func script(_ json: String) throws -> SantaScript {
        try JSONDecoder().decode(SantaScript.self, from: Data(json.utf8))
    }

    @Test("le script livré se décode et respecte sa forme")
    func decodeScriptLivre() throws {
        let json = """
        {
          "voix": { "nom": "Sylvestre", "voice_id": "2vBkYF6XwnA9E7Osnt2m",
                    "model_id": "eleven_multilingual_v2" },
          "repliques": [
            { "id": "accueil", "texte": "Ho ho ho !", "attend_reponse": false },
            { "id": "demande_prenom", "texte": "Comment t'appelles-tu ?",
              "attend_reponse": true, "champ": "prenom",
              "aide_parent": "Saisis le prénom." },
            { "id": "question_activite", "texte": "Que préfères-tu ?",
              "attend_reponse": true, "champ": "activite",
              "choix": [ { "cle": "construire", "libelle": "Construire" } ] }
          ]
        }
        """
        let s = try script(json)
        #expect(s.voix.nom == "Sylvestre")
        #expect(s.voix.modelID == "eleven_multilingual_v2")
        #expect(s.repliques.count == 3)
        #expect(s.repliques[0].attendReponse == false)
        #expect(s.repliques[1].champ == "prenom")
        #expect(s.repliques[2].choix?.first?.cle == "construire")
    }

    @Test("un champ facultatif absent ne casse pas le décodage")
    func champFacultatifAbsent() throws {
        // Régression : ajouter un champ non optionnel au modèle casserait
        // tous les scripts déjà livrés sur les appareils.
        let json = """
        {
          "voix": { "nom": "V", "voice_id": "x", "model_id": "m" },
          "repliques": [ { "id": "a", "texte": "Bonjour" } ]
        }
        """
        let s = try script(json)
        #expect(s.repliques[0].facultatif == false)
        #expect(s.repliques[0].attendReponse == false)
        #expect(s.repliques[0].choix == nil)
    }

    @Test("chaque réplique nomme son fichier audio")
    func nomDeRessourceAudio() throws {
        let json = """
        {
          "voix": { "nom": "V", "voice_id": "x", "model_id": "m" },
          "repliques": [ { "id": "cloture", "texte": "Au revoir" } ]
        }
        """
        let s = try script(json)
        #expect(s.repliques[0].audioResource == "cloture")
    }

    @Test("le fichier réellement livré dans le dépôt est valide")
    func fichierLivreValide() throws {
        // Lit le JSON du dépôt, pas une copie : si quelqu'un le casse en
        // éditant un texte, ce test échoue avant la livraison.
        // #filePath = <dépôt>/ios/GiftManagerTests/SantaScriptTests.swift,
        // donc deux remontées mènent à <dépôt>/ios.
        let ici = URL(fileURLWithPath: #filePath)
        let iosDir = ici.deletingLastPathComponent().deletingLastPathComponent()
        let url = iosDir
            .appendingPathComponent("GiftManager/Resources/SantaScript/santa_script_fr.json")

        let data = try Data(contentsOf: url)
        let s = try JSONDecoder().decode(SantaScript.self, from: data)

        #expect(s.repliques.count >= 5)
        #expect(s.voix.voiceID == "2vBkYF6XwnA9E7Osnt2m", "la voix retenue est Sylvestre")

        // Les identifiants servent de noms de fichiers audio : ils doivent être uniques.
        let ids = s.repliques.map(\.id)
        #expect(Set(ids).count == ids.count, "identifiants de répliques dupliqués")

        // Toute question qui attend une réponse doit nommer son champ,
        // sinon la réponse du parent n'a nulle part où aller.
        for r in s.repliques where r.attendReponse {
            #expect(r.champ != nil, "la réplique \(r.id) attend une réponse sans champ")
        }

        // Le texte doit porter des accents : sans eux, le modèle multilingue
        // bascule sur une prononciation anglaise (« Pernoel »).
        let accents = CharacterSet(charactersIn: "éèêëàâäîïôöùûüç")
        for r in s.repliques where r.texte.count > 40 {
            #expect(r.texte.rangeOfCharacter(from: accents) != nil,
                    "la réplique \(r.id) n'a aucun accent, prononciation anglaise probable")
        }
    }
}

@Suite("Envies recueillies")
struct SantaWishesTests {

    @Test("une session sans envie principale n'est pas exploitable")
    func sessionVide() {
        var w = SantaWishes()
        #expect(w.estExploitable == false)
        w.prenom = "Lina"
        w.activites = ["construire"]
        #expect(w.estExploitable == false, "un prénom seul ne suffit pas")
        w.enviePrincipale = "une trottinette"
        #expect(w.estExploitable)
    }

    @Test("les espaces seuls ne valent pas une envie")
    func envieEnEspaces() {
        var w = SantaWishes()
        w.enviePrincipale = "   \n  "
        #expect(w.estExploitable == false)
    }

    @Test("les préférences affinent la requête principale")
    func requetesAffinees() {
        var w = SantaWishes()
        w.enviePrincipale = "trottinette"
        w.preferences = "rouge"
        w.envieSecondaire = "jeu de société"

        // La plus précise d'abord : « trottinette rouge » vaut mieux que « trottinette ».
        #expect(w.requetes == ["trottinette rouge", "trottinette", "jeu de société"])
    }

    @Test("sans préférence, la requête reste l'envie brute")
    func requetesSansPreference() {
        var w = SantaWishes()
        w.enviePrincipale = "Lego pompiers"
        #expect(w.requetes == ["Lego pompiers"])
    }
}

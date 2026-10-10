import Foundation
import Testing

@testable import GiftManager

/// Le pays vient du foyer (`households.country`), jamais de la locale de
/// l'appareil : un parent suisse en voyage en France doit continuer à voir
/// des boutiques suisses. Ces tests verrouillent cette règle.
@Suite("Recherche de jouets par pays du foyer")
struct StoreCatalogSearchTests {

    @Test("un foyer suisse ne reçoit que des boutiques suisses")
    func boutiquesSuisses() {
        let stores = StoreCatalog.searchableStores(for: "CH")
        #expect(!stores.isEmpty)
        #expect(stores.allSatisfy { $0.country == "CH" })

        let domaines = stores.map(\.domain)
        #expect(domaines.contains("galaxus.ch"))
        #expect(domaines.contains("fnac.ch"))
        #expect(domaines.contains("franz-carl-weber.ch"))

        // Aucune boutique française ne doit fuiter dans un foyer suisse.
        #expect(!domaines.contains("amazon.fr"))
        #expect(!domaines.contains("king-jouet.com"))
    }

    @Test("un foyer français ne reçoit que des boutiques françaises")
    func boutiquesFrancaises() {
        let stores = StoreCatalog.searchableStores(for: "FR")
        #expect(!stores.isEmpty)
        #expect(stores.allSatisfy { $0.country == "FR" })

        let domaines = stores.map(\.domain)
        #expect(domaines.contains("amazon.fr"))
        #expect(domaines.contains("fnac.com"))
        #expect(domaines.contains("king-jouet.com"))

        // Et réciproquement : pas de .ch chez un foyer français.
        #expect(!domaines.contains("galaxus.ch"))
        #expect(!domaines.contains("fnac.ch"))
    }

    @Test("les deux foyers de la famille ne partagent aucune boutique de pays")
    func suisseEtFranceNeSeMelangentPas() {
        // Cas réel : un foyer en Suisse, celui du frère en France.
        let ch = Set(StoreCatalog.searchableStores(for: "CH").map(\.domain))
        let fr = Set(StoreCatalog.searchableStores(for: "FR").map(\.domain))

        // fnac.com (FR) et fnac.ch (CH) sont deux domaines distincts :
        // l'intersection doit être vide.
        #expect(ch.intersection(fr).isEmpty,
                "des boutiques sont partagées entre les deux pays : \(ch.intersection(fr))")
    }

    @Test("les enseignes de jouets viennent en premier")
    func jouetsEnTete() {
        // Un enfant demande un jouet : la première boutique proposée doit être
        // un magasin de jouets, pas un généraliste.
        #expect(StoreCatalog.searchableStores(for: "CH").first?.domain == "franz-carl-weber.ch")
        #expect(StoreCatalog.searchableStores(for: "FR").first?.domain == "king-jouet.com")
    }

    @Test("un pays sans liste dédiée retombe sur son Amazon")
    func repliSurAmazon() {
        let it = StoreCatalog.searchableStores(for: "IT")
        #expect(it.count == 1)
        #expect(it.first?.domain == "amazon.it")
        #expect(it.first?.country == "IT")
    }

    @Test("un pays inconnu ne propose rien plutôt que n'importe quoi")
    func paysInconnu() {
        #expect(StoreCatalog.searchableStores(for: "ZZ").isEmpty)
        #expect(StoreCatalog.searchableStores(for: nil).isEmpty)
        #expect(StoreCatalog.searchableStores(for: "").isEmpty)
    }

    @Test("la requête est échappée dans l'URL")
    func urlEchappee() throws {
        let store = try #require(StoreCatalog.searchableStores(for: "FR")
            .first { $0.domain == "amazon.fr" })

        let url = try #require(StoreCatalog.searchURL(for: "trottinette rouge", in: store))
        let texte = url.absoluteString

        #expect(texte.hasPrefix("https://www.amazon.fr/s?k="))
        #expect(!texte.contains(" "), "l'espace doit être échappé")
        #expect(texte.contains("trottinette%20rouge"))
    }

    @Test("les accents et apostrophes d'un enfant ne cassent pas l'URL")
    func urlAvecAccents() throws {
        let store = try #require(StoreCatalog.searchableStores(for: "CH").first)
        // Ce qu'un enfant dit vraiment : « une épée de chevalier »
        let url = try #require(StoreCatalog.searchURL(for: "une épée de chevalier", in: store))
        #expect(URL(string: url.absoluteString) != nil, "URL invalide après échappement")
        #expect(!url.absoluteString.contains("é"), "les accents doivent être encodés")
    }

    @Test("une requête vide n'ouvre pas de page de recherche")
    func requeteVide() throws {
        let store = try #require(StoreCatalog.searchableStores(for: "CH").first)
        #expect(StoreCatalog.searchURL(for: "", in: store) == nil)
        #expect(StoreCatalog.searchURL(for: "    ", in: store) == nil)
    }

    @Test("chaque gabarit contient bien le marqueur de requête")
    func gabaritsValides() {
        for pays in ["CH", "FR", "DE", "BE"] {
            for store in StoreCatalog.searchableStores(for: pays) {
                #expect(store.searchTemplate.contains("{q}"),
                        "\(store.domain) n'a pas de marqueur {q}")
                #expect(store.searchTemplate.hasPrefix("https://"),
                        "\(store.domain) doit être en HTTPS")
            }
        }
    }
}

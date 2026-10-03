import Testing
@testable import GiftManager

/// #61 — Détection des doublons quand un cadeau a été recréé faute de le retrouver.
struct DuplicateMatchTests {
    @Test func titreIdentiqueMalgreCasseEtEspaces() {
        #expect(DuplicateMatch.normalize("LEGO  Technic") == DuplicateMatch.normalize("lego technic"))
    }

    @Test func titreIdentiqueMalgreAccentsEtPonctuation() {
        #expect(DuplicateMatch.normalize("Télécommande, 2 piles !") == DuplicateMatch.normalize("telecommande 2 piles"))
    }

    @Test func titresDifferentsNeSontPasConfondus() {
        #expect(DuplicateMatch.normalize("Vélo rouge") != DuplicateMatch.normalize("Vélo bleu"))
    }

    @Test func titreVideNeCorrespondARien() {
        #expect(DuplicateMatch.normalize("   ").isEmpty)
    }

    @Test func memeProduitMalgreParametresDeSuivi() {
        #expect(DuplicateMatch.sameURL("https://www.amazon.fr/dp/B09QFZ2X7C?tag=aff-21&utm_source=wa",
                                       "https://amazon.fr/dp/B09QFZ2X7C"))
    }

    @Test func memeProduitMalgreSlashFinal() {
        #expect(DuplicateMatch.sameURL("https://www.galaxus.ch/fr/s1/product/lego-42141/",
                                       "https://www.galaxus.ch/fr/s1/product/lego-42141"))
    }

    @Test func produitsDifferentsNeSontPasConfondus() {
        #expect(!DuplicateMatch.sameURL("https://www.amazon.fr/dp/B09QFZ2X7C",
                                        "https://www.amazon.fr/dp/B09Y2MYL5C"))
    }

    @Test func boutiquesDifferentesNeSontPasConfondues() {
        #expect(!DuplicateMatch.sameURL("https://www.amazon.fr/dp/B09QFZ2X7C",
                                        "https://www.fnac.com/dp/B09QFZ2X7C"))
    }

    @Test func urlInvalideNeDeclencheAucuneCorrespondance() {
        #expect(!DuplicateMatch.sameURL("pas une url", "pas une url"))
    }
}

import Testing
import Foundation
@testable import GiftManager

/// Le champ « Nom de la famille » doit refléter la famille affichée dès sa construction.
/// Sinon, au changement de famille, l'ancien nom reste une image à l'écran : le bouton
/// « Renommer » clignote, et un appui écrase le nom de la nouvelle famille par l'ancien.
struct RenameGroupStateTests {
    private func group(_ name: String) -> FamilyGroup {
        FamilyGroup(id: UUID(), name: name, inviteCode: "ABC123", createdBy: nil)
    }

    @Test func leNomInitialSuitLaFamilleAffichee() {
        #expect(RenameGroupState(group: group("Lazzarotto")).name == "Lazzarotto")
        #expect(RenameGroupState(group: group("Celiba")).name == "Celiba")
    }

    @Test func aucunBoutonSansModification() {
        // C'est le cœur du bug : à l'arrivée sur une famille, rien ne doit s'afficher.
        #expect(RenameGroupState(group: group("Lazzarotto")).canRename == false)
        #expect(RenameGroupState(group: group("Celiba")).canRename == false)
    }

    @Test func boutonApresUneVraieSaisie() {
        var state = RenameGroupState(group: group("Lazzarotto"))
        state.name = "Famille Lazzarotto"
        #expect(state.canRename)
    }

    @Test func espacesSeulsNeDeclenchentRien() {
        var state = RenameGroupState(group: group("Lazzarotto"))
        state.name = "   "
        #expect(state.canRename == false)
    }

    @Test func memeNomAvecEspacesAutourNeComptePas() {
        var state = RenameGroupState(group: group("Lazzarotto"))
        state.name = "  Lazzarotto  "
        #expect(state.canRename == false)
    }
}

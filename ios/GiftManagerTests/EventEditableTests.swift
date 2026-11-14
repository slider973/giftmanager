import Testing
import Foundation
@testable import GiftManager

/// #89 : l'app ne modifie que les événements « autre » à venir. Noël est global, les anniversaires
/// sont créés par le serveur, et la RLS refuse d'écrire les autres types (même règle qu'Android).
struct EventEditableTests {
    private func event(_ kind: GiftEventKind, daysFromToday: Int) -> GiftEvent {
        let date = Calendar.current.date(byAdding: .day, value: daysFromToday, to: .now)!
        return GiftEvent(id: UUID(), groupId: kind == .other ? UUID() : nil, kind: kind, title: "Fête",
                         eventDate: DayDate(date), childId: kind == .birthday ? UUID() : nil)
    }

    @Test func unEvenementAutreAVenirSeModifie() {
        #expect(event(.other, daysFromToday: 10).isEditable)
        #expect(event(.other, daysFromToday: 0).isEditable)
    }

    @Test func noelEtAnniversaireNeSeModifientPas() {
        #expect(!event(.christmas, daysFromToday: 10).isEditable)
        #expect(!event(.birthday, daysFromToday: 10).isEditable)
    }

    @Test func unEvenementPasseNeSeModifiePas() {
        #expect(!event(.other, daysFromToday: -1).isEditable)
    }
}

import Testing
import Foundation
@testable import GiftManager

/// #115 : le compte voit les événements de toutes ses familles ; seuls ceux de la famille
/// affichée doivent apparaître, sinon changer de famille ne change pas la liste.
struct GroupEventsTests {
    private let family1 = UUID()
    private let family2 = UUID()
    private let lea = Child(id: UUID(), householdId: UUID(), firstName: "Léa", birthdate: nil,
                            avatarEmoji: nil, avatarColor: nil, avatarUrl: nil)
    private let zoe = Child(id: UUID(), householdId: UUID(), firstName: "Zoé", birthdate: nil,
                            avatarEmoji: nil, avatarColor: nil, avatarUrl: nil)

    private func event(_ title: String, _ kind: GiftEventKind, group: UUID? = nil, child: UUID? = nil) -> GiftEvent {
        GiftEvent(id: UUID(), groupId: group, kind: kind, title: title, eventDate: DayDate(.now), childId: child)
    }

    private var events: [GiftEvent] {
        [
            event("Noël", .christmas),
            event("Fête famille 1", .other, group: family1),
            event("Fête famille 2", .other, group: family2),
            event("Anniversaire de Léa", .birthday, child: lea.id),
            event("Anniversaire de Zoé", .birthday, child: zoe.id),
        ]
    }

    @Test func chaqueFamilleNeVoitQueSesEvenements() {
        let in1 = GiftEvent.scoped(events, toGroup: family1, children: [lea]).map(\.title)
        #expect(in1 == ["Noël", "Fête famille 1", "Anniversaire de Léa"])

        let in2 = GiftEvent.scoped(events, toGroup: family2, children: [zoe]).map(\.title)
        #expect(in2 == ["Noël", "Fête famille 2", "Anniversaire de Zoé"])
    }

    @Test func noelResteVisiblePartout() {
        #expect(GiftEvent.scoped(events, toGroup: UUID(), children: []).map(\.title) == ["Noël"])
    }

    @Test func sansFamilleRienNestEcarte() {
        #expect(GiftEvent.scoped(events, toGroup: nil, children: []).count == 5)
    }
}

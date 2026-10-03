import XCTest
@testable import GiftManager

@MainActor
final class ChildModeModelTests: XCTestCase {
    private let child = Child(id: UUID(), householdId: UUID(), firstName: "Léo", birthdate: nil,
                              avatarEmoji: nil, avatarColor: nil, avatarUrl: nil)

    private func item(_ title: String, kind: WishKind = .wish, owned: Bool = false, priority: Int = 0,
                      status: ItemStatus? = nil, reservation: ReservationState? = nil) -> WishItem {
        WishItem(id: UUID(), childId: child.id, eventId: nil, kind: kind, title: title, notes: "Note secrète",
                 imageUrl: "https://example.com/\(title).png", priority: priority, position: 0, owned: owned,
                 createdBy: nil, status: status, myReservation: reservation)
    }

    private final class Recorder: @unchecked Sendable {
        var calls: [(UUID, Bool)] = []
        var error: Error?
    }

    private func model(_ items: [WishItem], recorder: Recorder = Recorder()) -> ChildModeModel {
        ChildModeModel(child: child, wishes: items) { id, favorite in
            recorder.calls.append((id, favorite))
            if let error = recorder.error { throw error }
        }
    }

    func testOnlyWishesOfTheListAreShownInParentOrder() {
        let lego = item("Lego")
        let idea = item("Idée d'un oncle", kind: .idea)
        let owned = item("Déjà possédé", owned: true)
        let bike = item("Vélo")
        let visible = ChildModeModel.visibleItems([lego, idea, owned, bike])
        XCTAssertEqual(visible.map(\.title), ["Lego", "Vélo"])
    }

    func testVisibleItemsCarryNoStatusNorReservation() {
        // Même si le serveur renvoyait un statut ou une réservation, le mode enfant n'en garde rien.
        let reserved = item("Lego", priority: 1, status: .taken, reservation: .reserved)
        let visible = ChildModeModel.visibleItems([reserved])
        XCTAssertEqual(visible, [ChildModeItem(id: reserved.id, title: "Lego", imageURL: reserved.imageURL, isFavorite: true)])
        let fields = Set(Mirror(reflecting: visible[0]).children.compactMap(\.label))
        XCTAssertEqual(fields, ["id", "title", "imageURL", "isFavorite"])
    }

    func testHeartIsSavedOnTheWish() async throws {
        let lego = item("Lego")
        let recorder = Recorder()
        let model = model([lego], recorder: recorder)

        try await model.toggleFavorite(lego.id)
        XCTAssertTrue(model.items[0].isFavorite)
        XCTAssertEqual(model.favoriteCount, 1)
        XCTAssertEqual(recorder.calls.map(\.0), [lego.id])
        XCTAssertEqual(recorder.calls.map(\.1), [true])
        XCTAssertTrue(model.didChange)

        try await model.toggleFavorite(lego.id)
        XCTAssertFalse(model.items[0].isFavorite)
        XCTAssertEqual(recorder.calls.map(\.1), [true, false])
    }

    func testFailedSaveRestoresTheHeart() async {
        let lego = item("Lego", priority: 1)
        let recorder = Recorder()
        recorder.error = URLError(.notConnectedToInternet)
        let model = model([lego], recorder: recorder)

        do {
            try await model.toggleFavorite(lego.id)
            XCTFail("L'erreur doit remonter")
        } catch {}
        XCTAssertTrue(model.items[0].isFavorite)
        XCTAssertFalse(model.didChange)
        XCTAssertTrue(model.saving.isEmpty)
    }

    func testUnknownItemIsIgnored() async throws {
        let recorder = Recorder()
        let model = model([item("Lego")], recorder: recorder)
        try await model.toggleFavorite(UUID())
        XCTAssertTrue(recorder.calls.isEmpty)
        XCTAssertFalse(model.didChange)
    }
}

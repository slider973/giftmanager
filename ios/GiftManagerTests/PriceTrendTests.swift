import XCTest
@testable import GiftManager

final class PriceTrendTests: XCTestCase {
    private let day: TimeInterval = 86_400

    private func check(_ link: UUID, _ daysAgo: Double, _ price: Decimal?, currency: String? = "CHF",
                       inStock: Bool? = true) -> PriceCheck {
        PriceCheck(linkId: link, url: "https://www.galaxus.ch/p/1", price: price, currency: currency,
                   inStock: inStock, checkedAt: Date(timeIntervalSince1970: 1_800_000_000 - daysAgo * day))
    }

    func testTrendFromChecksInAnyOrder() {
        let link = UUID()
        let trends = PriceTrend.trends(from: [check(link, 0, 169), check(link, 6, 199), check(link, 3, 189)])
        XCTAssertEqual(trends.count, 1)
        XCTAssertEqual(trends[0].points.map(\.price), [199, 189, 169])
        XCTAssertEqual(trends[0].change, -30)
        XCTAssertEqual(trends[0].direction, .down)
        XCTAssertEqual(trends[0].storeName, "Galaxus")
    }

    func testMissingPricesAndOtherCurrenciesAreSkipped() {
        let link = UUID()
        let trend = PriceTrend.trends(from: [check(link, 4, 180, currency: "EUR"), check(link, 3, 199),
                                             check(link, 2, nil), check(link, 1, 205)])[0]
        XCTAssertEqual(trend.points.map(\.price), [199, 205])
        XCTAssertEqual(trend.direction, .up)
    }

    func testStockComesFromLatestCheckThatKnowsIt() {
        let link = UUID()
        let trend = PriceTrend.trends(from: [check(link, 2, 199, inStock: false), check(link, 1, 199, inStock: nil)])[0]
        XCTAssertEqual(trend.inStock, false)
        XCTAssertEqual(trend.direction, .flat)
    }

    func testOutOfStockAndDropsComeFirst() {
        let stable = UUID(), drop = UUID(), gone = UUID()
        let trends = PriceTrend.trends(from: [
            check(stable, 2, 50), check(stable, 1, 50),
            check(drop, 2, 50), check(drop, 1, 40),
            check(gone, 1, 60, inStock: false),
        ])
        XCTAssertEqual(trends.map(\.linkId), [gone, drop, stable])
    }

    func testEmptyHistoryShowsNothing() {
        XCTAssertTrue(PriceTrend.trends(from: []).isEmpty)
        XCTAssertTrue(PriceTrend.trends(from: [check(UUID(), 1, nil, inStock: nil)]).isEmpty)
    }

    func testNotificationRouting() {
        let item = UUID()
        XCTAssertEqual(NotificationRouter.itemId(threadId: "price-\(item.uuidString)", userInfo: [:]), item)
        XCTAssertEqual(NotificationRouter.itemId(threadId: nil, userInfo: ["item_id": item.uuidString]), item)
        XCTAssertNil(NotificationRouter.itemId(threadId: "new-items", userInfo: [:]))
        XCTAssertNil(NotificationRouter.itemId(threadId: "price-pas-un-uuid", userInfo: [:]))
    }
}

@MainActor
final class NotificationRouterTests: XCTestCase {
    func testAlertWaitsForChildModeExit() {
        let router = NotificationRouter()
        let item = UUID()
        router.suspended = true
        router.open(threadId: "price-\(item.uuidString)", userInfo: [:])
        XCTAssertNil(router.presentedItem, "Aucune fiche pendant le mode enfant")
        XCTAssertEqual(router.pendingItem?.id, item)
        router.suspended = false
        XCTAssertEqual(router.presentedItem?.id, item, "L'alerte s'ouvre à la sortie")
    }

    func testResetOnSignOut() {
        let router = NotificationRouter()
        router.open(threadId: "price-\(UUID().uuidString)", userInfo: [:])
        router.suspended = true
        router.reset()
        XCTAssertNil(router.pendingItem)
        XCTAssertFalse(router.suspended)
    }
}

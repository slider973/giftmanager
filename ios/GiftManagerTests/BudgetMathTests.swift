import XCTest
@testable import GiftManager

final class BudgetMathTests: XCTestCase {
    private let leo = UUID(), emma = UUID(), noel = UUID(), birthday = UUID()

    private func budget(child: UUID? = nil, event: UUID? = nil, spent: Decimal = 0, amount: Decimal = 200,
                        currency: String = "CHF") -> Budget {
        Budget(id: UUID(), childId: child, childName: child == nil ? nil : "Léo", eventId: event,
               eventTitle: event == nil ? nil : "Noël 2026", amount: amount, currency: currency, spent: spent)
    }

    private func plain(_ text: String) -> String {
        text.replacingOccurrences(of: "\u{00A0}", with: " ").replacingOccurrences(of: "\u{202F}", with: " ")
    }

    func testGaugeText() {
        XCTAssertEqual(plain(BudgetMath.gaugeText(spent: 180, amount: 200, currency: "CHF")), "180 / 200 CHF")
        XCTAssertEqual(plain(BudgetMath.gaugeText(spent: Decimal(string: "49.9")!, amount: 150, currency: "EUR")), "49,90 / 150 €")
        XCTAssertEqual(plain(BudgetMath.amountText(1200, currency: "CHF")), "1 200 CHF")
    }

    func testLevels() {
        XCTAssertEqual(BudgetMath.level(budget(spent: 100)), .comfortable)
        XCTAssertEqual(BudgetMath.level(budget(spent: 180)), .nearlyReached)
        XCTAssertEqual(BudgetMath.level(budget(spent: 200)), .nearlyReached)
        XCTAssertEqual(BudgetMath.level(budget(spent: 201)), .over)
    }

    func testProgressIsCapped() {
        XCTAssertEqual(budget(spent: 50).progress, 0.25, accuracy: 0.0001)
        XCTAssertEqual(budget(spent: 400).progress, 1)
        XCTAssertEqual(budget(spent: 400).remaining, -200)
    }

    func testScopeTitle() {
        XCTAssertEqual(budget(child: leo).scopeTitle, "Léo")
        XCTAssertEqual(budget(event: noel).scopeTitle, "Noël 2026")
        XCTAssertEqual(budget(child: leo, event: noel).scopeTitle, "Léo · Noël 2026")
    }

    func testOverlappingBudgetsAreNotSummed() {
        // Deux enfants différents : disjoints.
        XCTAssertTrue(BudgetMath.areDisjoint([budget(child: leo), budget(child: emma, currency: "EUR")]))
        // Deux événements différents : disjoints.
        XCTAssertTrue(BudgetMath.areDisjoint([budget(event: noel), budget(event: birthday)]))
        // Léo (tous événements) et Noël (tous enfants) comptent les cadeaux de Léo pour Noël.
        XCTAssertFalse(BudgetMath.areDisjoint([budget(child: leo), budget(event: noel)]))
        // Léo et Léo à Noël se recoupent.
        XCTAssertFalse(BudgetMath.areDisjoint([budget(child: leo), budget(child: leo, event: noel)]))
        // Léo à Noël et Emma tous événements : disjoints.
        XCTAssertTrue(BudgetMath.areDisjoint([budget(child: leo, event: noel), budget(child: emma)]))
    }

    func testBudgetDecoding() throws {
        let json = #"""
        [{"id":"5C0E4E8A-0000-4000-8000-000000000001","child_id":null,"child_name":null,
          "event_id":"5C0E4E8A-0000-4000-8000-000000000002","event_title":"Noël 2026",
          "amount":200.00,"currency":"CHF","spent":180.5}]
        """#
        let budgets = try JSONDecoder().decode([Budget].self, from: Data(json.utf8))
        XCTAssertEqual(budgets.first?.scopeTitle, "Noël 2026")
        XCTAssertEqual(budgets.first?.spent, Decimal(string: "180.5"))
    }
}

import XCTest
@testable import GiftManager

/// Décodage des réponses v2 (cagnotte, listes d'adultes, équilibre, remerciements) et règles d'affichage.
final class GivingModelsTests: XCTestCase {
    private let decoder = JSONDecoder()
    private let childId = "11111111-1111-1111-1111-111111111111"
    private let itemId = "22222222-2222-2222-2222-222222222222"

    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try decoder.decode(type, from: Data(json.utf8))
    }

    private func wishJSON(status: String?, extra: String = "") -> String {
        """
        {"id":"\(itemId)","child_id":"\(childId)","event_id":null,"kind":"wish","title":"Vélo",
         "notes":null,"image_url":null,"priority":0,"position":0,"owned":false,"created_by":null,
         "created_at":"2026-10-02T10:00:00+00:00",
         "status":\(status.map { "\"\($0)\"" } ?? "null"),"my_reservation":null\(extra)}
        """
    }

    // MARK: - Cagnotte

    func testWishItemDecodesPotFields() throws {
        let item = try decode(WishItem.self, wishJSON(status: "pot", extra: """
        ,"pot_total":120.5,"pot_currency":"CHF","pot_count":3,"my_contribution":40
        """))
        XCTAssertEqual(item.status, .pot)
        XCTAssertEqual(item.potTotal, Decimal(string: "120.5"))
        XCTAssertEqual(item.potCurrency, "CHF")
        XCTAssertEqual(item.potCount, 3)
        XCTAssertEqual(item.myContribution, 40)
        XCTAssertTrue(item.isInMyPot)
        XCTAssertEqual(item.displayStatus(isParent: false), .pot)
    }

    func testWishItemWithoutPotFieldsStillDecodes() throws {
        let item = try decode(WishItem.self, wishJSON(status: "available"))
        XCTAssertNil(item.potTotal)
        XCTAssertNil(item.myContribution)
        XCTAssertFalse(item.isInMyPot)
    }

    func testParentNeverSeesPotStatus() throws {
        // Même si un statut arrivait, un parent ne voit aucun badge (mode surprise).
        let item = try decode(WishItem.self, wishJSON(status: "pot", extra: #","pot_total":50,"pot_currency":"EUR","pot_count":1"#))
        XCTAssertNil(item.displayStatus(isParent: true))
    }

    func testParentRowHasNoPotProgress() throws {
        // Le serveur renvoie des champs de cagnotte nuls pour les parents : pas de progression.
        let item = try decode(WishItem.self, wishJSON(status: nil, extra: #","pot_total":null,"pot_currency":null,"pot_count":null"#))
        XCTAssertNil(PotProgress.make(item: item, links: []))
    }

    func testPotParticipantDecoding() throws {
        let rows = try decode([PotParticipant].self, """
        [{"display_name":"Mamie","amount":50,"currency":"CHF","is_me":false},
         {"display_name":"Jonathan","amount":25.5,"currency":"CHF","is_me":true}]
        """)
        XCTAssertEqual(rows.count, 2)
        XCTAssertEqual(rows[1].amount, Decimal(string: "25.5"))
        XCTAssertTrue(rows[1].isMe)
    }

    func testMyContributionDecoding() throws {
        let rows = try decode([MyContribution].self, """
        [{"item_id":"\(itemId)","title":"Vélo","image_url":null,"amount":40,"currency":"EUR","owned":false,
          "pot_total":120,"pot_count":3,"child_id":"\(childId)","child_name":"Emma",
          "event_id":null,"event_title":"Noël 2026","event_date":"2026-12-25"}]
        """)
        XCTAssertEqual(rows.first?.amount, 40)
        XCTAssertEqual(rows.first?.potCount, 3)
        XCTAssertEqual(rows.first?.eventDate, DayDate(makeDate(2026, 12, 25)))
    }

    func testPotProgressAgainstPriceInSameCurrency() {
        let links = [
            link(price: 230, currency: "EUR"),
            link(price: 200, currency: "CHF"),
        ]
        let progress = PotProgress(collected: 150, currency: "CHF",
                                   target: PotProgress.target(currency: "CHF", links: links), participants: 3)
        XCTAssertEqual(progress.target, 200)
        XCTAssertEqual(progress.fraction ?? 0, 0.75, accuracy: 0.0001)
        XCTAssertEqual(progress.remaining, 50)
        XCTAssertFalse(progress.isComplete)
        XCTAssertEqual(progress.participantsText, "3 participants")
    }

    func testPotProgressWithoutPriceHasNoGauge() {
        let progress = PotProgress(collected: 60, currency: "CHF",
                                   target: PotProgress.target(currency: "CHF", links: [link(price: 99, currency: "EUR")]),
                                   participants: 1)
        XCTAssertNil(progress.fraction)
        XCTAssertNil(progress.remaining)
        XCTAssertFalse(progress.isComplete)
        XCTAssertEqual(progress.participantsText, "1 participant")
    }

    func testPotProgressIsClampedWhenOverfunded() {
        let progress = PotProgress(collected: 260, currency: "CHF", target: 200, participants: 4)
        XCTAssertEqual(progress.fraction, 1)
        XCTAssertEqual(progress.remaining, 0)
        XCTAssertTrue(progress.isComplete)
    }

    func testPotAmountParsing() {
        XCTAssertEqual(PotAmount.parse("25"), 25)
        XCTAssertEqual(PotAmount.parse("12,50"), Decimal(string: "12.5"))
        XCTAssertEqual(PotAmount.parse(" 12.5 "), Decimal(string: "12.5"))
        XCTAssertEqual(PotAmount.parse("1'000"), 1000)
        XCTAssertEqual(PotAmount.parse("10,555"), Decimal(string: "10.56"))
        XCTAssertNil(PotAmount.parse(""))
        XCTAssertNil(PotAmount.parse("0"))
        XCTAssertNil(PotAmount.parse("-5"))
        XCTAssertNil(PotAmount.parse("abc"))
        XCTAssertNil(PotAmount.parse("1,2,3"))
        XCTAssertNil(PotAmount.parse("100000000"))
    }

    func testPotAmountSuggestionsIncludeRemaining() {
        XCTAssertEqual(PotAmount.suggestions(remaining: nil), [10, 20, 50])
        XCTAssertEqual(PotAmount.suggestions(remaining: 35), [10, 20, 35])
        XCTAssertEqual(PotAmount.suggestions(remaining: 20), [10, 20])
        XCTAssertEqual(PotAmount.suggestions(remaining: 0), [10, 20, 50])
    }

    // MARK: - Listes d'adultes

    func testChildDecodesAdultFlagAndDefaultsToChild() throws {
        let adult = try decode(Child.self, """
        {"id":"\(childId)","household_id":"\(itemId)","first_name":"Papa","birthdate":null,
         "avatar_emoji":null,"avatar_color":null,"avatar_url":null,"is_adult":true}
        """)
        XCTAssertTrue(adult.isAdult)
        let legacy = try decode(Child.self, """
        {"id":"\(childId)","household_id":"\(itemId)","first_name":"Léo","birthdate":"2018-03-12"}
        """)
        XCTAssertFalse(legacy.isAdult)
    }

    func testChildEncodesAdultFlag() throws {
        let child = Child(id: UUID(), householdId: UUID(), firstName: "Mamie", birthdate: nil, avatarEmoji: nil,
                          avatarColor: nil, avatarUrl: nil, isAdult: true)
        let object = try JSONSerialization.jsonObject(with: JSONEncoder().encode(child)) as? [String: Any]
        XCTAssertEqual(object?["is_adult"] as? Bool, true)
    }

    // MARK: - Équilibre

    func testReservationCountsSummary() throws {
        let rows = try decode([ReservationCounts].self, """
        [{"child_id":"\(childId)","reserved_count":2,"purchased_count":1,"pot_count":1},
         {"child_id":"\(itemId)","reserved_count":0,"purchased_count":0,"pot_count":0}]
        """)
        XCTAssertEqual(rows[0].total, 4)
        XCTAssertEqual(rows[0].summaryText, "2 réservés · 1 acheté · 1 cagnotte")
        XCTAssertEqual(rows[0].accessibilityText, "2 cadeaux réservés, 1 acheté, 1 en cagnotte")
        XCTAssertEqual(rows[1].summaryText, "Rien de prévu pour l'instant")
    }

    func testReservationCountsSingularForms() {
        let counts = ReservationCounts(childId: UUID(), reservedCount: 1, purchasedCount: 0, potCount: 2)
        XCTAssertEqual(counts.summaryText, "1 réservé · 2 cagnottes")
    }

    // MARK: - Remerciements

    func testThanksDecodingWithMicroseconds() throws {
        let rows = try decode([ThanksNote].self, """
        [{"id":"\(itemId)","item_id":"\(itemId)","title":"Vélo","child_name":"Emma","sender_name":"Claire",
          "message":"Merci !","photo_url":null,"created_at":"2026-12-26T09:15:42.123456+00:00","revealed":false}]
        """)
        XCTAssertEqual(rows.first?.senderName, "Claire")
        XCTAssertFalse(rows.first?.revealed ?? true)
        XCTAssertNotNil(rows.first?.createdDate)
    }

    func testDonorDecoding() throws {
        let donors = try decode([ItemDonor].self, #"[{"display_name":"Mamie"}]"#)
        XCTAssertEqual(donors.map(\.displayName), ["Mamie"])
    }

    func testReceiptRulesFollowEventDate() {
        let today = makeDate(2026, 12, 26)
        let christmas = DayDate(makeDate(2026, 12, 25))
        let nextYear = DayDate(makeDate(2027, 12, 25))
        let sameDay = DayDate(today)

        XCTAssertTrue(ReceiptRules.canMarkReceived(isParent: true, kind: .wish, owned: false, eventDate: christmas, today: today))
        XCTAssertTrue(ReceiptRules.canMarkReceived(isParent: true, kind: .wish, owned: false, eventDate: sameDay, today: today))
        XCTAssertFalse(ReceiptRules.canMarkReceived(isParent: true, kind: .wish, owned: false, eventDate: nextYear, today: today))
        XCTAssertFalse(ReceiptRules.canMarkReceived(isParent: false, kind: .wish, owned: false, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.canMarkReceived(isParent: true, kind: .idea, owned: false, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.canMarkReceived(isParent: true, kind: .wish, owned: true, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.canMarkReceived(isParent: true, kind: .wish, owned: false, eventDate: nil, today: today))

        XCTAssertTrue(ReceiptRules.canSendThanks(isParent: true, kind: .wish, owned: true, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.canSendThanks(isParent: true, kind: .wish, owned: false, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.canSendThanks(isParent: false, kind: .wish, owned: true, eventDate: christmas, today: today))

        XCTAssertTrue(ReceiptRules.isReceived(owned: true, eventDate: christmas, today: today))
        XCTAssertFalse(ReceiptRules.isReceived(owned: true, eventDate: nextYear, today: today))
        XCTAssertFalse(ReceiptRules.isReceived(owned: false, eventDate: christmas, today: today))
    }

    // MARK: - Anniversaires

    func testProfileBirthdayReminderPreference() throws {
        let id = UUID().uuidString
        let off = try decode(Profile.self, """
        {"id":"\(id)","display_name":"Mamie","country":"CH","currency":"CHF","onboarded":true,
         "notify_birthday_reminders":false}
        """)
        XCTAssertFalse(off.notifyBirthdayReminders)
        let legacy = try decode(Profile.self, """
        {"id":"\(id)","display_name":"Mamie","country":"CH","currency":"CHF","onboarded":true}
        """)
        XCTAssertTrue(legacy.notifyBirthdayReminders)
    }

    func testDonorNamesAreJoinedInFrench() {
        XCTAssertEqual(GiftDetailView.names(["Mamie"]), "Mamie")
        XCTAssertEqual(GiftDetailView.names(["Mamie", "Paul"]), "Mamie et Paul")
        XCTAssertEqual(GiftDetailView.names(["Mamie", "Paul", "Léa"]), "Mamie, Paul et Léa")
    }

    // MARK: - Utilitaires

    private func link(price: Decimal, currency: String) -> ItemLink {
        ItemLink(id: UUID(), itemId: UUID(), url: "https://example.com", store: nil, country: nil,
                 price: price, currency: currency)
    }

    private func makeDate(_ year: Int, _ month: Int, _ day: Int) -> Date {
        Calendar.current.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
    }
}

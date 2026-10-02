import XCTest
@testable import GiftManager

final class StoreCatalogTests: XCTestCase {
    func testKnownStoresGiveCountryAndCurrency() {
        XCTAssertEqual(StoreCatalog.store(for: "https://www.galaxus.ch/fr/s1/product/123"),
                       .init(name: "Galaxus", country: "CH", currency: "CHF"))
        XCTAssertEqual(StoreCatalog.store(for: "https://www.amazon.fr/dp/B09QFZ2X7C")?.country, "FR")
        XCTAssertEqual(StoreCatalog.store(for: "https://amazon.de/dp/X")?.currency, "EUR")
    }

    func testUnknownStoreUsesDomainAndTopLevelDomain() {
        let store = StoreCatalog.store(for: "https://www.jouets-du-lac.ch/produit/42")
        XCTAssertEqual(store?.name, "Jouets-du-lac")
        XCTAssertEqual(store?.country, "CH")
        XCTAssertEqual(store?.currency, "CHF")
    }

    func testLinksOfReaderCountryComeFirst() {
        let item = UUID()
        let fr = ItemLink(id: UUID(), itemId: item, url: "https://amazon.fr", store: "Amazon", country: "FR", price: 10, currency: "EUR")
        let ch = ItemLink(id: UUID(), itemId: item, url: "https://galaxus.ch", store: "Galaxus", country: "CH", price: 11, currency: "CHF")
        XCTAssertEqual(StoreCatalog.sorted([fr, ch], preferredCountry: "CH").first?.country, "CH")
        XCTAssertEqual(StoreCatalog.sorted([ch, fr], preferredCountry: "FR").first?.country, "FR")
    }

    func testPriceParsing() {
        XCTAssertEqual(LinkPreviewService.parsePrice("199,99"), Decimal(string: "199.99"))
        XCTAssertEqual(LinkPreviewService.parsePrice("1'299.00"), Decimal(string: "1299.00"))
        XCTAssertEqual(LinkPreviewService.parsePrice("CHF 49.–"), Decimal(49))
        XCTAssertEqual(LinkPreviewService.parsePrice("1.299,00"), Decimal(string: "1299.00"))
        XCTAssertEqual(LinkPreviewService.parsePrice("1'299.90"), Decimal(string: "1299.90"))
        XCTAssertEqual(LinkPreviewService.parsePrice("19,99"), Decimal(string: "19.99"))
        XCTAssertEqual(LinkPreviewService.parsePrice("1.299"), Decimal(1299))
        XCTAssertNil(LinkPreviewService.parsePrice("999999999"))
        XCTAssertNil(LinkPreviewService.parsePrice("gratuit"))
    }

    func testDisplayedPriceText() {
        XCTAssertEqual(LinkPreviewService.priceFromDisplayedText("157,09 € avec 13 % d'économies"), Decimal(string: "157.09"))
        XCTAssertEqual(LinkPreviewService.priceFromDisplayedText("149,31CHF"), Decimal(string: "149.31"))
        XCTAssertEqual(LinkPreviewService.priceFromDisplayedText("CHF 1'299.–"), Decimal(1299))
        XCTAssertEqual(LinkPreviewService.priceFromDisplayedText("$1,299.99"), Decimal(string: "1299.99"))
        XCTAssertEqual(LinkPreviewService.priceFromDisplayedText("1.299,00 €"), Decimal(string: "1299.00"))
        XCTAssertNil(LinkPreviewService.priceFromDisplayedText("Indisponible"))
    }

    func testCurrencyFromDisplayedText() {
        XCTAssertEqual(LinkPreviewService.currency(inDisplayedText: "149,31CHF", storeCountry: "DE"), "CHF")
        XCTAssertEqual(LinkPreviewService.currency(inDisplayedText: "159,99€", storeCountry: "DE"), "EUR")
        XCTAssertEqual(LinkPreviewService.currency(inDisplayedText: "£20.00", storeCountry: "GB"), "GBP")
        XCTAssertEqual(LinkPreviewService.currency(inDisplayedText: "$30", storeCountry: "CA"), "CAD")
        XCTAssertEqual(LinkPreviewService.currency(inDisplayedText: "$30", storeCountry: "US"), "USD")
        XCTAssertNil(LinkPreviewService.currency(inDisplayedText: "30", storeCountry: "FR"))
    }

    func testSharedTextExtractsFirstLink() {
        let url = LinkPreviewService.normalizedURL("Regarde ça 👉 https://www.galaxus.ch/fr/s1/product/123 trop bien")
        XCTAssertEqual(url?.host(), "www.galaxus.ch")
        XCTAssertEqual(LinkPreviewService.normalizedURL("amazon.fr/dp/X")?.scheme, "https")
    }

    func testChildEncodesClearedFieldsAsNull() throws {
        let child = Child(id: UUID(), householdId: UUID(), firstName: "Léo", birthdate: nil,
                          avatarEmoji: nil, avatarColor: nil, avatarUrl: nil)
        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(child)) as? [String: Any]
        XCTAssertTrue(json?["birthdate"] is NSNull, "une date effacée doit être envoyée à null")
        XCTAssertTrue(json?["avatar_emoji"] is NSNull)
    }

    func testAgeIsComputedAtEventDate() throws {
        let child = Child(id: UUID(), householdId: UUID(), firstName: "Léo",
                          birthdate: try JSONDecoder().decode(DayDate.self, from: Data("\"2018-03-12\"".utf8)),
                          avatarEmoji: nil, avatarColor: nil, avatarUrl: nil)
        let birthday = try JSONDecoder().decode(DayDate.self, from: Data("\"2027-03-12\"".utf8))
        XCTAssertEqual(child.age(on: birthday.localDate), 9)
    }

    func testDayDateRoundTrip() throws {
        let decoded = try JSONDecoder().decode(DayDate.self, from: Data("\"2026-12-25\"".utf8))
        let encoded = try JSONEncoder().encode(decoded)
        XCTAssertEqual(String(data: encoded, encoding: .utf8), "\"2026-12-25\"")
        XCTAssertEqual(Calendar.current.component(.day, from: decoded.localDate), 25)
    }
}

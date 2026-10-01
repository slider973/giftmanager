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
        XCTAssertEqual(LinkPreviewService.parsePrice("CHF 49.–"), Decimal(string: "49."))
    }

    func testSharedTextExtractsFirstLink() {
        let url = LinkPreviewService.normalizedURL("Regarde ça 👉 https://www.galaxus.ch/fr/s1/product/123 trop bien")
        XCTAssertEqual(url?.host(), "www.galaxus.ch")
        XCTAssertEqual(LinkPreviewService.normalizedURL("amazon.fr/dp/X")?.scheme, "https")
    }

    func testDayDateRoundTrip() throws {
        let decoded = try JSONDecoder().decode(DayDate.self, from: Data("\"2026-12-25\"".utf8))
        let encoded = try JSONEncoder().encode(decoded)
        XCTAssertEqual(String(data: encoded, encoding: .utf8), "\"2026-12-25\"")
        XCTAssertEqual(Calendar.current.component(.day, from: decoded.localDate), 25)
    }
}

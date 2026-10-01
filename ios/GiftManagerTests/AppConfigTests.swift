import XCTest
@testable import GiftManager

final class AppConfigTests: XCTestCase {
    func testSupabaseConfigIsPresent() {
        XCTAssertNotNil(AppConfig.supabaseURL.host)
        XCTAssertFalse(AppConfig.supabasePublishableKey.isEmpty)
    }
}

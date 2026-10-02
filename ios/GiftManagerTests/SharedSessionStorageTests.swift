import Auth
import XCTest
@testable import GiftManager

final class SharedSessionStorageTests: XCTestCase {
    /// Stockage en mémoire qui, comme le trousseau, lève une erreur quand on supprime un élément absent.
    private final class MemoryStorage: AuthLocalStorage, @unchecked Sendable {
        struct NotFound: Error {}
        var items: [String: Data] = [:]
        func store(key: String, value: Data) throws { items[key] = value }
        func retrieve(key: String) throws -> Data? { items[key] }
        func remove(key: String) throws {
            guard items.removeValue(forKey: key) != nil else { throw NotFound() }
        }
    }

    private let key = "sb-auth-token"
    private let session = Data("session".utf8)

    func testMigratesLegacySessionWithoutLosingIt() throws {
        let shared = MemoryStorage(), legacy = MemoryStorage()
        legacy.items[key] = session
        let storage = SharedSessionStorage(shared: shared, legacy: legacy)

        XCTAssertEqual(try storage.retrieve(key: key), session)
        XCTAssertEqual(shared.items[key], session, "la session doit rester dans le trousseau partagé")
        XCTAssertNil(legacy.items[key])
        XCTAssertEqual(try storage.retrieve(key: key), session, "lecture suivante : toujours connecté")
    }

    func testSharedSessionTakesPrecedence() throws {
        let shared = MemoryStorage(), legacy = MemoryStorage()
        shared.items[key] = Data("nouvelle".utf8)
        legacy.items[key] = Data("ancienne".utf8)

        XCTAssertEqual(try SharedSessionStorage(shared: shared, legacy: legacy).retrieve(key: key), Data("nouvelle".utf8))
    }

    func testSignOutRemovesLegacyEvenWhenSharedIsEmpty() throws {
        let shared = MemoryStorage(), legacy = MemoryStorage()
        legacy.items[key] = session
        let storage = SharedSessionStorage(shared: shared, legacy: legacy)

        try storage.remove(key: key)

        XCTAssertNil(legacy.items[key])
        XCTAssertNil(try storage.retrieve(key: key), "aucune session ne doit être re-migrée après déconnexion")
    }

    func testExtensionWithoutLegacyStorage() throws {
        let storage = SharedSessionStorage(shared: MemoryStorage(), legacy: nil)
        XCTAssertNil(try storage.retrieve(key: key))
        XCTAssertNoThrow(try storage.remove(key: key))
    }
}

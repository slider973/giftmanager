import Testing
@testable import GiftManager

/// Traduction des erreurs serveur pour l'utilisateur.
struct GiftErrorTests {
    private struct Server: Error, CustomStringConvertible {
        let description: String
    }

    @Test func colonneAbsenteSignaleUneAppTropAncienne() {
        // Cas réel : l'app installée demandait households.group_id, supprimée par la migration #60.
        let error = GiftError(Server(description: #"{"code":"42703","message":"column households.group_id does not exist"}"#))
        #expect(error == .outdatedApp)
        #expect(error.errorDescription?.contains("TestFlight") == true)
        // Le message SQL brut ne doit jamais atteindre l'utilisateur.
        #expect(error.errorDescription?.contains("group_id") == false)
    }

    @Test func fonctionRpcAbsenteSignaleAussiUneAppTropAncienne() {
        #expect(GiftError(Server(description: #"{"code":"PGRST202","message":"function not found"}"#)) == .outdatedApp)
        #expect(GiftError(Server(description: #"{"code":"42883","message":"function public.foo does not exist"}"#)) == .outdatedApp)
    }

    @Test func erreursMetierRestentDistinctes() {
        #expect(GiftError(Server(description: "ITEM_UNAVAILABLE")) == .unavailable)
        #expect(GiftError(Server(description: "ITEM_OWNED")) == .owned)
        #expect(GiftError(Server(description: "INVALID_INVITE_CODE")) == .invalidInvite)
        #expect(GiftError(Server(description: "ALREADY_IN_HOUSEHOLD")) == .alreadyInHousehold)
        #expect(GiftError(Server(description: "CURRENCY_MISMATCH")) == .currencyMismatch)
    }

    @Test func cadeauIntrouvableNePassePasPourUneAppObsolete() {
        // ITEM_NOT_FOUND est testé avant la détection d'obsolescence : il reste une erreur métier.
        #expect(GiftError(Server(description: "ITEM_NOT_FOUND")) == .notFound)
    }
}

extension GiftError: @retroactive Equatable {
    public static func == (lhs: GiftError, rhs: GiftError) -> Bool {
        switch (lhs, rhs) {
        case (.unavailable, .unavailable), (.owned, .owned), (.notFound, .notFound),
             (.invalidInvite, .invalidInvite), (.alreadyInHousehold, .alreadyInHousehold),
             (.currencyMismatch, .currencyMismatch), (.outdatedApp, .outdatedApp):
            true
        case (.other(let a), .other(let b)):
            a == b
        default:
            false
        }
    }
}

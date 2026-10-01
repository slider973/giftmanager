import AuthenticationServices
import CryptoKit
import Foundation
import Supabase

/// Sign in with Apple natif : le jeton d'identité Apple est échangé contre une session Supabase.
@MainActor
final class AppleSignIn {
    private var currentNonce: String?

    func prepare(_ request: ASAuthorizationAppleIDRequest) {
        let nonce = Self.randomNonce()
        currentNonce = nonce
        request.requestedScopes = [.fullName, .email]
        request.nonce = Self.sha256(nonce)
    }

    /// Renvoie le prénom fourni par Apple (uniquement à la première connexion), s'il existe.
    func complete(_ result: Result<ASAuthorization, Error>, client: SupabaseClient) async throws -> String? {
        let authorization = try result.get()
        guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
              let tokenData = credential.identityToken,
              let idToken = String(data: tokenData, encoding: .utf8),
              let nonce = currentNonce else {
            throw URLError(.userAuthenticationRequired)
        }
        try await client.auth.signInWithIdToken(credentials: .init(provider: .apple, idToken: idToken, nonce: nonce))
        return credential.fullName?.givenName
    }

    private static func randomNonce(length: Int = 32) -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        return String((0..<length).map { _ in charset.randomElement(using: &generator)! })
    }

    private static func sha256(_ input: String) -> String {
        SHA256.hash(data: Data(input.utf8)).map { String(format: "%02x", $0) }.joined()
    }
}

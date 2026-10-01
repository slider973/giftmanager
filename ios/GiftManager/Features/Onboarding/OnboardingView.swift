import AuthenticationServices
import SwiftUI

/// Onboarding (écran 1 de la maquette) puis connexion avec Apple.
struct OnboardingView: View {
    @Environment(AppState.self) private var appState
    @State private var page = 0
    @State private var appleSignIn = AppleSignIn()
    @State private var isSigningIn = false

    private struct Page: Identifiable {
        let id: Int
        let image: String
        let title: String
        let message: String
    }

    private let pages = [
        Page(id: 0, image: "mascot_family", title: "Bienvenue sur\nFamille Cadeaux",
             message: "Organisez les cadeaux de toute la famille en toute simplicité."),
        Page(id: 1, image: "illustration_travel", title: "Plusieurs foyers,\nplusieurs pays",
             message: "Suisse, France ou ailleurs : chacun ajoute des liens des boutiques de son pays."),
        Page(id: 2, image: "mascot_gift", title: "Des listes\npour chaque fête",
             message: "Noël, anniversaires… Les enfants choisissent leurs envies avec leurs parents."),
        Page(id: 3, image: "mascot_surprise", title: "La surprise\nest garantie",
             message: "Les autres voient « déjà pris » sans savoir par qui. Les parents ne voient rien."),
    ]

    private var isLastPage: Bool { page == pages.count - 1 }

    var body: some View {
        VStack(spacing: Spacing.xl) {
            TabView(selection: $page) {
                ForEach(pages) { page in
                    VStack(spacing: Spacing.xl) {
                        Spacer(minLength: Spacing.l)
                        Image(page.image)
                            .resizable()
                            .scaledToFit()
                            .frame(maxWidth: 280, maxHeight: 280)
                            .accessibilityHidden(true)
                        Text(page.title)
                            .font(Font.Theme.title)
                            .foregroundStyle(Color.Theme.textPrimary)
                            .multilineTextAlignment(.center)
                        Text(page.message)
                            .font(Font.Theme.body)
                            .foregroundStyle(Color.Theme.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, Spacing.xl)
                        Spacer(minLength: Spacing.l)
                    }
                    .tag(page.id)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .onAppear {
                // Points de pagination lisibles sur le fond crème.
                UIPageControl.appearance().currentPageIndicatorTintColor = UIColor(Color.Theme.primary)
                UIPageControl.appearance().pageIndicatorTintColor = UIColor(Color.Theme.primary).withAlphaComponent(0.25)
            }

            VStack(spacing: Spacing.m) {
                if isLastPage {
                    SignInWithAppleButton(.continue) { request in
                        appleSignIn.prepare(request)
                    } onCompletion: { result in
                        Task { await signIn(result) }
                    }
                    .signInWithAppleButtonStyle(.black)
                    .frame(height: 54)
                    .clipShape(Capsule())
                    .disabled(isSigningIn)
                    .overlay { if isSigningIn { ProgressView().tint(.white) } }

                    #if DEBUG
                    DemoSignInButton()
                    #endif
                } else {
                    PrimaryButton(title: "Continuer", systemImage: "chevron.right") {
                        withAnimation { page += 1 }
                    }
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.bottom, Spacing.l)
        }
        .fcScreenBackground()
    }

    private func signIn(_ result: Result<ASAuthorization, Error>) async {
        if case .failure(let error) = result, (error as? ASAuthorizationError)?.code == .canceled { return }
        isSigningIn = true
        defer { isSigningIn = false }
        do {
            let givenName = try await appleSignIn.complete(result, client: appState.repository.client)
            if let givenName, !givenName.isEmpty { appState.suggestedName = givenName }
        } catch {
            appState.errorMessage = "La connexion avec Apple a échoué. Réessaie."
        }
    }
}

#if DEBUG
/// Connexion aux comptes de démo du Supabase local (supabase/seed.sql). Absent des builds Release.
private struct DemoSignInButton: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        Menu("Compte de démo (dev)") {
            ForEach(["alice", "bob", "carol"], id: \.self) { name in
                Button(name.capitalized) {
                    Task {
                        do {
                            try await appState.repository.client.auth.signIn(email: "\(name)@demo.local", password: "demo1234")
                        } catch {
                            appState.errorMessage = error.localizedDescription
                        }
                    }
                }
            }
        }
        .font(Font.Theme.caption)
        .foregroundStyle(Color.Theme.textSecondary)
    }
}
#endif

#Preview {
    OnboardingView().environment(AppState())
}

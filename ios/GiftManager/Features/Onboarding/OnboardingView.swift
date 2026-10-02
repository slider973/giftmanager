import AuthenticationServices
import SwiftUI

/// Onboarding (écran 1 de la maquette) puis connexion avec Apple.
struct OnboardingView: View {
    @Environment(AppState.self) private var appState
    @State private var page = 0
    @State private var appleSignIn = AppleSignIn()
    @State private var isSigningIn = false
    @Environment(\.colorScheme) private var colorScheme

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
        VStack(spacing: Spacing.l) {
            TabView(selection: $page) {
                ForEach(pages) { page in
                    pageView(page).tag(page.id)
                }
            }
            // Points dessinés en SwiftUI : `UIPageControl` ne suit ni les couleurs du thème
            // en sombre, ni le premier affichage (apparence posée trop tard).
            .tabViewStyle(.page(indexDisplayMode: .never))

            PageDots(count: pages.count, current: page)

            VStack(spacing: Spacing.s) {
                if isLastPage {
                    SignInWithAppleButton(.continue) { request in
                        appleSignIn.prepare(request)
                    } onCompletion: { result in
                        Task { await signIn(result) }
                    }
                    // Noir sur crème, blanc sur bleu nuit (règle Apple de contraste du bouton).
                    .signInWithAppleButtonStyle(colorScheme == .dark ? .white : .black)
                    .frame(height: HitTarget.button)
                    .clipShape(Capsule())
                    .disabled(isSigningIn)
                    .opacity(isSigningIn ? 0.6 : 1)
                    .overlay { if isSigningIn { ProgressView() } }
                    .id(colorScheme)

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
            .padding(.bottom, Spacing.s)
        }
        .padding(.bottom, Spacing.s)
        .fcScreenBackground()
    }

    private func pageView(_ page: Page) -> some View {
        VStack(spacing: Spacing.xxl) {
            Spacer(minLength: Spacing.l)
            Image(page.image)
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 280, maxHeight: 280)
                .accessibilityHidden(true)
            VStack(spacing: Spacing.m) {
                Text(page.title)
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text(page.message)
                    .font(Font.Theme.body)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .lineSpacing(2)
                    .frame(maxWidth: 320)
            }
            .multilineTextAlignment(.center)
            .padding(.horizontal, Spacing.xl)
            Spacer(minLength: Spacing.s)
        }
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
        .frame(minHeight: HitTarget.minimum)
    }
}
#endif

/// Pagination à points de l'onboarding : point actif allongé en `primary`,
/// autres points en `textSecondary` atténué — lisibles en clair comme en sombre.
private struct PageDots: View {
    let count: Int
    let current: Int

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: Spacing.s) {
            ForEach(0..<count, id: \.self) { index in
                Capsule()
                    .fill(index == current ? Color.Theme.primary : Color.Theme.textSecondary.opacity(0.4))
                    .frame(width: index == current ? 20 : 8, height: 8)
            }
        }
        .animation(reduceMotion ? nil : .spring(response: 0.3, dampingFraction: 0.8), value: current)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Page \(current + 1) sur \(count)")
    }
}

#Preview {
    OnboardingView().environment(AppState())
}

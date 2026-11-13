import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState
    /// Animation du cadeau qui s'ouvre, jouée une fois au démarrage par-dessus le premier écran.
    @State private var showSplash = true

    var body: some View {
        @Bindable var appState = appState
        ZStack {
            screens
            if showSplash {
                GiftOpeningSplash(onFinished: dismissSplash)
                    .transition(.opacity)
                    .zIndex(1)
            }
        }
        .alert("Oups", isPresented: Binding(
            get: { appState.errorMessage != nil },
            set: { if !$0 { appState.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(appState.errorMessage ?? "")
        }
    }

    private var screens: some View {
        Group {
            switch appState.phase {
            case .launching:
                LaunchView()
            case .signedOut:
                OnboardingView()
            case .needsProfile:
                ProfileSetupView()
            case .needsGroup:
                GroupSetupView()
            case .ready:
                MainTabView()
            }
        }
        .animation(.easeInOut(duration: 0.3), value: appState.phase)
    }

    /// Appelé à la fin de l'animation ou au toucher ; ne joue qu'une fois.
    private func dismissSplash() {
        guard showSplash else { return }
        withAnimation(.easeOut(duration: 0.35)) { showSplash = false }
    }
}

private struct LaunchView: View {
    var body: some View {
        GiftLoadingView(size: 128)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .fcScreenBackground()
    }
}

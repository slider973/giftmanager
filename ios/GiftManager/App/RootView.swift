import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState

    /// L'animation d'ouverture du cadeau dure environ 3,3 s. Sans ce délai minimum,
    /// un démarrage rapide la coupe au bout de quelques dixièmes de seconde et on ne voit rien.
    @State private var launchAnimationDone = false

    /// Phase affichée : on reste sur l'écran de lancement tant que l'animation n'a pas fini.
    private var displayedPhase: AppState.Phase {
        launchAnimationDone ? appState.phase : .launching
    }

    var body: some View {
        @Bindable var appState = appState
        Group {
            switch displayedPhase {
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
        .task {
            try? await Task.sleep(for: .seconds(3.4))
            launchAnimationDone = true
        }
        .animation(.easeInOut(duration: 0.3), value: displayedPhase)
        .alert("Oups", isPresented: Binding(
            get: { appState.errorMessage != nil },
            set: { if !$0 { appState.errorMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(appState.errorMessage ?? "")
        }
    }
}

private struct LaunchView: View {
    var body: some View {
        GiftLoadingView(size: 128)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .fcScreenBackground()
    }
}

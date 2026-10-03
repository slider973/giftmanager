import SwiftUI

struct RootView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        @Bindable var appState = appState
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
        .animation(.easeInOut(duration: 0.25), value: appState.phase)
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
        GiftLoadingView(size: 104)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .fcScreenBackground()
    }
}

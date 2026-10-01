import SwiftUI

@main
struct GiftManagerApp: App {
    @State private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(appState)
                .tint(Color.Theme.primary)
                .task { appState.start() }
                .onOpenURL { url in appState.handleDeepLink(url) }
        }
    }
}

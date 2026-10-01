import SwiftUI

@main
struct GiftManagerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var appState = AppState()

    init() {
        FCSystemAppearance.apply()
    }

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

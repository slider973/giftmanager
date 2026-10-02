import SwiftUI
import WidgetKit

@main
struct GiftManagerApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
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
                .task { await CurrencyService.shared.refreshIfNeeded() }
                .onOpenURL { url in appState.handleDeepLink(url) }
        }
        .onChange(of: scenePhase) { _, phase in
            // Le widget relit les données avec la session partagée (connexion, événements, achats).
            if phase == .background { WidgetCenter.shared.reloadAllTimelines() }
            if phase == .active { Task { await CurrencyService.shared.refreshIfNeeded() } }
        }
    }
}

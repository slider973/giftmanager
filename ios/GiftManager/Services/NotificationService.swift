import Foundation
import UIKit
import UserNotifications

/// Notifications (#15) :
///  - rappels locaux J-30 / J-7 avant un événement s'il reste des cadeaux réservés non achetés ;
///  - push « nouvelles envies » (jeton APNs enregistré côté serveur, envoi par la fonction notify-new-items).
/// Aucune notification ne révèle une réservation : les rappels sont calculés sur l'appareil, pour soi.
@MainActor
final class NotificationService: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationService()

    enum Setting: String {
        case purchaseReminders = "notifications.purchaseReminders"
        case familyNews = "notifications.familyNews"
    }

    private let center = UNUserNotificationCenter.current()
    private let reminderPrefix = "reminder-"
    /// Appelé quand iOS fournit le jeton APNs (via l'AppDelegate).
    var onDeviceToken: ((String) -> Void)?

    override init() {
        super.init()
        center.delegate = self
    }

    static func isEnabled(_ setting: Setting) -> Bool {
        UserDefaults.standard.object(forKey: setting.rawValue) as? Bool ?? true
    }

    static func set(_ setting: Setting, _ value: Bool) {
        UserDefaults.standard.set(value, forKey: setting.rawValue)
    }

    @discardableResult
    func requestAuthorization() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    func registerForRemote() async {
        guard Self.isEnabled(.familyNews), await requestAuthorization() else { return }
        UIApplication.shared.registerForRemoteNotifications()
    }

    // MARK: - Rappels d'achats
    //
    // #59 : les rappels partent désormais du serveur (fonction Edge purchase-reminders,
    // J-30 / J-15 / J-7 / J-2 à 18 h locales). La planification locale est supprimée — elle
    // ne partait que si l'app avait été ouverte à temps, et ferait maintenant doublon.

    /// Supprime les rappels locaux encore planifiés par les versions précédentes.
    func cancelLocalPurchaseReminders() async {
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(reminderPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
    }

    func scheduleReminders(reservations: [MyReservation], events: [GiftEvent]) async {
        // Le serveur s'en charge : on se contente de nettoyer l'héritage local.
        await cancelLocalPurchaseReminders()
    }

    // MARK: - UNUserNotificationCenterDelegate

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification)
        async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    /// Toucher une alerte de prix ouvre la fiche du cadeau (#39).
    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
                                            didReceive response: UNNotificationResponse) async {
        let content = response.notification.request.content
        let threadId = content.threadIdentifier
        guard let itemId = NotificationRouter.itemId(threadId: threadId, userInfo: content.userInfo) else { return }
        await MainActor.run { NotificationRouter.shared.pendingItem = .init(id: itemId) }
    }
}

/// Reçoit le jeton APNs.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        Task { @MainActor in NotificationService.shared.onDeviceToken?(token) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {}
}

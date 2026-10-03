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

    func scheduleReminders(reservations: [MyReservation], events: [GiftEvent]) async {
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix(reminderPrefix) }
        center.removePendingNotificationRequests(withIdentifiers: pending)
        guard Self.isEnabled(.purchaseReminders) else { return }

        let toBuy = reservations.filter { $0.status == .reserved && !$0.owned }
        let byEvent = Dictionary(grouping: toBuy) { $0.eventId }
        guard !byEvent.isEmpty, await requestAuthorization() else { return }

        let calendar = Calendar.current
        for (eventId, items) in byEvent {
            guard let eventId, let event = events.first(where: { $0.id == eventId }) else { continue }
            for daysBefore in [30, 7] {
                guard let day = calendar.date(byAdding: .day, value: -daysBefore, to: event.eventDate.localDate),
                      var fire = calendar.date(bySettingHour: 18, minute: 0, second: 0, of: day),
                      fire > .now else { continue }
                fire = max(fire, .now.addingTimeInterval(60))
                let content = UNMutableNotificationContent()
                content.title = "\(event.title) dans \(daysBefore) jours"
                content.body = items.count > 1
                    ? "Il te reste \(items.count) cadeaux à acheter."
                    : "Il te reste « \(items[0].title) » à acheter."
                content.sound = .default
                let trigger = UNCalendarNotificationTrigger(
                    dateMatching: calendar.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
                try? await center.add(UNNotificationRequest(identifier: "\(reminderPrefix)\(eventId)-\(daysBefore)",
                                                            content: content, trigger: trigger))
            }
        }
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

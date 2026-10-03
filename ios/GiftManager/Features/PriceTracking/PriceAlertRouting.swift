import Observation
import SwiftUI

/// Ouverture de la fiche d'un cadeau quand on touche une alerte de prix (#39).
///
/// La fonction `track-prices` envoie `aps.thread-id = "price-<item_id>"` ; une clé `item_id`
/// dans la charge utile est aussi acceptée.
@MainActor
@Observable
final class NotificationRouter {
    static let shared = NotificationRouter()

    struct PendingItem: Identifiable, Equatable {
        let id: UUID
    }

    /// Cadeau à ouvrir dès que l'interface principale est prête.
    var pendingItem: PendingItem?

    nonisolated static func itemId(threadId: String?, userInfo: [AnyHashable: Any]) -> UUID? {
        if let raw = userInfo["item_id"] as? String, let id = UUID(uuidString: raw) { return id }
        guard let threadId, threadId.hasPrefix("price-") else { return nil }
        return UUID(uuidString: String(threadId.dropFirst("price-".count)))
    }

    func open(threadId: String?, userInfo: [AnyHashable: Any]) {
        guard let id = Self.itemId(threadId: threadId, userInfo: userInfo) else { return }
        pendingItem = PendingItem(id: id)
    }
}

/// Présente la fiche du cadeau demandé par une notification, au-dessus de l'onglet courant.
struct PriceAlertPresenter: ViewModifier {
    @Bindable private var router = NotificationRouter.shared

    func body(content: Content) -> some View {
        content.sheet(item: $router.pendingItem) { pending in
            NavigationStack {
                ReservedGiftLoader(itemId: pending.id)
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Fermer") { router.pendingItem = nil }
                        }
                    }
            }
        }
    }
}

/// Retrouve un cadeau parmi mes réservations, puis affiche sa fiche.
private struct ReservedGiftLoader: View {
    let itemId: UUID

    @Environment(AppState.self) private var appState
    @State private var state: LoadState = .loading

    private enum LoadState {
        case loading
        case loaded(WishItem, GiftListModel)
        case missing
    }

    var body: some View {
        Group {
            switch state {
            case .loading:
                ProgressView("Chargement du cadeau…")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .tint(Color.Theme.primary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .fcScreenBackground()
            case .loaded(let item, let model):
                GiftDetailView(item: item, model: model)
            case .missing:
                EmptyStateView(imageName: "mascot_thinking", title: "Cadeau introuvable",
                               message: "Il ne fait plus partie de tes réservations, ou il a été retiré de la liste.")
                    .padding(Spacing.xl)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .fcScreenBackground()
            }
        }
        .task(id: itemId) { await load() }
    }

    private func load() async {
        do {
            let reservations = try await appState.repository.myReservations()
            guard let reservation = reservations.first(where: { $0.itemId == itemId }),
                  let child = appState.children.first(where: { $0.id == reservation.childId }) else {
                state = .missing
                return
            }
            let model = GiftListModel(child: child, eventId: reservation.eventId)
            try await model.load(appState.repository)
            guard let item = model.items.first(where: { $0.id == itemId }) else {
                state = .missing
                return
            }
            state = .loaded(item, model)
        } catch {
            appState.report(error)
            state = .missing
        }
    }
}

import SwiftUI

/// Détail d'un événement : les enfants concernés et l'accès à leurs listes (#8).
struct EventDetailView: View {
    let event: GiftEvent
    @Environment(AppState.self) private var appState
    @State private var editing: EventEditorView.Mode?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.m) {
                HStack(spacing: Spacing.m) {
                    EventKindTile(kind: event.kind.designKind)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(Formatting.dateText(event.eventDate))
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                        Text(event.isPast ? "Événement passé" : Formatting.countdownText(days: event.daysRemaining))
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                }
                .accessibilityElement(children: .combine)
                .padding(.bottom, Spacing.s)

                if event.isPast {
                    FCNotice(systemImage: "archivebox", text: "Événement passé : les listes sont archivées en lecture seule.")
                }

                let mine = appState.children(for: event).filter { appState.isParent(of: $0) }
                let others = appState.children(for: event).filter { !appState.isParent(of: $0) }

                if !others.isEmpty {
                    SectionHeader(title: "Les listes de la famille")
                    ForEach(others) { childLink($0) }
                }
                if !mine.isEmpty {
                    SectionHeader(title: "Mes enfants")
                        .padding(.top, others.isEmpty ? 0 : Spacing.m)
                    ForEach(mine) { childLink($0) }
                    FCNotice(systemImage: "eye.slash",
                             text: "Mode surprise : tu ne vois pas ce qui a été réservé pour tes enfants.",
                             tone: .surprise)
                        .padding(.top, Spacing.xs)
                }
                if appState.children(for: event).isEmpty {
                    EmptyStateView(imageName: "empty_box", title: "Aucun enfant",
                                   message: "Ajoute les enfants dans Famille › Membres.")
                }
            }
            .padding(Spacing.xl)
        }
        .fcScreenBackground()
        .navigationTitle(event.title)
        .toolbar {
            if !event.isPast {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Modifier") { editing = .edit(event) }
                }
            }
        }
        .sheet(item: $editing) { EventEditorView(mode: $0) }
    }

    private func childLink(_ child: Child) -> some View {
        NavigationLink(value: ChildDestination(child: child, eventId: event.id, readOnly: event.isPast)) {
            HStack {
                ChildRow(child: child)
                Spacer(minLength: Spacing.s)
                Image(systemName: "chevron.right")
                    .font(Font.Theme.callout.weight(.semibold))
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }
            .fcCard(padding: Spacing.m)
        }
        .buttonStyle(FCPressableStyle())
    }
}

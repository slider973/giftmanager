import SwiftUI

/// Détail d'un événement : les listes concernées, enfants puis adultes (#8, #37).
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

                let all = appState.children(for: event)
                let mine = all.filter { appState.isParent(of: $0) }
                let otherKids = all.filter { !appState.isParent(of: $0) && !$0.isAdult }
                let otherAdults = all.filter { !appState.isParent(of: $0) && $0.isAdult }

                if !otherKids.isEmpty {
                    SectionHeader(title: "Les listes des enfants")
                    ForEach(otherKids) { childLink($0) }
                }
                if !otherAdults.isEmpty {
                    SectionHeader(title: "Les listes des adultes")
                        .padding(.top, otherKids.isEmpty ? 0 : Spacing.m)
                    ForEach(otherAdults) { childLink($0) }
                }
                if !mine.isEmpty {
                    // Mes enfants et les listes d'adultes de mon foyer : mode surprise pour toutes.
                    SectionHeader(title: mine.contains(where: \.isAdult) ? "Mon foyer" : "Mes enfants")
                        .padding(.top, otherKids.isEmpty && otherAdults.isEmpty ? 0 : Spacing.m)
                    ForEach(mine) { childLink($0) }
                    FCNotice(systemImage: "eye.slash",
                             text: mine.contains(where: \.isAdult)
                                ? "Mode surprise : tu ne vois pas ce qui a été réservé sur les listes de ton foyer."
                                : "Mode surprise : tu ne vois pas ce qui a été réservé pour tes enfants.",
                             tone: .surprise)
                        .padding(.top, Spacing.xs)
                }
                if all.isEmpty {
                    EmptyStateView(imageName: "empty_box", title: "Aucune liste",
                                   message: "Ajoute les enfants ou une liste d'adulte dans Famille › Membres.")
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

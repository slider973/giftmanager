import SwiftUI

/// Détail d'un événement : les listes concernées, enfants puis adultes (#8, #37).
struct EventDetailView: View {
    let event: GiftEvent
    @Environment(AppState.self) private var appState
    @State private var editing: EventEditorView.Mode?
    /// Compteurs anonymes par liste (#41) ; jamais renvoyés pour mon foyer.
    @State private var counts: [UUID: ReservationCounts] = [:]

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
                        if let line = birthdayLine {
                            Text(line)
                                .font(Font.Theme.caption.weight(.semibold))
                                .foregroundStyle(Color.Theme.textPrimary)
                        }
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
        .task(id: appState.itemsRevision) { await loadCounts() }
        .refreshable {
            await appState.reloadGroup()
            appState.itemsChanged()
        }
    }

    /// Anniversaire (#43) : « Léo fête ses 8 ans ». Rien sans date de naissance.
    private var birthdayLine: String? {
        guard event.kind == .birthday, let child = appState.child(event.childId),
              let age = child.age(on: event.eventDate.localDate), let ageText = Formatting.ageText(age) else { return nil }
        return event.isPast ? "\(child.firstName) a fêté ses \(ageText)" : "\(child.firstName) fête ses \(ageText)"
    }

    private func loadCounts() async {
        guard !event.isPast else { return }
        do {
            let rows = try await appState.repository.reservationCounts(eventId: event.id)
            counts = Dictionary(rows.map { ($0.childId, $0) }, uniquingKeysWith: { first, _ in first })
        } catch {
            appState.report(error)
        }
    }

    /// Indicateur d'équilibre discret : rien pour mes propres listes, ni sans compteur du serveur.
    @ViewBuilder
    private func balance(for child: Child) -> some View {
        if !appState.isParent(of: child), let count = counts[child.id] {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
                Image(systemName: count.total > 0 ? "gift" : "hourglass")
                    .accessibilityHidden(true)
                Text(count.summaryText)
                    .monospacedDigit()
            }
            .font(Font.Theme.caption.weight(count.total > 0 ? .regular : .medium))
            // Aucun cadeau prévu : ambre doux (4,5:1 sur surface) pour qu'aucun enfant ne soit oublié.
            .foregroundStyle(count.total > 0 ? Color.Theme.textSecondary : Color.Theme.accentAmber)
            .padding(.leading, 40 + Spacing.m)
            .accessibilityLabel("Équilibre : \(count.accessibilityText)")
        }
    }

    private func childLink(_ child: Child) -> some View {
        NavigationLink(value: ChildDestination(child: child, eventId: event.id, readOnly: event.isPast)) {
            HStack {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    ChildRow(child: child)
                    balance(for: child)
                }
                Spacer(minLength: Spacing.s)
                Image(systemName: "chevron.right")
                    .font(Font.Theme.callout.weight(.semibold))
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }
            .fcCard(padding: Spacing.m)
            .accessibilityElement(children: .combine)
        }
        .buttonStyle(FCPressableStyle())
    }
}

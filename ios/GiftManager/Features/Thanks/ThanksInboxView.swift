import SwiftUI

/// Remerciements reçus pour les cadeaux que j'ai offerts (#42), avec l'option de me faire connaître.
/// Par défaut je reste anonyme : les parents ne voient un donateur que s'il se dévoile.
struct ThanksInboxView: View {
    @Environment(AppState.self) private var appState
    @State private var notes: [ThanksNote] = []
    @State private var hasLoaded = false
    @State private var pending: Set<UUID> = []

    var body: some View {
        List {
            if hasLoaded && notes.isEmpty {
                EmptyStateView(imageName: "mascot_sleeping", title: "Aucun merci pour l'instant",
                               message: "Après la fête, les parents peuvent remercier pour un cadeau : leur message arrivera ici.")
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            ForEach(notes) { note in
                card(note)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.s, leading: Spacing.xl, bottom: Spacing.s, trailing: Spacing.xl))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .fcScreenBackground()
        .overlay {
            if !hasLoaded {
                ProgressView("Chargement des remerciements…")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .tint(Color.Theme.primary)
            }
        }
        .navigationTitle("Remerciements")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task { await load() }
    }

    private func card(_ note: ThanksNote) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack(spacing: Spacing.m) {
                ChildAvatar(name: note.senderName ?? note.childName, emoji: nil, colorName: nil, size: 40)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(note.senderName.map { "De \($0)" } ?? "Des parents de \(note.childName)")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text(subtitle(note))
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                Spacer(minLength: 0)
            }
            .accessibilityElement(children: .combine)

            Text(note.message)
                .font(Font.Theme.callout)
                .foregroundStyle(Color.Theme.textPrimary)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .textSelection(.enabled)

            if let url = note.photoURL {
                RemoteImage(url: url, contentMode: .fill, placeholderSeed: note.title)
                    .frame(maxWidth: .infinity)
                    .frame(height: 220)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                    .accessibilityLabel("Photo jointe au remerciement")
            }

            Divider().overlay(Color.Theme.separator)

            Toggle(isOn: Binding(get: { note.revealed }, set: { value in Task { await reveal(note, value) } })) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Me faire connaître")
                        .font(Font.Theme.callout.weight(.medium))
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text(note.revealed ? "Les parents de \(note.childName) savent que c'est toi."
                                       : "Les parents ne savent pas que c'est toi.")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                }
            }
            .tint(Color.Theme.secondary)
            .disabled(pending.contains(note.id))
            .frame(minHeight: HitTarget.minimum)
            .accessibilityHint("Révèle ton prénom aux parents pour ce cadeau seulement")
        }
        .fcCard()
    }

    private func subtitle(_ note: ThanksNote) -> String {
        var parts = ["Pour « \(note.title) »"]
        if let date = note.createdDate {
            parts.append(date.formatted(.relative(presentation: .named).locale(Locale(identifier: "fr_FR"))))
        }
        return parts.joined(separator: " · ")
    }

    private func load() async {
        do {
            notes = try await appState.repository.myThanks()
        } catch {
            appState.report(error)
        }
        hasLoaded = true
    }

    /// Bascule optimiste ; tous les remerciements du même cadeau suivent.
    private func reveal(_ note: ThanksNote, _ value: Bool) async {
        pending.insert(note.id)
        defer { pending.remove(note.id) }
        setRevealed(itemId: note.itemId, value)
        do {
            try await appState.repository.revealMyself(itemId: note.itemId, reveal: value)
        } catch {
            setRevealed(itemId: note.itemId, !value)
            appState.report(error)
        }
    }

    private func setRevealed(itemId: UUID, _ value: Bool) {
        for index in notes.indices where notes[index].itemId == itemId {
            notes[index].revealed = value
        }
    }
}

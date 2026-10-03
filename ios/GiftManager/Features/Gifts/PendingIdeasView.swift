import SwiftUI

/// Idées proposées pour mes enfants, en attente de mon verdict (#57).
///
/// Le parent est le seul à savoir si un cadeau convient (âge, doublon, règles de la maison).
/// Il accepte, refuse, ou signale que l'enfant l'a déjà — sans jamais apprendre qui l'offrira :
/// le mode surprise porte sur la réservation, pas sur l'objet.
struct PendingIdeasView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var ideas: [PendingIdea] = []
    @State private var hasLoaded = false
    @State private var working: UUID?
    @State private var rejecting: PendingIdea?
    @State private var note = ""

    var body: some View {
        List {
            if hasLoaded && ideas.isEmpty {
                EmptyStateView(imageName: "mascot_thinking", title: "Aucune proposition",
                               message: "Quand la famille proposera un cadeau pour tes enfants, tu le verras ici avant qu'il ne rejoigne leur liste.",
                               actionTitle: nil) {}
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }
            ForEach(ideas) { idea in
                card(idea)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.xl,
                                              bottom: Spacing.xs, trailing: Spacing.xl))
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .fcScreenBackground()
        .navigationTitle("Propositions")
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await load() }
        .task(id: appState.itemsRevision) { await load() }
        .alert("Refuser cette idée ?", isPresented: Binding(get: { rejecting != nil },
                                                            set: { if !$0 { rejecting = nil; note = "" } })) {
            TextField("Un mot pour l'auteur (facultatif)", text: $note)
            Button("Refuser", role: .destructive) {
                if let idea = rejecting { Task { await decide(idea, .rejected) } }
                rejecting = nil
            }
            Button("Annuler", role: .cancel) { rejecting = nil; note = "" }
        } message: {
            Text("L'idée ne rejoindra pas la liste. Son auteur en sera informé, sans savoir ce que tu as prévu.")
        }
        .overlay {
            if !hasLoaded {
                ProgressView("Chargement…")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .tint(Color.Theme.primary)
            }
        }
    }

    private func card(_ idea: PendingIdea) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack(alignment: .top, spacing: Spacing.m) {
                RemoteImage(url: idea.imageURL, contentMode: .fill, placeholderSeed: idea.title)
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(idea.title)
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text("\(idea.author) propose ce cadeau pour \(idea.childName)")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                    if let notes = idea.notes, !notes.isEmpty {
                        Text(notes)
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                            .lineLimit(3)
                    }
                }
                Spacer(minLength: 0)
            }

            HStack(spacing: Spacing.s) {
                PrimaryButton(title: "Accepter", systemImage: "checkmark",
                              isLoading: working == idea.id) {
                    Task { await decide(idea, .accepted) }
                }
                SecondaryButton(title: "Il l'a déjà", systemImage: "checkmark.seal") {
                    Task { await decide(idea, .ownedAlready) }
                }
            }
            TextLinkButton(title: "Refuser") { rejecting = idea; note = "" }
                .frame(maxWidth: .infinity)

            Label("Tu ne sauras jamais qui offre ce cadeau.", systemImage: "eye.slash")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .fcCard()
        .disabled(working != nil)
        .accessibilityElement(children: .contain)
    }

    private func load() async {
        do {
            ideas = try await appState.repository.pendingIdeas()
        } catch {
            appState.report(error)
        }
        hasLoaded = true
    }

    private func decide(_ idea: PendingIdea, _ decision: IdeaReview) async {
        working = idea.id
        defer { working = nil }
        do {
            try await appState.repository.reviewIdea(itemId: idea.id, decision: decision,
                                                     note: note.trimmed.isEmpty ? nil : note.trimmed)
            note = ""
            ideas.removeAll { $0.id == idea.id }
            appState.itemsChanged()
        } catch {
            appState.report(error)
        }
    }
}

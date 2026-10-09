import SwiftUI

/// Validation parentale des pistes de cadeaux trouvées.
///
/// Rien n'entre dans la liste de l'enfant sans un geste explicite du parent.
/// Les boutiques affichées sont celles du pays du foyer : un parent suisse
/// voit Franz Carl Weber et Galaxus, un parent français King Jouet et la Fnac.
struct SantaSuggestionsView: View {
    let model: SantaSessionModel
    let childName: String

    @Environment(\.openURL) private var openURL
    @State private var saving: UUID?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                header

                ForEach(model.suggestions) { s in
                    suggestionCard(s)
                }

                Button("Terminer sans en retenir d'autre") {
                    model.finish()
                }
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.m)
            }
            .padding(Spacing.l)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text("Ce que \(childName) a demandé")
                .font(Font.Theme.title)
                .foregroundStyle(Color.Theme.textPrimary)

            if !model.wishes.enviePrincipale.isEmpty {
                Text("« \(model.wishes.enviePrincipale) »")
                    .font(Font.Theme.body)
                    .foregroundStyle(Color.Theme.textSecondary)
            }

            Text("Ouvre une boutique pour vérifier, puis retiens ce qui convient. Rien n'est ajouté sans ton accord.")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
                .padding(.top, Spacing.xs)
        }
    }

    private func suggestionCard(_ s: SantaSessionModel.Suggestion) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            HStack(spacing: Spacing.s) {
                Text(s.storeName)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)

                CountryFlag(code: s.store.country)

                Spacer()
            }

            Text(s.query)
                .font(Font.Theme.body)
                .foregroundStyle(Color.Theme.textSecondary)

            HStack(spacing: Spacing.m) {
                Button {
                    openURL(s.url)
                } label: {
                    Label("Voir la boutique", systemImage: "safari")
                        .font(Font.Theme.callout)
                }
                .buttonStyle(.bordered)

                Spacer()

                Button {
                    model.reject(s)
                } label: {
                    Image(systemName: "xmark")
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                .buttonStyle(.bordered)
                .accessibilityLabel("Écarter cette piste")

                Button {
                    saving = s.id
                    Task {
                        await model.approve(s)
                        saving = nil
                    }
                } label: {
                    if saving == s.id {
                        ProgressView().controlSize(.small)
                    } else {
                        Label("Retenir", systemImage: "checkmark")
                            .font(Font.Theme.callout)
                    }
                }
                .buttonStyle(.borderedProminent)
                .tint(Color.Theme.primary)
                .disabled(saving != nil)
                .accessibilityLabel("Retenir cette piste pour \(childName)")
            }
        }
        .padding(Spacing.l)
        .background(Color.Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: Radius.card))
    }
}

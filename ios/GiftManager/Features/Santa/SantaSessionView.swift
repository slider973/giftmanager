import SwiftUI

/// Session du Père Noël, menée par le parent avec l'enfant à côté.
///
/// Le Père Noël parle, l'enfant répond à voix haute, le parent retranscrit.
/// Aucun micro n'est demandé : pas de permission à accorder, pas de latence
/// de transcription, et le parent filtre naturellement ce qui est consigné.
struct SantaSessionView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var model: SantaSessionModel
    @State private var player = SantaVoicePlayer()
    @State private var answer: String = ""
    @FocusState private var answerFocused: Bool

    private let childName: String

    init(model: SantaSessionModel, childName: String) {
        self._model = State(initialValue: model)
        self.childName = childName
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.Theme.background.ignoresSafeArea()
                content
            }
            .navigationTitle("Le Père Noël")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Fermer") {
                        player.stop()
                        dismiss()
                    }
                }
            }
        }
        .onAppear { model.start() }
        .onDisappear { player.stop() }
    }

    @ViewBuilder
    private var content: some View {
        switch model.stage {
        case .notStarted:
            ProgressView().tint(Color.Theme.primary)

        case .blocked(let reason):
            EmptyStateView(
                imageName: "mascot_thinking",
                title: "Session impossible",
                message: reason
            )

        case .speaking, .awaitingAnswer:
            conversation

        case .reviewingSuggestions:
            SantaSuggestionsView(model: model, childName: childName)

        case .done:
            doneView
        }
    }

    // MARK: - Conversation

    private var conversation: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
                santaHeader

                if let line = model.currentLine {
                    Text(line.texte)
                        .font(Font.Theme.title)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .multilineTextAlignment(.center)
                        .lineSpacing(4)
                        .padding(.horizontal, Spacing.l)
                        .transition(.opacity)

                    if player.audioUnavailable {
                        // L'audio manque : le parent lit le texte lui-même
                        // plutôt que de voir la session s'arrêter.
                        Text("Lis ce texte à voix haute")
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }

                    if case .awaitingAnswer = model.stage {
                        answerArea(for: line)
                    } else {
                        continueButton(for: line)
                    }
                }
            }
            .padding(.vertical, Spacing.xxl)
        }
        .onChange(of: model.stage) { _, _ in speakCurrentLine() }
        .onAppear { speakCurrentLine() }
    }

    private var santaHeader: some View {
        VStack(spacing: Spacing.s) {
            Image(systemName: player.isPlaying ? "waveform.circle.fill" : "gift.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(Color.Theme.primary)
                .symbolEffect(.pulse, isActive: player.isPlaying)

            Text(player.isPlaying ? "Le Père Noël parle…" : "À \(childName) de répondre")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
    }

    @ViewBuilder
    private func continueButton(for line: SantaScript.Line) -> some View {
        Button {
            player.stop()
            model.advanceAfterSpeaking()
        } label: {
            Text(player.isPlaying ? "Passer" : "Continuer")
                .frame(maxWidth: .infinity)
        }
        .buttonStyle(.borderedProminent)
        .tint(Color.Theme.primary)
        .padding(.horizontal, Spacing.xl)
    }

    @ViewBuilder
    private func answerArea(for line: SantaScript.Line) -> some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            if let aide = line.aideParent {
                Text(aide)
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }

            if let choix = line.choix {
                // Questions à choix : l'enfant montre, le parent tape.
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 140), spacing: Spacing.s)],
                          spacing: Spacing.s) {
                    ForEach(choix) { c in
                        choiceChip(c)
                    }
                }
            } else {
                FCTextField(title: "Réponse", text: $answer)
                    .focused($answerFocused)
                    .submitLabel(.done)
                    .onSubmit { submit(line) }
            }

            HStack(spacing: Spacing.m) {
                if line.facultatif {
                    Button("Passer") {
                        answer = ""
                        player.stop()
                        model.skipAnswer()
                    }
                    .buttonStyle(.bordered)
                }

                Button("Valider") { submit(line) }
                    .buttonStyle(.borderedProminent)
                    .tint(Color.Theme.primary)
                    .disabled(!canSubmit(line))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, Spacing.xl)
    }

    private func choiceChip(_ c: SantaScript.Choice) -> some View {
        let selected = model.wishes.activites.contains(c.cle)
        return Button {
            model.toggleActivity(c.cle)
        } label: {
            Text(c.libelle)
                .font(Font.Theme.callout)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.m)
                .background(selected ? Color.Theme.primary : Color.Theme.surface)
                .foregroundStyle(selected ? Color.Theme.onPrimary : Color.Theme.textPrimary)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }

    private var doneView: some View {
        VStack(spacing: Spacing.l) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 56))
                .foregroundStyle(Color.Theme.primary)

            Text(model.savedCount > 0
                 ? "\(model.savedCount) idée\(model.savedCount > 1 ? "s" : "") enregistrée\(model.savedCount > 1 ? "s" : "")"
                 : "Session terminée")
                .font(Font.Theme.title)
                .foregroundStyle(Color.Theme.textPrimary)

            Button("Terminer") { dismiss() }
                .buttonStyle(.borderedProminent)
                .tint(Color.Theme.primary)
                .padding(.horizontal, Spacing.xl)
        }
        .padding(Spacing.xxl)
    }

    // MARK: - Actions

    private func canSubmit(_ line: SantaScript.Line) -> Bool {
        if line.choix != nil { return !model.wishes.activites.isEmpty || line.facultatif }
        if line.facultatif { return true }
        return !answer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private func submit(_ line: SantaScript.Line) {
        player.stop()
        answerFocused = false
        model.submitAnswer(answer)
        answer = ""
    }

    private func speakCurrentLine() {
        guard case .speaking = model.stage, let line = model.currentLine else { return }
        player.play(resource: line.audioResource) {
            // Une réplique sans question enchaîne d'elle-même ; sinon on laisse
            // le parent saisir la réponse à son rythme.
            if !line.attendReponse {
                model.advanceAfterSpeaking()
            } else {
                model.advanceAfterSpeaking()
            }
        }
    }
}

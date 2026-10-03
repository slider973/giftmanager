import PhotosUI
import SwiftUI

/// Remercier pour un cadeau reçu (#42). Écrit par un parent ; le serveur transmet le message
/// aux donateurs sans jamais révéler leur identité (réponse identique s'il n'y en a aucun).
struct ThanksComposerView: View {
    let item: WishItem
    let childName: String

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var message = ""
    @State private var photoItem: PhotosPickerItem?
    @State private var imageData: Data?
    @State private var isSending = false
    @State private var errorText: String?
    @State private var sent = false
    @FocusState private var messageFocused: Bool
    @ScaledMetric(relativeTo: .body) private var editorHeight: CGFloat = 140

    static let maxLength = 1000

    private var trimmed: String { message.trimmed }
    private var canSend: Bool { !trimmed.isEmpty && trimmed.count <= Self.maxLength && !isSending }

    var body: some View {
        NavigationStack {
            Group {
                if sent {
                    sentState
                } else {
                    form
                }
            }
            .fcScreenBackground()
            .navigationTitle("Dire merci")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !sent {
                    ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                }
            }
        }
        .onAppear {
            if message.isEmpty { message = "Merci beaucoup pour « \(item.title) » !" }
        }
        .onChange(of: photoItem) { _, newItem in
            Task {
                guard let data = try? await newItem?.loadTransferable(type: Data.self),
                      let image = UIImage(data: data) else { return }
                imageData = image.resizedJPEG(maxDimension: 1200)
            }
        }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                HStack(spacing: Spacing.m) {
                    RemoteImage(url: item.imageURL, placeholderSeed: item.title)
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                        .accessibilityHidden(true)
                    VStack(alignment: .leading, spacing: Spacing.xs) {
                        Text(item.title)
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                            .lineLimit(2)
                        Text("Reçu par \(childName)")
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                    Spacer(minLength: 0)
                }
                .accessibilityElement(children: .combine)

                FCNotice(systemImage: "lock.fill",
                         text: "Ton message arrivera chez la personne qui a offert ce cadeau. Tu ne sauras pas qui c'est, sauf si elle choisit de se faire connaître.",
                         tone: .surprise)

                VStack(alignment: .leading, spacing: Spacing.s) {
                    Text("Ton message")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .accessibilityHidden(true)
                    TextEditor(text: $message)
                        .font(Font.Theme.body)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .tint(Color.Theme.primary)
                        .scrollContentBackground(.hidden)
                        .focused($messageFocused)
                        .frame(minHeight: editorHeight)
                        .padding(.horizontal, Spacing.m)
                        .padding(.vertical, Spacing.s)
                        .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                                .strokeBorder(messageFocused ? Color.Theme.primary : Color.Theme.separator,
                                              lineWidth: messageFocused ? 1.5 : 1)
                        }
                        .accessibilityLabel("Ton message")
                    Text("\(trimmed.count) / \(Self.maxLength)")
                        .font(Font.Theme.caption)
                        .monospacedDigit()
                        .foregroundStyle(trimmed.count > Self.maxLength ? Color.Theme.takenFg : Color.Theme.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .accessibilityLabel("\(trimmed.count) caractères sur \(Self.maxLength)")
                }

                photoSection

                if let errorText {
                    FCNotice(systemImage: "exclamationmark.triangle", text: errorText, tone: .warning)
                }

                PrimaryButton(title: "Envoyer le merci", systemImage: "paperplane.fill", isLoading: isSending) {
                    Task { await send() }
                }
                .disabled(!canSend)
            }
            .padding(Spacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    @ViewBuilder
    private var photoSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("Photo (facultatif)")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.textSecondary)
                .accessibilityHidden(true)
            if let imageData, let image = UIImage(data: imageData) {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .frame(height: 200)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        .accessibilityLabel("Photo jointe")
                    Button {
                        self.imageData = nil
                        photoItem = nil
                    } label: {
                        Image(systemName: "xmark")
                            .font(Font.Theme.callout.weight(.bold))
                            .foregroundStyle(Color.Theme.textPrimary)
                            .frame(width: 34, height: 34)
                            .background(Color.Theme.surface, in: Circle())
                            .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                    .padding(Spacing.xs)
                    .accessibilityLabel("Retirer la photo")
                }
            } else {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Ajouter une photo", systemImage: "camera.fill")
                        .font(Font.Theme.callout.weight(.medium))
                        .foregroundStyle(Color.Theme.primary)
                        .frame(maxWidth: .infinity, minHeight: 72)
                        .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
                        .overlay {
                            RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                                .strokeBorder(Color.Theme.separator, style: StrokeStyle(lineWidth: 1, dash: [5, 4]))
                        }
                        .contentShape(Rectangle())
                }
                .buttonStyle(FCPressableStyle())
                .accessibilityHint("Une photo de l'enfant avec son cadeau, par exemple")
            }
        }
    }

    private var sentState: some View {
        VStack(spacing: Spacing.l) {
            Spacer(minLength: 0)
            EmptyStateView(imageName: "mascot_love", title: "Merci envoyé !",
                           message: "Il arrivera chez la personne qui a offert « \(item.title) », sans révéler qui c'est.")
            PrimaryButton(title: "Terminer") { dismiss() }
                .frame(maxWidth: 320)
                .padding(.horizontal, Spacing.xl)
            Spacer(minLength: 0)
        }
        .sensoryFeedback(.success, trigger: sent)
    }

    private func send() async {
        guard canSend, let userId = appState.userId else { return }
        isSending = true
        errorText = nil
        defer { isSending = false }
        do {
            var photoURL: String?
            if let imageData {
                // URL publique du bucket `images` : seule forme acceptée par le serveur.
                photoURL = try await appState.repository.uploadImage(imageData, userId: userId).absoluteString
            }
            try await appState.repository.sendThanks(itemId: item.id, message: trimmed, photoURL: photoURL)
            withAnimation(.easeOut(duration: 0.25)) { sent = true }
        } catch {
            if error is CancellationError { return }
            errorText = GiftError(error).localizedDescription
        }
    }
}

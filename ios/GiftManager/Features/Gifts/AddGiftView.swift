import PhotosUI
import SwiftUI

/// Ajouter / modifier un cadeau (écran 4 de la maquette) : lien de n'importe quelle boutique,
/// aperçu automatique, plusieurs liens par pays, photo manuelle (#9, #10, #17).
struct AddGiftView: View {
    let child: Child
    let kind: WishKind
    let eventId: UUID?
    var owned = false
    var existing: WishItem?
    var existingLinks: [ItemLink] = []
    let onDone: () -> Void

    @Environment(AppState.self) private var appState

    @State private var urlText = ""
    @State private var title = ""
    @State private var notes = ""
    @State private var priceText = ""
    @State private var currency = "CHF"
    @State private var store: StoreCatalog.Store?
    @State private var imageURL: URL?
    @State private var imageData: Data?
    @State private var isFavorite = false
    @State private var selectedEvent: UUID?
    @State private var otherLinks: [DraftLink] = []
    @State private var photoItem: PhotosPickerItem?
    @State private var isFetching = false
    @State private var previewFailed = false
    @State private var isSaving = false
    @State private var showAddLink = false
    @State private var fetchTask: Task<Void, Never>?

    private var isEditing: Bool { existing != nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                urlField
                previewCard
                if !isEditing || kind == .wish { optionsCard }
                otherLinksSection
                PrimaryButton(title: isEditing ? "Enregistrer" : (kind == .idea ? "Proposer cette idée" : "Ajouter à la liste"),
                              systemImage: "checkmark", isLoading: isSaving) {
                    Task { await save() }
                }
                .disabled(title.trimmed.isEmpty || isSaving)
            }
            .padding(Spacing.xl)
        }
        .fcScreenBackground()
        .navigationTitle(isEditing ? "Modifier" : (kind == .idea ? "Proposer une idée" : "Ajouter un cadeau"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Annuler", action: onDone)
            }
        }
        .sheet(isPresented: $showAddLink) {
            AddLinkSheet(defaultCurrency: appState.profile?.currency ?? "EUR") { otherLinks.append($0) }
                .presentationDetents([.medium])
        }
        .onChange(of: photoItem) { _, newItem in
            Task {
                guard let data = try? await newItem?.loadTransferable(type: Data.self),
                      let image = UIImage(data: data) else { return }
                imageData = image.resizedJPEG(maxDimension: 1200)
                imageURL = nil
            }
        }
        .onAppear(perform: load)
    }

    // MARK: - Sections

    private var urlField: some View {
        HStack(spacing: Spacing.s) {
            FCTextField(title: "Lien du cadeau", text: $urlText, systemImage: "magnifyingglass",
                        prompt: "https://www.galaxus.ch/…", isTitleHidden: true)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onSubmit { fetchPreview() }
                .onChange(of: urlText) { _, _ in scheduleFetch() }
            // PasteButton : collage sans alerte d'autorisation iOS.
            PasteButton(payloadType: String.self) { strings in
                if let pasted = strings.first { urlText = pasted }
            }
            .labelStyle(.iconOnly)
            .buttonBorderShape(.capsule)
            .tint(Color.Theme.primary)
        }
    }

    private var previewCard: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            ZStack {
                if let imageData, let image = UIImage(data: imageData) {
                    Image(uiImage: image).resizable().scaledToFit()
                } else {
                    RemoteImage(url: imageURL, contentMode: .fit)
                }
                if isFetching { ProgressView() }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 200)
            .background(Color.Theme.background.opacity(0.5))
            .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
            .overlay(alignment: .bottomTrailing) {
                PhotosPicker(selection: $photoItem, matching: .images) {
                    Label("Photo", systemImage: "camera.fill")
                        .font(Font.Theme.captionBold)
                        .padding(.horizontal, Spacing.m)
                        .frame(minHeight: 36)
                        .background(.ultraThinMaterial, in: Capsule())
                }
                .padding(Spacing.s)
            }

            if previewFailed {
                Text("Aperçu indisponible pour ce lien : complète les informations à la main.")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.takenFg)
            }

            TextField("Nom du cadeau", text: $title, axis: .vertical)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textPrimary)

            HStack(spacing: Spacing.s) {
                if let store {
                    if let country = store.country { CountryFlag(code: country) }
                    Text(store.name)
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                Spacer()
                TextField("Prix", text: $priceText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 90)
                Picker("Devise", selection: $currency) {
                    ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
                }
                .labelsHidden()
            }
            .font(Font.Theme.body)
        }
        .fcCard()
    }

    private var optionsCard: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            if kind == .wish && !owned {
                Toggle(isOn: $isFavorite) {
                    Label("Très envie", systemImage: "heart.fill")
                        .foregroundStyle(Color.Theme.heart)
                }
            }
            if !owned {
                Picker("Pour", selection: $selectedEvent) {
                    Text("Toute l'année").tag(UUID?.none)
                    ForEach(appState.upcomingEvents.filter { $0.childId == nil || $0.childId == child.id }) { event in
                        Text(event.title).tag(Optional(event.id))
                    }
                }
            }
            TextField("Notes (taille, couleur…)", text: $notes, axis: .vertical)
                .lineLimit(1...4)
        }
        .font(Font.Theme.body)
        .fcCard()
    }

    private var otherLinksSection: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("Autres liens pour ce cadeau").font(Font.Theme.headline)
            Text("Ajoute le même cadeau dans d'autres boutiques ou pays (ex. Amazon.fr et Galaxus.ch).")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
            ForEach(otherLinks) { link in
                HStack {
                    StoreLinkRow(store: link.store ?? "Lien", countryCode: link.country,
                                 priceText: Money.format(link.price, currency: link.currency)) {}
                    Button(role: .destructive) {
                        otherLinks.removeAll { $0.id == link.id }
                    } label: {
                        Image(systemName: "minus.circle.fill").frame(width: 44, height: 44)
                    }
                    .accessibilityLabel("Retirer ce lien")
                }
            }
            TextLinkButton(title: "+ Ajouter un lien") { showAddLink = true }
        }
    }

    // MARK: - Aperçu

    private func scheduleFetch() {
        fetchTask?.cancel()
        let text = urlText
        // En modification, ne pas relancer l'aperçu pour le lien d'origine (titre et photo conservés).
        if isEditing && text == existingLinks.first?.url { return }
        fetchTask = Task {
            try? await Task.sleep(for: .milliseconds(700))
            guard !Task.isCancelled, text == urlText else { return }
            fetchPreview()
        }
    }

    private func fetchPreview() {
        guard let url = LinkPreviewService.normalizedURL(urlText), url.host()?.contains(".") == true else { return }
        store = StoreCatalog.store(for: url.absoluteString)
        if let storeCurrency = store?.currency { currency = storeCurrency }
        isFetching = true
        previewFailed = false
        Task {
            let preview = await LinkPreviewService.preview(for: url.absoluteString)
            isFetching = false
            guard let preview else {
                previewFailed = true
                return
            }
            if title.trimmed.isEmpty || !isEditing { title = preview.title ?? title }
            if let url = preview.imageURL {
                imageURL = url
                imageData = nil
            } else if let data = preview.imageData {
                imageData = UIImage(data: data)?.resizedJPEG(maxDimension: 1200) ?? data
            }
            if let price = preview.price { priceText = "\(price)" }
            if let previewCurrency = preview.currency { currency = previewCurrency }
        }
    }

    // MARK: - Chargement / enregistrement

    private func load() {
        currency = appState.profile?.currency ?? "CHF"
        selectedEvent = eventId
        guard let existing else { return }
        title = existing.title
        notes = existing.notes ?? ""
        imageURL = existing.imageURL
        isFavorite = existing.isFavorite
        selectedEvent = existing.eventId
        if let main = existingLinks.first {
            urlText = main.url
            store = StoreCatalog.Store(name: main.store ?? "", country: main.country, currency: main.currency)
            priceText = main.price.map { "\($0)" } ?? ""
            currency = main.currency ?? currency
        }
        otherLinks = existingLinks.dropFirst().map {
            DraftLink(url: $0.url, store: $0.store, country: $0.country, price: $0.price, currency: $0.currency)
        }
    }

    private var mainLink: DraftLink? {
        guard let url = LinkPreviewService.normalizedURL(urlText) else { return nil }
        let detected = store ?? StoreCatalog.store(for: url.absoluteString)
        return DraftLink(url: url.absoluteString, store: detected?.name, country: detected?.country,
                         price: LinkPreviewService.parsePrice(priceText), currency: currency)
    }

    private func save() async {
        guard let userId = appState.userId else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            var finalImage = imageURL?.absoluteString
            if let imageData {
                finalImage = try await appState.repository.uploadImage(imageData, userId: userId).absoluteString
            }
            let links = [mainLink].compactMap { $0 } + otherLinks
            let note = notes.trimmed.isEmpty ? nil : notes.trimmed
            if let existing {
                try await appState.repository.updateItem(id: existing.id, title: title.trimmed, notes: note,
                                                         imageUrl: finalImage, priority: isFavorite ? 1 : 0,
                                                         eventId: selectedEvent)
                try await appState.repository.replaceLinks(itemId: existing.id, links: links)
            } else {
                let item = GiftRepository.NewItem(child_id: child.id, event_id: owned ? nil : selectedEvent,
                                                  kind: kind.rawValue, title: title.trimmed, notes: note,
                                                  image_url: finalImage, priority: isFavorite ? 1 : 0,
                                                  owned: owned, created_by: userId)
                let newId = try await appState.repository.addItem(item, links: links)
                if !owned {
                    let repository = appState.repository
                    Task.detached { await repository.notifyNewItems([newId]) }
                }
            }
            appState.itemsChanged()
            onDone()
        } catch {
            appState.report(error)
        }
    }
}

/// Saisie d'un lien supplémentaire (autre boutique / autre pays).
private struct AddLinkSheet: View {
    let defaultCurrency: String
    let onAdd: (DraftLink) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    @State private var price = ""
    @State private var currency = "EUR"
    @State private var isFetching = false

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: Spacing.l) {
                FCTextField(title: "Lien", text: $url, systemImage: "link", prompt: "https://www.amazon.fr/…")
                    .keyboardType(.URL)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .onSubmit(fetch)
                HStack {
                    if let store = StoreCatalog.store(for: LinkPreviewService.normalizedURL(url)?.absoluteString ?? "") {
                        if let country = store.country { CountryFlag(code: country) }
                        Text(store.name).foregroundStyle(Color.Theme.textSecondary)
                    }
                    Spacer()
                    if isFetching { ProgressView() }
                    TextField("Prix", text: $price)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                        .frame(maxWidth: 90)
                    Picker("Devise", selection: $currency) {
                        ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
                    }
                    .labelsHidden()
                }
                .fcCard()
                PrimaryButton(title: "Ajouter ce lien", systemImage: "plus") {
                    guard let normalized = LinkPreviewService.normalizedURL(url) else { return }
                    let store = StoreCatalog.store(for: normalized.absoluteString)
                    onAdd(DraftLink(url: normalized.absoluteString, store: store?.name, country: store?.country,
                                    price: LinkPreviewService.parsePrice(price), currency: currency))
                    dismiss()
                }
                .disabled(LinkPreviewService.normalizedURL(url) == nil)
                Spacer()
            }
            .padding(Spacing.xl)
            .fcScreenBackground()
            .navigationTitle("Autre lien")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
            .onAppear { currency = defaultCurrency }
            .onChange(of: url) { _, _ in
                if let detected = StoreCatalog.store(for: url)?.currency { currency = detected }
            }
        }
    }

    private func fetch() {
        isFetching = true
        Task {
            let preview = await LinkPreviewService.preview(for: url)
            isFetching = false
            if let value = preview?.price { price = "\(value)" }
            if let value = preview?.currency { currency = value }
        }
    }
}

extension UIImage {
    /// Redimensionne et compresse une image avant envoi.
    func resizedJPEG(maxDimension: CGFloat, quality: CGFloat = 0.8) -> Data? {
        let scale = min(1, maxDimension / max(size.width, size.height))
        let target = CGSize(width: size.width * scale, height: size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: target)
        return renderer.image { _ in draw(in: CGRect(origin: .zero, size: target)) }.jpegData(compressionQuality: quality)
    }
}

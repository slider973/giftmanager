import Supabase
import SwiftUI

/// Formulaire de l'extension de partage : aperçu du lien, choix de l'enfant et de l'événement, enregistrement.
/// Mes enfants → souhait ; enfants des autres foyers → idée (invisible pour leurs parents, règle serveur).
struct ShareView: View {
    let sharedText: String
    let onClose: () -> Void

    private enum Phase { case loading, signedOut, ready, saved }
    @State private var phase: Phase = .loading
    @State private var errorMessage: String?

    @State private var userId: UUID?
    @State private var groups: [FamilyGroup] = []
    @State private var groupId: UUID?
    @State private var myHouseholdId: UUID?
    @State private var children: [Child] = []
    @State private var events: [GiftEvent] = []
    @State private var childId: UUID?
    @State private var eventId: UUID?

    @State private var link: URL?
    @State private var preview: LinkPreview?
    @State private var isFetching = false
    @State private var title = ""
    @State private var priceText = ""
    @State private var currency = "CHF"
    @State private var isSaving = false

    private let repository = GiftRepository()

    private var store: StoreCatalog.Store? { link.flatMap { StoreCatalog.store(for: $0.absoluteString) } }
    private var selectedChild: Child? { children.first { $0.id == childId } }
    private var isIdea: Bool { selectedChild.map { $0.householdId != myHouseholdId } ?? false }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .loading:
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                case .signedOut:
                    EmptyStateView(imageName: "mascot_thinking", title: "Connecte-toi d'abord",
                                   message: "Ouvre Gift Manager et connecte-toi avec Apple, puis partage à nouveau ce lien.")
                case .saved:
                    EmptyStateView(imageName: "mascot_celebrate", title: isIdea ? "Idée proposée !" : "Ajouté à la liste !",
                                   message: "Tu le retrouveras dans Gift Manager.")
                case .ready:
                    form
                }
            }
            .fcScreenBackground()
            .navigationTitle("Gift Manager")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(phase == .saved ? "Fermer" : "Annuler", action: onClose)
                }
            }
            .alert("Oups", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(errorMessage ?? "")
            }
        }
        .task { await load() }
    }

    private var form: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    ZStack {
                        if let data = preview?.imageData, let image = UIImage(data: data) {
                            Image(uiImage: image).resizable().scaledToFit()
                        } else {
                            RemoteImage(url: preview?.imageURL, contentMode: .fit)
                        }
                        if isFetching { ProgressView() }
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: 160)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))

                    TextField("Nom du cadeau", text: $title, axis: .vertical)
                        .font(Font.Theme.headline)
                    HStack {
                        if let store {
                            if let country = store.country { CountryFlag(code: country) }
                            Text(store.name).font(Font.Theme.caption).foregroundStyle(Color.Theme.textSecondary)
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
                }
                .fcCard()

                VStack(alignment: .leading, spacing: Spacing.m) {
                    if groups.count > 1 {
                        Picker("Famille", selection: $groupId) {
                            ForEach(groups) { Text($0.name).tag(Optional($0.id)) }
                        }
                        .onChange(of: groupId) { _, _ in Task { await loadGroup() } }
                    }
                    Picker("Pour", selection: $childId) {
                        ForEach(children) { child in
                            Text(child.householdId == myHouseholdId ? child.firstName : "\(child.firstName) (idée)")
                                .tag(Optional(child.id))
                        }
                    }
                    Picker("Événement", selection: $eventId) {
                        Text("Toute l'année").tag(UUID?.none)
                        ForEach(events.filter { $0.childId == nil || $0.childId == childId }) { Text($0.title).tag(Optional($0.id)) }
                    }
                    if isIdea {
                        FCNotice(systemImage: "eye.slash",
                                 text: "Idée : invisible pour les parents de \(selectedChild?.firstName ?? "l'enfant").")
                    }
                }
                .font(Font.Theme.body)
                .fcCard()

                PrimaryButton(title: isIdea ? "Proposer cette idée" : "Ajouter à la liste", systemImage: "checkmark",
                              isLoading: isSaving) {
                    Task { await save() }
                }
                .disabled(title.trimmed.isEmpty || childId == nil || isSaving)
            }
            .padding(Spacing.l)
        }
    }

    // MARK: - Données

    private func load() async {
        guard let session = try? await repository.client.auth.session else {
            phase = .signedOut
            return
        }
        userId = session.user.id
        link = LinkPreviewService.normalizedURL(sharedText)
        if let profile = try? await repository.myProfile(userId: session.user.id) { currency = profile.currency }
        if let storeCurrency = store?.currency { currency = storeCurrency }
        do {
            groups = try await repository.myGroups()
            let saved = UserDefaults.standard.string(forKey: "share.groupId").flatMap(UUID.init(uuidString:))
            groupId = groups.first { $0.id == saved }?.id ?? groups.first?.id
            await loadGroup()
            phase = .ready
        } catch {
            errorMessage = GiftError(error).localizedDescription
            phase = .ready
        }
        await fetchPreview()
    }

    private func loadGroup() async {
        guard let groupId, let userId else { return }
        UserDefaults.standard.set(groupId.uuidString, forKey: "share.groupId")
        let members = (try? await repository.householdMembers(groupId: groupId)) ?? []
        myHouseholdId = members.first { $0.userId == userId }?.householdId
        let all = (try? await repository.children(groupId: groupId)) ?? []
        children = all.filter { $0.householdId == myHouseholdId } + all.filter { $0.householdId != myHouseholdId }
        events = ((try? await repository.events(groupId: groupId)) ?? []).filter { !$0.isPast }
        childId = children.first?.id
        eventId = events.first { $0.kind == .christmas }?.id ?? events.first?.id
    }

    private func fetchPreview() async {
        guard let link else { return }
        isFetching = true
        preview = await LinkPreviewService.preview(for: link.absoluteString)
        isFetching = false
        if title.isEmpty { title = preview?.title ?? "" }
        if let price = preview?.price {
            priceText = String(format: "%.2f", NSDecimalNumber(decimal: price).doubleValue)
        }
        if let previewCurrency = preview?.currency { currency = previewCurrency }
    }

    private func save() async {
        guard let userId, let childId else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            var image = preview?.imageURL?.absoluteString
            if image == nil, let data = preview?.imageData {
                image = try await repository.uploadImage(data, userId: userId).absoluteString
            }
            let item = GiftRepository.NewItem(child_id: childId, event_id: eventId, kind: isIdea ? "idea" : "wish",
                                              title: title.trimmed, notes: nil, image_url: image, priority: 0,
                                              owned: false, created_by: userId)
            let links = link.map {
                [DraftLink(url: $0.absoluteString, store: store?.name, country: store?.country,
                           price: LinkPreviewService.parsePrice(priceText), currency: currency)]
            } ?? []
            let newId = try await repository.addItem(item, links: links)
            await repository.notifyNewItems([newId])
            phase = .saved
            try? await Task.sleep(for: .seconds(1.2))
            onClose()
        } catch {
            errorMessage = GiftError(error).localizedDescription
        }
    }
}

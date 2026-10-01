import SwiftUI

/// Fiche cadeau (écran 5 de la maquette) : liens par pays, réservation anonyme, actions des parents (#9, #11).
struct GiftDetailView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.openURL) private var openURL
    @Environment(\.dismiss) private var dismiss

    @State var item: WishItem
    let model: GiftListModel
    var readOnly = false
    @State private var isWorking = false
    @State private var editing = false
    @State private var confirmDelete = false
    @State private var celebrate = false

    private var child: Child { model.child }
    private var isParent: Bool { appState.isParent(of: child) }
    private var links: [ItemLink] { model.links(for: item, preferredCountry: appState.profile?.country) }
    private var canEdit: Bool { !readOnly && (item.kind == .wish ? isParent : item.createdBy == appState.userId) }
    /// Un parent peut offrir lui-même un cadeau de la liste de son enfant (action discrète, dans le menu).
    private var canParentReserve: Bool { !readOnly && isParent && item.kind == .wish && !item.owned && item.myReservation == nil }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                ZStack(alignment: .topTrailing) {
                    RemoteImage(url: item.imageURL, contentMode: .fit, placeholderSeed: item.title)
                        .frame(maxWidth: .infinity)
                        // Sans photo, un bandeau plus bas : pas de grand aplat vide en tête de fiche.
                        .frame(height: item.imageURL == nil ? 180 : 280)
                        .background(Color.Theme.surface)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
                    if item.kind == .wish {
                        // Pastille blanche derrière le cœur : lisible sur n'importe quelle photo.
                        PriorityHeart(isOn: item.isFavorite, action: isParent && !item.owned && !readOnly ? toggleFavorite : nil)
                            .background(Color.Theme.surface, in: Circle())
                            .padding(Spacing.m)
                    }
                }

                VStack(alignment: .leading, spacing: Spacing.s) {
                    if item.kind == .idea {
                        Label("Idée proposée — invisible pour les parents", systemImage: "lightbulb")
                            .font(Font.Theme.captionBold)
                            .foregroundStyle(Color.Theme.accentAmber)
                    }
                    Text(item.title)
                        .font(Font.Theme.title)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    if let status = item.displayStatus(isParent: isParent) {
                        StatusBadge(status: status)
                    }
                    if let notes = item.notes, !notes.isEmpty {
                        Text(notes)
                            .font(Font.Theme.body)
                            .foregroundStyle(Color.Theme.textSecondary)
                            .padding(.top, Spacing.xs)
                    }
                }

                if !links.isEmpty {
                    VStack(alignment: .leading, spacing: Spacing.s) {
                        Text("Liens par pays")
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                            .accessibilityAddTraits(.isHeader)
                        ForEach(links) { link in
                            StoreLinkRow(store: link.store ?? StoreCatalog.store(for: link.url)?.name ?? "Lien",
                                         countryCode: link.country, priceText: link.priceText) {
                                if let url = URL(string: link.url) { openURL(url) }
                            }
                        }
                    }
                }

                if readOnly {
                    FCNotice(systemImage: "archivebox", text: "Événement passé : fiche archivée.")
                } else {
                    VStack(alignment: .leading, spacing: Spacing.m) {
                        actions
                    }
                }
            }
            .padding(Spacing.xl)
        }
        .fcScreenBackground()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if canEdit {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        if canParentReserve {
                            Button("Je l'offre moi-même", systemImage: "gift") { Task { await reserve() } }
                        }
                        Button("Modifier", systemImage: "pencil") { editing = true }
                        if isParent && item.kind == .wish {
                            Button(item.owned ? "Remettre dans la liste" : "Il l'a déjà", systemImage: "checkmark.seal") {
                                Task { await toggleOwned() }
                            }
                        }
                        Button("Supprimer", systemImage: "trash", role: .destructive) { confirmDelete = true }
                    } label: {
                        Image(systemName: "ellipsis.circle").accessibilityLabel("Actions")
                    }
                }
            }
        }
        .sheet(isPresented: $editing) {
            NavigationStack {
                AddGiftView(child: child, kind: item.kind, eventId: item.eventId, existing: item,
                            existingLinks: model.links[item.id] ?? []) {
                    editing = false
                    appState.itemsChanged()
                    dismiss()
                }
            }
        }
        .confirmationDialog("Supprimer « \(item.title) » ?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) { Task { await delete() } }
        }
        .overlay {
            if celebrate {
                CelebrationOverlay { celebrate = false }
            }
        }
    }

    // MARK: - Actions selon le rôle

    @ViewBuilder
    private var actions: some View {
        if isParent {
            parentActions
        } else {
            switch item.status {
            case .available:
                SecondaryButton(title: "Je l'offre", systemImage: "gift.fill", isLoading: isWorking) {
                    Task { await reserve() }
                }
                Label("Personne ne saura que c'est toi, et les parents ne verront rien.", systemImage: "lock.fill")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .multilineTextAlignment(.center)
            case .mine:
                mineActions
            case .taken:
                FCNotice(systemImage: "hand.raised", text: "Quelqu'un de la famille s'en occupe déjà.")
            case .owned:
                FCNotice(systemImage: "checkmark.seal", text: "\(child.firstName) l'a déjà.")
            case nil:
                EmptyView()
            }
        }
    }

    @ViewBuilder
    private var mineActions: some View {
        if item.myReservation == .purchased {
            Label("Acheté — bravo !", systemImage: "checkmark.circle.fill")
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.availableFg)
                .padding(.horizontal, Spacing.l)
                .frame(maxWidth: .infinity, minHeight: HitTarget.button)
                .background(Color.Theme.availableBg, in: Capsule())
            TextLinkButton(title: "Pas encore acheté finalement") { Task { await setPurchased(false) } }
                .frame(maxWidth: .infinity)
        } else {
            PrimaryButton(title: "C'est acheté", systemImage: "bag.fill", isLoading: isWorking) {
                Task { await setPurchased(true) }
            }
        }
        TextLinkButton(title: "Annuler ma réservation") { Task { await cancel() } }
            .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var parentActions: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            FCNotice(systemImage: "eye.slash", text: "Mode surprise : tu ne vois pas si ce cadeau est réservé.",
                     tone: .surprise)
            if item.kind == .wish && !item.owned {
                if item.myReservation != nil {
                    Label("Tu l'offres toi-même", systemImage: "gift")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.mineFg)
                    if item.myReservation != .purchased {
                        TextLinkButton(title: "Marquer comme acheté") { Task { await setPurchased(true) } }
                    }
                    TextLinkButton(title: "Annuler ma réservation") { Task { await cancel() } }
                }
            }
        }
    }

    // MARK: - Appels

    private func reserve() async {
        await perform {
            try await appState.repository.reserve(itemId: item.id)
            item.status = isParent ? nil : .mine
            item.myReservation = .reserved
            celebrate = true
        }
    }

    private func cancel() async {
        await perform {
            try await appState.repository.cancelReservation(itemId: item.id)
            item.status = isParent ? nil : .available
            item.myReservation = nil
        }
    }

    private func setPurchased(_ purchased: Bool) async {
        await perform {
            try await appState.repository.setPurchased(itemId: item.id, purchased: purchased)
            item.myReservation = purchased ? .purchased : .reserved
        }
    }

    private func toggleFavorite() {
        Task {
            await perform {
                try await appState.repository.setPriority(itemId: item.id, favorite: !item.isFavorite)
                item.priority = item.isFavorite ? 0 : 1
            }
        }
    }

    private func toggleOwned() async {
        await perform {
            try await appState.repository.setOwned(itemId: item.id, owned: !item.owned)
            item.owned.toggle()
        }
    }

    private func delete() async {
        await perform {
            try await appState.repository.deleteItem(item.id)
            model.remove(item.id)
            dismiss()
        }
    }

    private func perform(_ action: () async throws -> Void) async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await action()
            model.update(item)
            appState.itemsChanged()
        } catch {
            appState.report(error)
            if case .unavailable = GiftError(error) { item.status = isParent ? nil : .taken }
        }
    }
}

/// Petite célébration après une réservation.
private struct CelebrationOverlay: View {
    let onFinish: () -> Void
    @State private var appear = false

    var body: some View {
        VStack(spacing: Spacing.m) {
            Image("mascot_celebrate")
                .resizable()
                .scaledToFit()
                .frame(width: 180)
                .accessibilityHidden(true)
            Text("Réservé !")
                .font(Font.Theme.title)
                .foregroundStyle(Color.Theme.textPrimary)
            Text("Ton secret est bien gardé.")
                .font(Font.Theme.body)
                .foregroundStyle(Color.Theme.textSecondary)
        }
        .padding(Spacing.xxl)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        .scaleEffect(appear ? 1 : 0.8)
        .opacity(appear ? 1 : 0)
        .onAppear {
            withAnimation(.spring(duration: 0.4)) { appear = true }
        }
        .task {
            try? await Task.sleep(for: .seconds(1.8))
            withAnimation { appear = false }
            try? await Task.sleep(for: .seconds(0.3))
            onFinish()
        }
        .onTapGesture(perform: onFinish)
        .accessibilityElement(children: .combine)
    }
}

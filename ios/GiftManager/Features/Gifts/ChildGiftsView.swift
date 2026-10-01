import SwiftUI

/// Liste d'un enfant (écran 3 de la maquette) : Liste · Possède déjà · Idées (#9, #10, #17).
struct ChildGiftsView: View {
    @Environment(AppState.self) private var appState
    @State private var model: GiftListModel
    @State private var tab = 0
    @State private var adding: AddMode?
    @State private var isReordering = false

    init(child: Child, eventId: UUID?) {
        _model = State(initialValue: GiftListModel(child: child, eventId: eventId))
    }

    private var child: Child { model.child }
    private var isParent: Bool { appState.isParent(of: child) }

    struct AddMode: Identifiable {
        let kind: WishKind
        let owned: Bool
        var id: String { "\(kind.rawValue)-\(owned)" }
    }

    private var tabs: [String] {
        var titles = ["Liste (\(model.wishes.count))", "Possède déjà (\(model.owned.count))"]
        if !isParent { titles.append("Idées (\(model.ideas.count))") }
        return titles
    }

    private var visibleItems: [WishItem] {
        switch tab {
        case 0: model.wishes
        case 1: model.owned
        default: model.ideas
        }
    }

    var body: some View {
        List {
            Section {
                header
                    .listRowInsets(EdgeInsets(top: Spacing.s, leading: Spacing.xl, bottom: Spacing.s, trailing: Spacing.xl))
                SegmentedTabs(selection: $tab, titles: tabs)
                    .listRowInsets(EdgeInsets(top: 0, leading: Spacing.xl, bottom: Spacing.s, trailing: Spacing.xl))
            }
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            Section {
                if model.hasLoaded && visibleItems.isEmpty {
                    emptyState
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                ForEach(visibleItems) { item in
                    // Lien invisible sous la carte : évite le chevron système qui déborde de la carte.
                    ZStack {
                        NavigationLink {
                            GiftDetailView(item: item, model: model)
                        } label: {
                            EmptyView()
                        }
                        .opacity(0)
                        card(for: item)
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(EdgeInsets(top: Spacing.xs, leading: Spacing.xl, bottom: Spacing.xs, trailing: Spacing.xl))
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) { trailingActions(item) }
                    .swipeActions(edge: .leading) { leadingActions(item) }
                }
                .onMove(perform: isParent && tab == 0 ? move : nil)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .fcScreenBackground()
        .environment(\.editMode, .constant(isReordering ? .active : .inactive))
        .navigationTitle(child.firstName)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbar }
        .refreshable { await load() }
        .task(id: appState.itemsRevision) { await load() }
        .sheet(item: $adding) { mode in
            NavigationStack {
                AddGiftView(child: child, kind: mode.kind, eventId: model.eventId, owned: mode.owned) { adding = nil }
            }
        }
        .overlay { if model.isLoading && !model.hasLoaded { ProgressView() } }
    }

    // MARK: - En-tête

    private var header: some View {
        HStack(spacing: Spacing.m) {
            ChildAvatar(name: child.firstName, emoji: child.avatarEmoji, colorName: child.avatarColor, size: 56)
            VStack(alignment: .leading, spacing: 2) {
                Text(child.firstName)
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                Text([Formatting.ageText(child.age), isParent ? "Ses envies" : appState.household(of: child)?.name]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            Spacer()
        }
        .accessibilityElement(children: .combine)
    }

    private func card(for item: WishItem) -> some View {
        let link = model.links(for: item, preferredCountry: appState.profile?.country).first
        return GiftCard(title: item.title, imageURL: item.imageURL, priceText: link?.priceText,
                        storeText: link?.store, countryCode: link?.country, isFavorite: item.isFavorite,
                        status: item.displayStatus(isParent: isParent))
    }

    @ViewBuilder
    private var emptyState: some View {
        switch tab {
        case 0:
            EmptyStateView(imageName: "empty_box", title: "Liste vide",
                           message: isParent ? "Ajoute ses envies avec \(child.firstName) en collant des liens de boutiques."
                                             : "\(child.firstName) n'a pas encore fait sa liste.",
                           actionTitle: isParent ? "Ajouter un cadeau" : nil) {
                adding = AddMode(kind: .wish, owned: false)
            }
        case 1:
            EmptyStateView(imageName: "mascot_gift", title: "Rien pour l'instant",
                           message: isParent ? "Note ce que \(child.firstName) a déjà pour éviter les doublons."
                                             : "Les parents n'ont rien indiqué.",
                           actionTitle: isParent ? "Ajouter" : nil) {
                adding = AddMode(kind: .wish, owned: true)
            }
        default:
            EmptyStateView(imageName: "mascot_thinking", title: "Aucune idée",
                           message: "Propose une idée de cadeau : ses parents ne la verront pas.",
                           actionTitle: "Proposer une idée") {
                adding = AddMode(kind: .idea, owned: false)
            }
        }
    }

    // MARK: - Actions

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItemGroup(placement: .topBarTrailing) {
            if isParent && tab == 0 && model.wishes.count > 1 {
                Button(isReordering ? "OK" : "Ordonner") { withAnimation { isReordering.toggle() } }
            }
            if isParent && tab < 2 {
                Button {
                    adding = AddMode(kind: .wish, owned: tab == 1)
                } label: {
                    Image(systemName: "plus.circle.fill").font(.title3)
                }
                .accessibilityLabel(tab == 1 ? "Ajouter un cadeau déjà possédé" : "Ajouter un cadeau")
            } else if !isParent && tab == 2 {
                Button {
                    adding = AddMode(kind: .idea, owned: false)
                } label: {
                    Image(systemName: "lightbulb.circle.fill").font(.title3)
                }
                .accessibilityLabel("Proposer une idée")
            }
        }
    }

    @ViewBuilder
    private func trailingActions(_ item: WishItem) -> some View {
        if canEdit(item) {
            Button(role: .destructive) {
                Task { await run { try await appState.repository.deleteItem(item.id); model.remove(item.id) } }
            } label: {
                Label("Supprimer", systemImage: "trash")
            }
        }
        if isParent && item.kind == .wish {
            Button {
                Task {
                    await run {
                        try await appState.repository.setOwned(itemId: item.id, owned: !item.owned)
                        var copy = item
                        copy.owned.toggle()
                        model.update(copy)
                    }
                }
            } label: {
                Label(item.owned ? "Remettre dans la liste" : "Il l'a déjà", systemImage: item.owned ? "arrow.uturn.left" : "checkmark.seal")
            }
            .tint(Color.Theme.secondary)
        }
    }

    @ViewBuilder
    private func leadingActions(_ item: WishItem) -> some View {
        if isParent && item.kind == .wish && !item.owned {
            Button {
                Task {
                    await run {
                        try await appState.repository.setPriority(itemId: item.id, favorite: !item.isFavorite)
                        var copy = item
                        copy.priority = item.isFavorite ? 0 : 1
                        model.update(copy)
                    }
                }
            } label: {
                Label(item.isFavorite ? "Retirer le cœur" : "Très envie", systemImage: item.isFavorite ? "heart.slash" : "heart.fill")
            }
            .tint(Color.Theme.heart)
        }
    }

    private func canEdit(_ item: WishItem) -> Bool {
        item.kind == .wish ? isParent : item.createdBy == appState.userId
    }

    private func move(from source: IndexSet, to destination: Int) {
        let ids = model.move(wishesFrom: source, to: destination)
        Task { await run { try await appState.repository.reorder(itemIds: ids) } }
    }

    private func load() async {
        await run { try await model.load(appState.repository) }
    }

    private func run(_ action: () async throws -> Void) async {
        do {
            try await action()
        } catch {
            appState.report(error)
        }
    }
}

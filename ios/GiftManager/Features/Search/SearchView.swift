import SwiftUI

/// Recherche dans les listes de la famille, avec filtres « disponibles » et pays de boutique (#11).
struct SearchView: View {
    @Environment(AppState.self) private var appState
    @State private var query = ""
    @State private var onlyAvailable = false
    @State private var country: String?
    @State private var models: [GiftListModel] = []
    @State private var hasLoaded = false

    private struct Result: Identifiable {
        let item: WishItem
        let model: GiftListModel
        var id: UUID { item.id }
    }

    private var results: [Result] {
        let text = query.trimmed.lowercased()
        return models.flatMap { model in
            let isParent = appState.isParent(of: model.child)
            return model.items.compactMap { item -> Result? in
                if item.owned { return nil }
                if !text.isEmpty && !item.title.lowercased().contains(text)
                    && !model.child.firstName.lowercased().contains(text) { return nil }
                if onlyAvailable && (isParent || item.status != .available) { return nil }
                if let country, !(model.links[item.id] ?? []).contains(where: { $0.country == country }) { return nil }
                return Result(item: item, model: model)
            }
        }
    }

    private var countries: [String] {
        let all = models.flatMap { $0.links.values.flatMap { $0 } }.compactMap(\.country)
        return Array(Set(all)).sorted()
    }

    var body: some View {
        NavigationStack {
            List {
                filters
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                if hasLoaded && results.isEmpty {
                    EmptyStateView(imageName: "mascot_thinking", title: "Aucun résultat",
                                   message: "Essaie un autre mot ou retire les filtres.")
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                ForEach(results) { result in
                    ZStack {
                        NavigationLink {
                            GiftDetailView(item: result.item, model: result.model)
                        } label: {
                            EmptyView()
                        }
                        .opacity(0)
                        VStack(alignment: .leading, spacing: Spacing.xs) {
                            Text(result.model.child.firstName)
                                .font(Font.Theme.captionBold)
                                .foregroundStyle(Color.Theme.textSecondary)
                            card(result)
                        }
                    }
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .navigationTitle("Recherche")
            .searchable(text: $query, prompt: "Cadeau, enfant…")
            .refreshable { await load() }
            .task(id: appState.itemsRevision) { await load() }
            .overlay { if !hasLoaded { ProgressView() } }
        }
    }

    private var filters: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s) {
                chip("Disponibles", isOn: onlyAvailable) { onlyAvailable.toggle() }
                chip("Tous pays", isOn: country == nil) { country = nil }
                ForEach(countries, id: \.self) { code in
                    chip("\(Countries.all.first { $0.code == code }?.flag ?? "") \(Countries.name(code))", isOn: country == code) {
                        country = country == code ? nil : code
                    }
                }
            }
        }
    }

    private func chip(_ title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(Font.Theme.captionBold)
                .padding(.horizontal, Spacing.m)
                .frame(minHeight: 36)
                .background(isOn ? Color.Theme.primary : Color.Theme.surface, in: Capsule())
                .foregroundStyle(isOn ? Color.Theme.onPrimary : Color.Theme.textPrimary)
        }
        .buttonStyle(.plain)
        .frame(minHeight: 44)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }

    private func card(_ result: Result) -> some View {
        let link = result.model.links(for: result.item, preferredCountry: appState.profile?.country).first
        return GiftCard(title: result.item.title, imageURL: result.item.imageURL, priceText: link?.priceText,
                        storeText: link?.store, countryCode: link?.country, isFavorite: result.item.isFavorite,
                        status: result.item.displayStatus(isParent: appState.isParent(of: result.model.child)))
    }

    private func load() async {
        let eventId = appState.currentEvent?.id
        var loaded: [GiftListModel] = []
        for child in appState.children {
            let model = GiftListModel(child: child, eventId: eventId)
            do {
                try await model.load(appState.repository)
                loaded.append(model)
            } catch {
                appState.report(error)
            }
        }
        models = loaded
        hasLoaded = true
    }
}

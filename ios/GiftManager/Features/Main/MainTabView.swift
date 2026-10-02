import SwiftUI

/// Barre d'onglets : Accueil · Recherche · + · Mes achats · Profil.
/// (La maquette prévoit « Notifications » ; en v1 cet emplacement accueille « Mes achats », cf. DESIGN.md.)
struct MainTabView: View {
    @Environment(AppState.self) private var appState

    private enum Tab: Hashable { case home, search, add, purchases, profile }
    @State private var selection: Tab = .home
    @State private var lastTab: Tab = .home
    @State private var showAddGift = false
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        TabView(selection: $selection) {
            FamilyHomeView()
                .tabItem { Label("Accueil", systemImage: "house.fill") }
                .tag(Tab.home)
            SearchView()
                .tabItem { Label("Recherche", systemImage: "magnifyingglass") }
                .tag(Tab.search)
            Color.clear
                .tabItem {
                    Label {
                        Text("Ajouter")
                    } icon: {
                        Image(uiImage: colorScheme == .dark ? Self.addTabImageDark : Self.addTabImageLight)
                    }
                }
                .tag(Tab.add)
            MyPurchasesView()
                .tabItem { Label("Mes achats", systemImage: "bag.fill") }
                .tag(Tab.purchases)
            ProfileView()
                .tabItem { Label("Profil", systemImage: "person.crop.circle") }
                .tag(Tab.profile)
        }
        .onChange(of: selection) { old, new in
            if new == .add {
                selection = old
                showAddGift = true
            }
        }
        .tint(Color.Theme.primary)
        .sheet(isPresented: $showAddGift) {
            QuickAddGiftView()
        }
        .alert("Rejoindre une famille ?", isPresented: Binding(
            get: { appState.pendingInviteCode != nil },
            set: { if !$0 { appState.pendingInviteCode = nil } }
        )) {
            Button("Rejoindre") {
                let code = appState.pendingInviteCode ?? ""
                appState.pendingInviteCode = nil
                Task { if await appState.joinGroup(code: code) != nil { await appState.refreshAll() } }
            }
            Button("Annuler", role: .cancel) { appState.pendingInviteCode = nil }
        } message: {
            Text("Tu as ouvert une invitation avec le code \(appState.pendingInviteCode ?? ""). Tu pourras basculer entre tes familles dans Paramètres.")
        }
    }

    /// « + » central de la maquette : disque `primary` plein, croix `onPrimary`.
    ///
    /// La barre d'onglets ignore les palettes de SF Symbols (elle affiche la variante
    /// multicolore, verte) : on dessine donc le disque soi-même, une image par apparence.
    private static let addTabImageLight = makeAddTabImage(.light)
    private static let addTabImageDark = makeAddTabImage(.dark)

    private static func makeAddTabImage(_ style: UIUserInterfaceStyle) -> UIImage {
        let traits = UITraitCollection(userInterfaceStyle: style)
        let primary = (UIColor(named: "Theme/primary") ?? .label).resolvedColor(with: traits)
        let onPrimary = (UIColor(named: "Theme/onPrimary") ?? .systemBackground).resolvedColor(with: traits)
        let side: CGFloat = 30
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side)).image { _ in
            primary.setFill()
            UIBezierPath(ovalIn: CGRect(x: 0, y: 0, width: side, height: side)).fill()
            let plus = UIImage(systemName: "plus",
                               withConfiguration: UIImage.SymbolConfiguration(pointSize: 14, weight: .bold))?
                .withTintColor(onPrimary, renderingMode: .alwaysOriginal)
            if let plus {
                plus.draw(at: CGPoint(x: (side - plus.size.width) / 2, y: (side - plus.size.height) / 2))
            }
        }
        return image.withRenderingMode(.alwaysOriginal)
    }
}

/// Bouton « + » : choisir l'enfant puis ajouter un souhait (ses enfants) ou une idée (les autres).
struct QuickAddGiftView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if !appState.myChildren.isEmpty {
                    Section("Ajouter une envie pour…") {
                        ForEach(appState.myChildren) { child in
                            NavigationLink(value: AddTarget(child: child, kind: .wish)) {
                                ChildRow(child: child)
                            }
                            .fcListRow()
                        }
                    }
                }
                if !appState.otherChildren.isEmpty {
                    Section {
                        ForEach(appState.otherChildren) { child in
                            NavigationLink(value: AddTarget(child: child, kind: .idea)) {
                                ChildRow(child: child)
                            }
                            .fcListRow()
                        }
                    } header: {
                        Text("Proposer une idée pour…")
                    } footer: {
                        Label("Les idées restent invisibles pour les parents de l'enfant.", systemImage: "eye.slash")
                    }
                }
                if appState.children.isEmpty {
                    EmptyStateView(imageName: "empty_box", title: "Aucun enfant",
                                   message: "Ajoute d'abord tes enfants dans Famille › Membres.")
                        .listRowBackground(Color.clear)
                }
            }
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .navigationTitle("Ajouter un cadeau")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: AddTarget.self) { target in
                AddGiftView(child: target.child, kind: target.kind, eventId: appState.currentEvent?.id) {
                    dismiss()
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
    }

    struct AddTarget: Hashable {
        let child: Child
        let kind: WishKind
    }
}

struct ChildRow: View {
    @Environment(AppState.self) private var appState
    let child: Child

    var body: some View {
        HStack(spacing: Spacing.m) {
            ChildAvatar(name: child.firstName, emoji: child.avatarEmoji, colorName: child.avatarColor, size: 40)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(child.firstName)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                Text([Formatting.ageText(child.age), appState.household(of: child)?.name]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
    }
}

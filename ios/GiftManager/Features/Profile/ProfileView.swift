import SwiftUI

/// Profil : prénom, pays, devise, déconnexion et suppression de compte (#5).
///
/// Liste groupée native (contenu de type « Réglages ») : le grand titre se replie
/// proprement au défilement, sans que l'en-tête ne passe sous la barre de navigation.
struct ProfileView: View {
    @Environment(AppState.self) private var appState
    @State private var name = ""
    @State private var country = "CH"
    @State private var currency = "CHF"
    @State private var isSaving = false
    @State private var confirmDelete = false
    @State private var purchaseReminders = NotificationService.isEnabled(.purchaseReminders)
    @State private var familyNews = NotificationService.isEnabled(.familyNews)

    private var hasChanges: Bool {
        guard let profile = appState.profile else { return false }
        return name.trimmed != profile.displayName || country != profile.country || currency != profile.currency
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    header
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets(top: Spacing.s, leading: 0, bottom: Spacing.s, trailing: 0))
                }

                identitySection
                notificationsSection

                Section {
                    FCNotice(systemImage: "eye.slash",
                             text: "Mode surprise : tu ne vois jamais ce qui a été réservé pour tes enfants, ni les idées proposées pour eux. Personne ne sait qui offre quoi.",
                             tone: .surprise)
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                }

                Section {
                    Button("Se déconnecter") { Task { await appState.signOut() } }
                        .foregroundStyle(Color.Theme.primary)
                        .frame(minHeight: HitTarget.minimum)
                        .fcListRow()
                    #if DEBUG
                    NavigationLink("Galerie du design system (dev)") { DesignSystemGallery() }
                        .foregroundStyle(Color.Theme.textPrimary)
                        .frame(minHeight: HitTarget.minimum)
                        .fcListRow()
                    #endif
                }

                Section {
                    // `takenFg` plutôt que le rouge système (3,5:1 sur blanc, sous le seuil AA).
                    Button("Supprimer mon compte", role: .destructive) { confirmDelete = true }
                        .foregroundStyle(Color.Theme.takenFg)
                        .frame(minHeight: HitTarget.minimum)
                        .fcListRow()
                } footer: {
                    Text(versionText)
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.l)
                }
            }
            .listStyle(.insetGrouped)
            .font(Font.Theme.callout)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .fcScreenBackground()
            .navigationTitle("Profil")
            .onAppear {
                if let profile = appState.profile {
                    name = profile.displayName
                    country = profile.country
                    currency = profile.currency
                }
            }
            .confirmationDialog("Supprimer ton compte ?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Supprimer définitivement", role: .destructive) { Task { await appState.deleteAccount() } }
            } message: {
                Text("Ton profil et tes réservations seront supprimés. Les listes de tes enfants restent accessibles à l'autre parent s'il y en a un.")
            }
        }
    }

    // MARK: - Sections

    private var header: some View {
        HStack(spacing: Spacing.l) {
            ChildAvatar(name: name.isEmpty ? "?" : name, emoji: nil, colorName: nil, size: 64)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(appState.profile?.displayName ?? "")
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                if let household = appState.myHousehold {
                    HStack(spacing: Spacing.xs) {
                        Image(systemName: "house.fill").accessibilityHidden(true)
                        Text(household.name)
                    }
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                }
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    private var identitySection: some View {
        Section {
            LabeledContent {
                TextField("Prénom", text: $name, prompt: Text("Ex. Jonathan").foregroundStyle(Color.Theme.textSecondary))
                    .textContentType(.givenName)
                    .multilineTextAlignment(.trailing)
                    .foregroundStyle(Color.Theme.textPrimary)
            } label: {
                Label("Prénom", systemImage: "person")
            }
            .frame(minHeight: HitTarget.minimum)
            .fcListRow()

            Picker(selection: ProfileFields.countryBinding(country: $country, currency: $currency)) {
                ForEach(Countries.all) { country in
                    Text("\(country.flag) \(country.name)").tag(country.code)
                }
            } label: {
                Label("Pays", systemImage: "globe.europe.africa")
            }
            .frame(minHeight: HitTarget.minimum)
            .fcListRow()

            Picker(selection: $currency) {
                ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
            } label: {
                Label("Devise", systemImage: "banknote")
            }
            .frame(minHeight: HitTarget.minimum)
            .fcListRow()

            if hasChanges {
                PrimaryButton(title: "Enregistrer", systemImage: "checkmark", isLoading: isSaving) {
                    Task {
                        isSaving = true
                        await appState.saveProfile(name: name.trimmed, country: country, currency: currency)
                        isSaving = false
                    }
                }
                .disabled(name.trimmed.isEmpty || isSaving)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: Spacing.m, leading: 0, bottom: 0, trailing: 0))
                .transition(.opacity)
            }
        } header: {
            Text("Mon profil")
        } footer: {
            Text("Ton prénom est visible par ta famille. Ton pays sert à te montrer d'abord les boutiques près de chez toi.")
        }
        .tint(Color.Theme.primary)
        .labelStyle(SettingsLabelStyle())
        .animation(.easeOut(duration: 0.2), value: hasChanges)
    }

    private var notificationsSection: some View {
        Section {
            Toggle(isOn: $purchaseReminders) {
                Label("Rappels d'achats (J-30, J-7)", systemImage: "calendar.badge.clock")
            }
            .frame(minHeight: HitTarget.minimum)
            .fcListRow()
            Toggle(isOn: $familyNews) {
                Label("Nouvelles envies dans la famille", systemImage: "sparkles")
            }
            .frame(minHeight: HitTarget.minimum)
            .fcListRow()
        } header: {
            Text("Notifications")
        } footer: {
            Text("Aucune notification ne dit qui a réservé quoi.")
        }
        .tint(Color.Theme.primary)
        .labelStyle(SettingsLabelStyle())
        .onChange(of: purchaseReminders) { _, value in
            NotificationService.set(.purchaseReminders, value)
            Task { await appState.refreshReminders() }
        }
        .onChange(of: familyNews) { _, value in
            NotificationService.set(.familyNews, value)
            Task {
                if value {
                    await NotificationService.shared.registerForRemote()
                } else {
                    UIApplication.shared.unregisterForRemoteNotifications()
                }
            }
        }
    }

    private var versionText: String {
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""
        return "Famille Cadeaux \(version) (\(build))"
    }
}

/// Libellé de réglage : icône teintée `textSecondary` alignée, texte `textPrimary`.
private struct SettingsLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: Spacing.m) {
            configuration.icon
                .foregroundStyle(Color.Theme.textSecondary)
                .frame(width: 24)
            configuration.title
                .foregroundStyle(Color.Theme.textPrimary)
        }
    }
}

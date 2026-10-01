import SwiftUI

/// Profil : prénom, pays, devise, déconnexion et suppression de compte (#5).
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
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.l) {
                    HStack(spacing: Spacing.m) {
                        ChildAvatar(name: name.isEmpty ? "?" : name, emoji: nil, colorName: nil, size: 64)
                        VStack(alignment: .leading) {
                            Text(appState.profile?.displayName ?? "")
                                .font(Font.Theme.title)
                                .foregroundStyle(Color.Theme.textPrimary)
                            if let household = appState.myHousehold {
                                Text(household.name)
                                    .font(Font.Theme.caption)
                                    .foregroundStyle(Color.Theme.textSecondary)
                            }
                        }
                    }

                    ProfileFields(name: $name, country: $country, currency: $currency)

                    PrimaryButton(title: "Enregistrer", systemImage: "checkmark", isLoading: isSaving) {
                        Task {
                            isSaving = true
                            await appState.saveProfile(name: name.trimmed, country: country, currency: currency)
                            isSaving = false
                        }
                    }
                    .disabled(!hasChanges || name.trimmed.isEmpty || isSaving)

                    VStack(alignment: .leading, spacing: Spacing.m) {
                        Label("Notifications", systemImage: "bell")
                            .font(Font.Theme.headline)
                        Toggle("Rappels d'achats (J-30, J-7)", isOn: $purchaseReminders)
                        Toggle("Nouvelles envies dans la famille", isOn: $familyNews)
                        Text("Aucune notification ne dit qui a réservé quoi.")
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                    .font(Font.Theme.body)
                    .fcCard()
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

                    VStack(alignment: .leading, spacing: Spacing.m) {
                        Label("Mode surprise", systemImage: "eye.slash")
                            .font(Font.Theme.headline)
                        Text("Tu ne vois jamais ce qui a été réservé pour tes enfants, ni les idées proposées pour eux. Personne ne sait qui offre quoi.")
                            .font(Font.Theme.caption)
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                    .fcCard()

                    VStack(spacing: Spacing.s) {
                        Button("Se déconnecter") { Task { await appState.signOut() } }
                            .frame(maxWidth: .infinity, minHeight: 44)
                        Button("Supprimer mon compte", role: .destructive) { confirmDelete = true }
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                    .font(Font.Theme.body)

                    #if DEBUG
                    NavigationLink("Galerie du design system (dev)") { DesignSystemGallery() }
                        .font(Font.Theme.caption)
                        .frame(maxWidth: .infinity)
                    #endif

                    Text("Famille Cadeaux \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "") (\(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? ""))")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .frame(maxWidth: .infinity)
                }
                .padding(Spacing.xl)
            }
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
}

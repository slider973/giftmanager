import SwiftUI

/// Premier lancement : prénom, pays et devise (#5).
struct ProfileSetupView: View {
    @Environment(AppState.self) private var appState
    @State private var name = ""
    @State private var country = "CH"
    @State private var currency = "CHF"
    @State private var isSaving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                Image("mascot_love")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 160)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: Spacing.s) {
                    Text("Faisons connaissance")
                        .font(Font.Theme.largeTitle)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text("Ton prénom est visible par ta famille. Ton pays sert à te montrer d'abord les boutiques près de chez toi.")
                        .font(Font.Theme.body)
                        .foregroundStyle(Color.Theme.textSecondary)
                }

                ProfileFields(name: $name, country: $country, currency: $currency)

                PrimaryButton(title: "C'est parti", systemImage: "arrow.right", isLoading: isSaving) {
                    Task {
                        isSaving = true
                        await appState.saveProfile(name: name.trimmingCharacters(in: .whitespaces),
                                                   country: country, currency: currency)
                        isSaving = false
                    }
                }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
            }
            .padding(Spacing.xl)
        }
        .fcScreenBackground()
        .onAppear {
            if name.isEmpty { name = appState.suggestedName ?? appState.profile?.displayName ?? "" }
            if let profile = appState.profile, profile.onboarded {
                country = profile.country
                currency = profile.currency
            } else if let region = Locale.current.region?.identifier, Countries.all.contains(where: { $0.code == region }) {
                country = region
                currency = StoreCatalog.currency(for: region) ?? currency
            }
        }
    }
}

/// Champs partagés entre la configuration initiale et l'écran Profil.
struct ProfileFields: View {
    @Binding var name: String
    @Binding var country: String
    @Binding var currency: String

    var body: some View {
        VStack(spacing: Spacing.m) {
            FCTextField(title: "Prénom", text: $name, systemImage: "person", prompt: "Ex. Jonathan")
                .textContentType(.givenName)

            HStack {
                Label("Pays", systemImage: "globe.europe.africa")
                    .foregroundStyle(Color.Theme.textSecondary)
                Spacer()
                Picker("Pays", selection: $country) {
                    ForEach(Countries.all) { country in
                        Text("\(country.flag) \(country.name)").tag(country.code)
                    }
                }
                .labelsHidden()
            }
            .font(Font.Theme.body)
            .fcCard()
            .onChange(of: country) { _, newValue in
                if let suggested = StoreCatalog.currency(for: newValue) { currency = suggested }
            }

            HStack {
                Label("Devise", systemImage: "banknote")
                    .foregroundStyle(Color.Theme.textSecondary)
                Spacer()
                Picker("Devise", selection: $currency) {
                    ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
                }
                .pickerStyle(.segmented)
                .frame(maxWidth: 220)
            }
            .font(Font.Theme.body)
            .fcCard()
        }
    }
}

import SwiftUI

/// Créer ou rejoindre une famille, puis créer ou rejoindre son foyer (#6).
struct GroupSetupView: View {
    @Environment(AppState.self) private var appState

    private enum Mode: Hashable { case choose, create, join }
    @State private var mode: Mode = .choose

    var body: some View {
        NavigationStack {
            Group {
                if appState.currentGroup != nil && appState.myHousehold == nil {
                    HouseholdSetupView()
                } else {
                    switch mode {
                    case .choose: chooser
                    case .create: CreateGroupForm(onCancel: { mode = .choose })
                    case .join: JoinGroupForm(onCancel: { mode = .choose })
                    }
                }
            }
            .fcScreenBackground()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Button("Se déconnecter", role: .destructive) { Task { await appState.signOut() } }
                    } label: {
                        Image(systemName: "ellipsis.circle").accessibilityLabel("Options")
                    }
                }
            }
        }
        .onAppear { if appState.pendingInviteCode != nil { mode = .join } }
    }

    private var chooser: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
                Image("mascot_family")
                    .resizable()
                    .scaledToFit()
                    .frame(height: 200)
                    .accessibilityHidden(true)
                VStack(spacing: Spacing.s) {
                    Text("Ta famille")
                        .font(Font.Theme.largeTitle)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text("Crée la famille et invite les autres foyers, ou rejoins-la avec le code reçu.")
                        .font(Font.Theme.body)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .multilineTextAlignment(.center)
                }
                VStack(spacing: Spacing.m) {
                    PrimaryButton(title: "Créer notre famille", systemImage: "plus") { mode = .create }
                    SecondaryButton(title: "J'ai un code d'invitation", systemImage: "envelope.open") { mode = .join }
                }
            }
            .padding(Spacing.xl)
        }
    }
}

private struct CreateGroupForm: View {
    @Environment(AppState.self) private var appState
    let onCancel: () -> Void
    @State private var groupName = ""
    @State private var householdName = ""
    @State private var isSaving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text("Créer la famille")
                    .font(Font.Theme.largeTitle)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                FCTextField(title: "Nom de la famille", text: $groupName, systemImage: "person.3", prompt: "Ex. Famille Lemaine")
                FCTextField(title: "Nom de ton foyer", text: $householdName, systemImage: "house", prompt: "Ex. Jonathan & Marie")
                FCNotice(systemImage: "sparkles",
                         text: "Le Noël de cette année est créé automatiquement. Tu pourras ensuite inviter les autres foyers.")
                PrimaryButton(title: "Créer", systemImage: "checkmark", isLoading: isSaving) {
                    Task {
                        isSaving = true
                        _ = await appState.createGroup(name: groupName.trimmed, householdName: householdName.trimmed)
                        isSaving = false
                    }
                }
                .disabled(groupName.trimmed.isEmpty || householdName.trimmed.isEmpty || isSaving)
                TextLinkButton(title: "Retour", action: onCancel)
                    .frame(maxWidth: .infinity)
            }
            .padding(Spacing.xl)
        }
        .onAppear {
            if householdName.isEmpty, let name = appState.profile?.displayName { householdName = "Foyer de \(name)" }
        }
    }
}

private struct JoinGroupForm: View {
    @Environment(AppState.self) private var appState
    let onCancel: () -> Void
    @State private var code = ""
    @State private var isJoining = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text("Rejoindre une famille")
                    .font(Font.Theme.largeTitle)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text("Saisis le code à 6 caractères reçu d'un membre de ta famille.")
                    .font(Font.Theme.body)
                    .foregroundStyle(Color.Theme.textSecondary)
                FCTextField(title: "Code d'invitation", text: $code, systemImage: "number", prompt: "ABC123")
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                PrimaryButton(title: "Rejoindre", systemImage: "arrow.right", isLoading: isJoining) {
                    Task {
                        isJoining = true
                        _ = await appState.joinGroup(code: code.trimmed)
                        appState.pendingInviteCode = nil
                        isJoining = false
                    }
                }
                .disabled(code.trimmed.count < 6 || isJoining)
                TextLinkButton(title: "Retour", action: onCancel)
                    .frame(maxWidth: .infinity)
            }
            .padding(Spacing.xl)
        }
        .onAppear { if let pending = appState.pendingInviteCode { code = pending } }
    }
}

/// Après avoir rejoint une famille : créer son foyer, ou rejoindre celui de son conjoint avec le code du foyer.
private struct HouseholdSetupView: View {
    @Environment(AppState.self) private var appState
    @State private var householdName = ""
    @State private var householdCode = ""
    @State private var isSaving = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text("Bienvenue dans « \(appState.currentGroup?.name ?? "") »")
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .accessibilityAddTraits(.isHeader)
                Text("Un foyer regroupe un ou deux parents et leurs enfants.")
                    .font(Font.Theme.body)
                    .foregroundStyle(Color.Theme.textSecondary)

                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Créer mon foyer")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    FCTextField(title: "Nom du foyer", text: $householdName, systemImage: "house", prompt: "Ex. Foyer de Marie")
                    PrimaryButton(title: "Créer mon foyer", systemImage: "plus", isLoading: isSaving) {
                        Task {
                            isSaving = true
                            _ = await appState.createHousehold(name: householdName.trimmed)
                            isSaving = false
                        }
                    }
                    .disabled(householdName.trimmed.isEmpty || isSaving)
                }
                .fcCard()

                VStack(alignment: .leading, spacing: Spacing.m) {
                    Text("Rejoindre le foyer de mon conjoint")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                        .accessibilityAddTraits(.isHeader)
                    Text("Demande-lui le code du foyer (Famille › Membres).")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                    FCTextField(title: "Code du foyer", text: $householdCode, systemImage: "key", prompt: "8 caractères")
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                    SecondaryButton(title: "Rejoindre ce foyer", systemImage: "person.2", isLoading: isSaving) {
                        Task {
                            isSaving = true
                            _ = await appState.joinHousehold(code: householdCode.trimmed)
                            isSaving = false
                        }
                    }
                    .disabled(householdCode.trimmed.count < 8 || isSaving)
                }
                .fcCard()

                FCNotice(systemImage: "info.circle",
                         text: "Grands-parents, oncles, tantes sans enfant : créez simplement votre foyer.")
            }
            .padding(Spacing.xl)
        }
        .onAppear {
            if householdName.isEmpty, let name = appState.profile?.displayName { householdName = "Foyer de \(name)" }
        }
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}

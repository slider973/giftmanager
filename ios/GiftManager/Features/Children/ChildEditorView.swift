import SwiftUI

/// Ajout / modification / suppression d'un enfant (#7). Réservé aux parents du foyer (RLS).
struct ChildEditorView: View {
    enum Mode: Identifiable {
        case create
        case edit(Child)

        var id: String {
            switch self {
            case .create: "create"
            case .edit(let child): child.id.uuidString
            }
        }
    }

    let mode: Mode
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var firstName = ""
    @State private var hasBirthdate = true
    @State private var birthdate = Calendar.current.date(byAdding: .year, value: -6, to: .now) ?? .now
    @State private var emoji: String? = AvatarPalette.emojis.first
    @State private var colorName = AvatarPalette.colors[1]
    @State private var isSaving = false
    @State private var confirmDelete = false

    private var existing: Child? {
        if case .edit(let child) = mode { return child }
        return nil
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: Spacing.l) {
                    ChildAvatar(name: firstName.isEmpty ? "?" : firstName, emoji: emoji, colorName: colorName, size: 96)
                        .shadow(color: .black.opacity(0.06), radius: 4, x: 0, y: 2)
                        .padding(.top, Spacing.s)
                        .accessibilityHidden(true)
                        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: emoji)

                    FCTextField(title: "Prénom", text: $firstName, systemImage: "person", prompt: "Ex. Léo")
                        .textContentType(.givenName)

                    VStack(alignment: .leading, spacing: 0) {
                        Toggle(isOn: $hasBirthdate.animation()) {
                            Label("Date de naissance", systemImage: "birthday.cake")
                                .foregroundStyle(Color.Theme.textPrimary)
                        }
                        .tint(Color.Theme.primary)
                        .frame(minHeight: HitTarget.minimum)
                        if hasBirthdate {
                            Divider().overlay(Color.Theme.separator)
                                .padding(.vertical, Spacing.xs)
                            DatePicker("Née / né le", selection: $birthdate, in: ...Date.now, displayedComponents: .date)
                                .environment(\.locale, Locale(identifier: "fr_FR"))
                                .foregroundStyle(Color.Theme.textPrimary)
                                .tint(Color.Theme.primary)
                                .frame(minHeight: HitTarget.minimum)
                        }
                    }
                    .font(Font.Theme.callout)
                    .fcCard(padding: Spacing.m)

                    VStack(alignment: .leading, spacing: Spacing.m) {
                        Text("Avatar")
                            .font(Font.Theme.headline)
                            .foregroundStyle(Color.Theme.textPrimary)
                            .accessibilityAddTraits(.isHeader)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s), count: 6), spacing: Spacing.s) {
                            ForEach(AvatarPalette.emojis, id: \.self) { item in
                                Button {
                                    emoji = item
                                } label: {
                                    Text(item)
                                        .font(.title2)
                                        .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                                        .background(emoji == item ? Color.Theme.pastel(named: colorName) ?? Color.Theme.pastelMint : .clear,
                                                    in: Circle())
                                        .overlay {
                                            Circle().strokeBorder(emoji == item ? Color.Theme.primary : .clear, lineWidth: 2)
                                        }
                                }
                                .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                                .accessibilityLabel("Avatar \(item)")
                                .accessibilityAddTraits(emoji == item ? .isSelected : [])
                            }
                        }
                        HStack(spacing: Spacing.m) {
                            ForEach(AvatarPalette.colors, id: \.self) { name in
                                Button {
                                    colorName = name
                                } label: {
                                    Circle()
                                        .fill(Color.Theme.pastel(named: name) ?? Color.Theme.pastelMint)
                                        .frame(width: 36, height: 36)
                                        .overlay(Circle().strokeBorder(Color.Theme.separator, lineWidth: 1))
                                        .overlay {
                                            if colorName == name {
                                                Circle().strokeBorder(Color.Theme.primary, lineWidth: 2.5).padding(-4)
                                            }
                                        }
                                        .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                                }
                                .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                                .accessibilityLabel("Couleur \(Self.colorLabel(name))")
                                .accessibilityAddTraits(colorName == name ? .isSelected : [])
                            }
                        }
                    }
                    .fcCard()

                    PrimaryButton(title: existing == nil ? "Ajouter" : "Enregistrer", systemImage: "checkmark", isLoading: isSaving) {
                        Task { await save() }
                    }
                    .disabled(firstName.trimmed.isEmpty || isSaving)

                    if existing != nil {
                        Button(role: .destructive) { confirmDelete = true } label: {
                            Label("Supprimer \(existing?.firstName ?? "")", systemImage: "trash")
                                .font(Font.Theme.callout.weight(.medium))
                                .foregroundStyle(Color.Theme.takenFg)
                                .frame(minHeight: HitTarget.minimum)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(FCPressableStyle(pressedScale: 1))
                        .padding(.top, Spacing.s)
                    }
                }
                .padding(Spacing.xl)
            }
            .fcScreenBackground()
            .navigationTitle(existing == nil ? "Nouvel enfant" : existing!.firstName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
            .confirmationDialog("Supprimer \(existing?.firstName ?? "") ?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Supprimer l'enfant et sa liste", role: .destructive) { Task { await delete() } }
            } message: {
                Text("Sa liste de cadeaux, ses idées et les réservations associées seront supprimées.")
            }
        }
        .onAppear(perform: load)
    }

    private static func colorLabel(_ name: String) -> String {
        switch name {
        case "pastelPink": "rose"
        case "pastelMint": "menthe"
        case "pastelBlue": "bleu"
        case "pastelPeach": "pêche"
        case "pastelLavender": "lavande"
        default: name
        }
    }

    private func load() {
        guard let child = existing else { return }
        firstName = child.firstName
        hasBirthdate = child.birthdate != nil
        if let date = child.birthdate?.localDate { birthdate = date }
        emoji = child.avatarEmoji
        colorName = child.avatarColor ?? colorName
    }

    private func save() async {
        guard let household = existing.map({ $0.householdId }) ?? appState.myHousehold?.id else { return }
        isSaving = true
        defer { isSaving = false }
        let child = Child(id: existing?.id ?? UUID(), householdId: household, firstName: firstName.trimmed,
                          birthdate: hasBirthdate ? DayDate(birthdate) : nil, avatarEmoji: emoji,
                          avatarColor: colorName, avatarUrl: existing?.avatarUrl)
        do {
            try await appState.repository.saveChild(child)
            await appState.reloadGroup()
            dismiss()
        } catch {
            appState.report(error)
        }
    }

    private func delete() async {
        guard let child = existing else { return }
        do {
            try await appState.repository.deleteChild(child.id)
            await appState.reloadGroup()
            appState.itemsChanged()
            dismiss()
        } catch {
            appState.report(error)
        }
    }
}

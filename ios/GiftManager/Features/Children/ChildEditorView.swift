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
                        .padding(.top, Spacing.l)

                    FCTextField(title: "Prénom", text: $firstName, systemImage: "person", prompt: "Ex. Léo")
                        .textContentType(.givenName)

                    VStack(alignment: .leading, spacing: Spacing.m) {
                        Toggle("Date de naissance", isOn: $hasBirthdate.animation())
                            .font(Font.Theme.body)
                        if hasBirthdate {
                            DatePicker("Née / né le", selection: $birthdate, in: ...Date.now, displayedComponents: .date)
                                .environment(\.locale, Locale(identifier: "fr_FR"))
                        }
                    }
                    .fcCard()

                    VStack(alignment: .leading, spacing: Spacing.m) {
                        Text("Avatar").font(Font.Theme.headline)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: Spacing.s), count: 6), spacing: Spacing.s) {
                            ForEach(AvatarPalette.emojis, id: \.self) { item in
                                Button {
                                    emoji = item
                                } label: {
                                    Text(item)
                                        .font(.title2)
                                        .frame(width: 44, height: 44)
                                        .background(emoji == item ? Color.Theme.pastel(named: colorName) ?? Color.Theme.pastelMint : .clear,
                                                    in: Circle())
                                }
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
                                        .fill(Color.Theme.pastel(named: name) ?? .gray)
                                        .frame(width: 36, height: 36)
                                        .overlay(Circle().stroke(Color.Theme.primary, lineWidth: colorName == name ? 3 : 0))
                                        .frame(width: 44, height: 44)
                                }
                                .accessibilityLabel("Couleur \(name)")
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
                        Button("Supprimer", role: .destructive) { confirmDelete = true }
                            .frame(minHeight: 44)
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

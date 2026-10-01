import SwiftUI

/// Création / modification d'un événement : anniversaire d'un enfant, Noël ou autre fête (#8).
struct EventEditorView: View {
    enum Mode: Identifiable {
        case create
        case edit(GiftEvent)

        var id: String {
            switch self {
            case .create: "create"
            case .edit(let event): event.id.uuidString
            }
        }
    }

    let mode: Mode
    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss

    @State private var kind: GiftEventKind = .birthday
    @State private var childId: UUID?
    @State private var title = ""
    @State private var date = Date.now
    @State private var isSaving = false
    @State private var confirmDelete = false

    private var existing: GiftEvent? {
        if case .edit(let event) = mode { return event }
        return nil
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker("Type", selection: $kind) {
                        Text("Anniversaire").tag(GiftEventKind.birthday)
                        Text("Noël").tag(GiftEventKind.christmas)
                        Text("Autre").tag(GiftEventKind.other)
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Color.clear)
                }

                if kind == .birthday {
                    Section("Enfant") {
                        Picker("Enfant", selection: $childId) {
                            Text("Choisir…").tag(UUID?.none)
                            ForEach(appState.children) { child in
                                Text(child.firstName).tag(Optional(child.id))
                            }
                        }
                    }
                }

                Section("Détails") {
                    TextField("Titre", text: $title)
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                        .environment(\.locale, Locale(identifier: "fr_FR"))
                }

                if existing != nil {
                    Section {
                        Button("Supprimer l'événement", role: .destructive) { confirmDelete = true }
                    } footer: {
                        Text("Les cadeaux restent dans les listes des enfants.")
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .navigationTitle(existing == nil ? "Nouvel événement" : "Modifier")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enregistrer") { Task { await save() } }
                        .disabled(!isValid || isSaving)
                }
            }
            .onChange(of: kind) { _, _ in suggest() }
            .onChange(of: childId) { _, _ in suggest() }
            .confirmationDialog("Supprimer cet événement ?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Supprimer", role: .destructive) { Task { await delete() } }
            }
        }
        .onAppear(perform: load)
    }

    private var isValid: Bool {
        !title.trimmed.isEmpty && (kind != .birthday || childId != nil)
    }

    private func load() {
        if let event = existing {
            kind = event.kind
            childId = event.childId
            title = event.title
            date = event.eventDate.localDate
        } else {
            childId = appState.myChildren.first?.id ?? appState.children.first?.id
            suggest()
        }
    }

    /// Titre et date proposés : prochain anniversaire de l'enfant, prochain Noël.
    private func suggest() {
        guard existing == nil else { return }
        let calendar = Calendar.current
        switch kind {
        case .birthday:
            guard let child = appState.child(childId) else { return }
            title = "Anniversaire de \(child.firstName)"
            if let birth = child.birthdate?.localDate {
                var comps = calendar.dateComponents([.month, .day], from: birth)
                comps.year = calendar.component(.year, from: .now)
                if let thisYear = calendar.date(from: comps) {
                    date = thisYear < calendar.startOfDay(for: .now) ? calendar.date(byAdding: .year, value: 1, to: thisYear)! : thisYear
                }
            }
        case .christmas:
            var comps = DateComponents(year: calendar.component(.year, from: .now), month: 12, day: 25)
            if let christmas = calendar.date(from: comps), christmas < .now { comps.year! += 1 }
            date = calendar.date(from: comps) ?? date
            title = "Noël \(comps.year!)"
        case .other:
            if title.hasPrefix("Anniversaire") || title.hasPrefix("Noël") { title = "" }
        }
    }

    private func save() async {
        guard let group = appState.currentGroup else { return }
        isSaving = true
        defer { isSaving = false }
        let event = GiftEvent(id: existing?.id ?? UUID(), groupId: group.id, kind: kind, title: title.trimmed,
                              eventDate: DayDate(date), childId: kind == .birthday ? childId : nil)
        do {
            try await appState.repository.saveEvent(event)
            await appState.reloadGroup()
            dismiss()
        } catch {
            appState.report(error)
        }
    }

    private func delete() async {
        guard let event = existing else { return }
        do {
            try await appState.repository.deleteEvent(event.id)
            await appState.reloadGroup()
            dismiss()
        } catch {
            appState.report(error)
        }
    }
}

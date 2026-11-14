import SwiftUI

/// Création / modification d'un événement de la famille (#8). Seul le type « autre » est proposé (#89) :
/// Noël est global et les anniversaires sont créés par le serveur (trigger `children_birthday`,
/// `ensure_birthday_events`) ; la RLS refuse d'écrire les autres types. Même règle qu'Android.
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
                Section("Détails") {
                    LabeledContent("Titre") {
                        TextField("Titre", text: $title,
                                  prompt: Text("Ex. Fête de fin d'année").foregroundStyle(Color.Theme.textSecondary))
                            .multilineTextAlignment(.trailing)
                    }
                    .frame(minHeight: HitTarget.minimum)
                    .fcListRow()
                    DatePicker("Date", selection: $date, displayedComponents: .date)
                        .environment(\.locale, Locale(identifier: "fr_FR"))
                        .frame(minHeight: HitTarget.minimum)
                        .fcListRow()
                }

                if let event = existing, canDelete(event) {
                    Section {
                        Button("Supprimer l'événement", role: .destructive) { confirmDelete = true }
                            .foregroundStyle(Color.Theme.takenFg)
                            .frame(minHeight: HitTarget.minimum)
                            .fcListRow()
                    } footer: {
                        Text("Les cadeaux restent dans les listes des enfants.")
                    }
                }
            }
            .font(Font.Theme.callout)
            .foregroundStyle(Color.Theme.textPrimary)
            .tint(Color.Theme.primary)
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .navigationTitle(existing == nil ? "Nouvel événement" : "Modifier")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Enregistrer") { Task { await save() } }
                            .fontWeight(.semibold)
                            .disabled(!isValid)
                    }
                }
            }
            .confirmationDialog("Supprimer cet événement ?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Supprimer", role: .destructive) { Task { await delete() } }
            }
        }
        .onAppear(perform: load)
    }

    /// Même règle que la RLS : l'auteur, ou un parent de l'enfant pour un anniversaire.
    private func canDelete(_ event: GiftEvent) -> Bool {
        event.createdBy == appState.userId || (appState.child(event.childId).map(appState.isParent(of:)) ?? false)
    }

    private var isValid: Bool {
        !title.trimmed.isEmpty
    }

    private func load() {
        guard let event = existing else { return }
        title = event.title
        date = event.eventDate.localDate
    }

    private func save() async {
        guard let group = appState.currentGroup else { return }
        isSaving = true
        defer { isSaving = false }
        // Une modification garde la famille d'origine de l'événement.
        let event = GiftEvent(id: existing?.id ?? UUID(), groupId: existing?.groupId ?? group.id, kind: .other,
                              title: title.trimmed, eventDate: DayDate(date), childId: existing?.childId,
                              createdBy: existing?.createdBy)
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

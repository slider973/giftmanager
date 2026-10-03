import SwiftUI

/// Définir, modifier ou supprimer un budget privé (#40).
struct BudgetEditorView: View {
    enum Target: Identifiable {
        case new
        case existing(Budget)

        var id: String {
            switch self {
            case .new: "new"
            case .existing(let budget): budget.id.uuidString
            }
        }

        var budget: Budget? {
            if case .existing(let budget) = self { return budget }
            return nil
        }
    }

    let target: Target
    let onDone: () -> Void

    @Environment(AppState.self) private var appState
    @State private var childId: UUID?
    @State private var eventId: UUID?
    @State private var amountText = ""
    @State private var currency = "EUR"
    @State private var isSaving = false
    @State private var confirmDelete = false
    @FocusState private var amountFocused: Bool

    init(target: Target, onDone: @escaping () -> Void) {
        self.target = target
        self.onDone = onDone
        if let budget = target.budget {
            _childId = State(initialValue: budget.childId)
            _eventId = State(initialValue: budget.eventId)
            _amountText = State(initialValue: BudgetMath.number(budget.amount))
            _currency = State(initialValue: budget.currency)
        }
    }

    private var amount: Decimal? {
        guard let value = LinkPreviewService.parsePrice(amountText), value > 0 else { return nil }
        return value
    }

    private var hasScope: Bool { childId != nil || eventId != nil }
    private var canSave: Bool { hasScope && amount != nil && !isSaving }

    /// Événements à venir, plus celui du budget édité s'il est passé.
    private var events: [GiftEvent] {
        var list = appState.upcomingEvents
        if let current = target.budget?.eventId, !list.contains(where: { $0.id == current }),
           let event = appState.events.first(where: { $0.id == current }) {
            list.append(event)
        }
        return list
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.xl) {
                scopeCard
                amountCard
                FCNotice(systemImage: "lock.fill",
                         text: "Ton budget est privé : personne d'autre ne le voit, pas même les parents.")
                VStack(spacing: Spacing.m) {
                    PrimaryButton(title: target.budget == nil ? "Créer le budget" : "Enregistrer",
                                  systemImage: "checkmark", isLoading: isSaving) {
                        Task { await save() }
                    }
                    .disabled(!canSave)
                    if target.budget != nil {
                        Button(role: .destructive) {
                            confirmDelete = true
                        } label: {
                            Text("Supprimer ce budget")
                                .font(Font.Theme.callout)
                                .foregroundStyle(Color.Theme.takenFg)
                                .frame(maxWidth: .infinity, minHeight: HitTarget.minimum)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(FCPressableStyle())
                    }
                }
            }
            .padding(Spacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
        .fcScreenBackground()
        .navigationTitle(target.budget == nil ? "Nouveau budget" : "Modifier le budget")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Annuler", action: onDone)
            }
        }
        .confirmationDialog("Supprimer ce budget ?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Supprimer", role: .destructive) { Task { await delete() } }
        } message: {
            Text("Tes réservations ne changent pas.")
        }
        .onAppear {
            if target.budget == nil {
                currency = appState.profile?.currency ?? "EUR"
                amountFocused = true
            }
        }
    }

    // MARK: - Champs

    private var scopeCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            pickerRow(title: "Enfant", systemImage: "person") {
                Picker("Enfant", selection: $childId) {
                    Text("Tous les enfants").tag(UUID?.none)
                    ForEach(appState.children) { child in
                        Text(child.firstName).tag(Optional(child.id))
                    }
                }
            }
            Divider().overlay(Color.Theme.separator)
            pickerRow(title: "Événement", systemImage: "calendar") {
                Picker("Événement", selection: $eventId) {
                    Text("Tous les événements").tag(UUID?.none)
                    ForEach(events) { event in
                        Text(event.title).tag(Optional(event.id))
                    }
                }
            }
            if !hasScope {
                Label("Choisis un enfant, un événement, ou les deux.", systemImage: "info.circle")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                    .padding(.top, Spacing.s)
            }
        }
        .font(Font.Theme.body)
        .fcCard()
    }

    private func pickerRow(title: String, systemImage: String, @ViewBuilder picker: () -> some View) -> some View {
        HStack {
            Label {
                Text(title).foregroundStyle(Color.Theme.textPrimary)
            } icon: {
                Image(systemName: systemImage).foregroundStyle(Color.Theme.textSecondary)
            }
            Spacer(minLength: Spacing.s)
            picker()
                .labelsHidden()
                .tint(Color.Theme.primary)
        }
        .frame(minHeight: HitTarget.minimum)
    }

    private var amountCard: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text("Montant")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.textSecondary)
                .accessibilityHidden(true)
            HStack(spacing: Spacing.s) {
                TextField("Montant", text: $amountText, prompt: Text("200").foregroundStyle(Color.Theme.textSecondary))
                    .keyboardType(.decimalPad)
                    .focused($amountFocused)
                    .monospacedDigit()
                    .font(Font.Theme.title)
                    .foregroundStyle(Color.Theme.textPrimary)
                Picker("Devise", selection: $currency) {
                    ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
                }
                .labelsHidden()
                .tint(Color.Theme.primary)
                .fixedSize()
            }
            .padding(.horizontal, Spacing.m)
            .frame(minHeight: HitTarget.button)
            .background(Color.Theme.background, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(amountFocused ? Color.Theme.primary : Color.Theme.separator, lineWidth: amountFocused ? 2 : 1)
            }
            Text("Seuls les cadeaux dont un lien est en \(currency) sont comptés dans ce budget.")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
        .fcCard()
    }

    // MARK: - Appels

    private func save() async {
        guard let amount else { return }
        isSaving = true
        defer { isSaving = false }
        do {
            let newId = try await appState.repository.setBudget(childId: childId, eventId: eventId,
                                                                amount: amount, currency: currency)
            // Périmètre ou devise changés : set_budget a créé un autre budget, on retire l'ancien.
            if let old = target.budget, old.id != newId {
                try await appState.repository.deleteBudget(old.id)
            }
            onDone()
        } catch {
            appState.report(error)
        }
    }

    private func delete() async {
        guard let budget = target.budget else { return }
        do {
            try await appState.repository.deleteBudget(budget.id)
            onDone()
        } catch {
            appState.report(error)
        }
    }
}

import SwiftUI

/// Participer à une cagnotte, ou modifier sa part (#35).
///
/// La devise est imposée par la cagnotte existante ; pour une nouvelle cagnotte, elle part
/// de la devise du meilleur lien du cadeau (sinon celle du profil).
struct JoinPotView: View {
    let item: WishItem
    let childName: String
    let links: [ItemLink]
    /// Appelé après une participation réussie.
    let onJoined: () -> Void

    @Environment(AppState.self) private var appState
    @Environment(\.dismiss) private var dismiss
    @State private var amountText = ""
    @State private var currency = "CHF"
    @State private var isSaving = false
    @State private var errorText: String?
    @FocusState private var amountFocused: Bool

    private var isUpdate: Bool { item.isInMyPot }
    /// Devise imposée par une cagnotte déjà ouverte.
    private var lockedCurrency: String? { item.potCurrency }
    private var progress: PotProgress? { PotProgress.make(item: item, links: links) }
    private var amount: Decimal? { PotAmount.parse(amountText) }
    private var showsInvalid: Bool { !amountText.trimmed.isEmpty && amount == nil }

    /// Reste à réunir, hors ma part actuelle (que je remplace en modifiant).
    private var remainingForMe: Decimal? {
        if let progress {
            guard let remaining = progress.remaining else { return nil }
            return remaining + (item.myContribution ?? 0)
        }
        return PotProgress.target(currency: currency, links: links)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    giftHeader

                    if let progress {
                        PotProgressCard(progress: progress,
                                        myShareText: item.myContribution.flatMap { Money.format($0, currency: progress.currency) })
                    }

                    amountField

                    FCNotice(systemImage: "eye.slash",
                             text: "Les parents de \(childName) ne verront rien. Les autres participants verront ton prénom et ta part.",
                             tone: .surprise)

                    PrimaryButton(title: isUpdate ? "Mettre à jour ma part" : "Participer",
                                  systemImage: "person.3.fill", isLoading: isSaving) {
                        Task { await save() }
                    }
                    .disabled(amount == nil || isSaving)
                }
                .padding(Spacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
            .fcScreenBackground()
            .navigationTitle(isUpdate ? "Ma part" : "Participer à une cagnotte")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { dismiss() } }
            }
            .onAppear(perform: prepare)
        }
    }

    private var giftHeader: some View {
        HStack(spacing: Spacing.m) {
            RemoteImage(url: item.imageURL, placeholderSeed: item.title)
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(item.title)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .lineLimit(2)
                Text("Pour \(childName)")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    private var amountField: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            Text(isUpdate ? "Ma nouvelle part" : "Ma participation")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.textSecondary)
                .accessibilityHidden(true)

            HStack(spacing: Spacing.s) {
                TextField("Montant", text: $amountText,
                          prompt: Text("Ex. 30").foregroundStyle(Color.Theme.textSecondary))
                    .keyboardType(.decimalPad)
                    .font(Font.Theme.title)
                    .monospacedDigit()
                    .foregroundStyle(Color.Theme.textPrimary)
                    .tint(Color.Theme.primary)
                    .focused($amountFocused)
                    .accessibilityLabel(isUpdate ? "Ma nouvelle part" : "Ma participation")
                    .accessibilityValue(amountText.isEmpty ? "vide" : "\(amountText) \(currency)")
                currencyControl
            }
            .padding(.leading, Spacing.l)
            .padding(.trailing, Spacing.s)
            .frame(minHeight: 60)
            .background(Color.Theme.surface, in: RoundedRectangle(cornerRadius: Radius.field, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: Radius.field, style: .continuous)
                    .strokeBorder(showsInvalid ? Color.Theme.takenFg : (amountFocused ? Color.Theme.primary : Color.Theme.separator),
                                  lineWidth: amountFocused || showsInvalid ? 1.5 : 1)
            }
            .contentShape(Rectangle())
            .onTapGesture { amountFocused = true }

            if showsInvalid {
                Label("Saisis un montant supérieur à zéro, par exemple 25 ou 12,50.", systemImage: "exclamationmark.circle")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.takenFg)
            } else if let lockedCurrency {
                Text("La cagnotte est en \(lockedCurrency) : toutes les parts sont dans cette devise.")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }

            if let errorText {
                FCNotice(systemImage: "exclamationmark.triangle", text: errorText, tone: .warning)
            }

            suggestions
        }
        .animation(.easeOut(duration: 0.15), value: showsInvalid)
    }

    @ViewBuilder
    private var currencyControl: some View {
        if let lockedCurrency {
            Text(lockedCurrency)
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.textSecondary)
                .padding(.horizontal, Spacing.m)
                .accessibilityHidden(true)
        } else {
            Menu {
                Picker("Devise", selection: $currency) {
                    ForEach(Countries.currencies, id: \.self) { Text($0).tag($0) }
                }
            } label: {
                HStack(spacing: Spacing.xs) {
                    Text(currency)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(Font.Theme.caption.weight(.semibold))
                        .accessibilityHidden(true)
                }
                .font(Font.Theme.headline)
                .foregroundStyle(Color.Theme.primary)
                .padding(.horizontal, Spacing.m)
                .frame(minHeight: HitTarget.minimum)
                .background(Color.Theme.background, in: Capsule())
                .contentShape(Capsule())
            }
            .accessibilityLabel("Devise")
            .accessibilityValue(currency)
        }
    }

    private var suggestions: some View {
        let remaining = remainingForMe
        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.s) {
                ForEach(PotAmount.suggestions(remaining: remaining), id: \.self) { value in
                    let text = Money.format(value, currency: lockedCurrency ?? currency) ?? ""
                    let isRest = value == remaining
                    let isSelected = amount == value
                    Button {
                        amountText = PotAmount.editableText(value)
                        amountFocused = false
                    } label: {
                        Text(isRest ? "Le reste : \(text)" : text)
                            .font(Font.Theme.callout.weight(.medium))
                            .monospacedDigit()
                            .foregroundStyle(isSelected ? Color.Theme.onPrimary : Color.Theme.textPrimary)
                            .padding(.horizontal, Spacing.l)
                            .frame(minHeight: HitTarget.minimum)
                            .background(isSelected ? Color.Theme.primary : Color.Theme.surface, in: Capsule())
                            .overlay(Capsule().strokeBorder(isSelected ? .clear : Color.Theme.separator, lineWidth: 1))
                            .contentShape(Capsule())
                    }
                    .buttonStyle(FCPressableStyle(pressedScale: 0.95))
                    .accessibilityLabel(isRest ? "Compléter la cagnotte : \(text)" : text)
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(.vertical, 2)
        }
        .scrollClipDisabled()
        .padding(.top, Spacing.xs)
    }

    private func prepare() {
        if let mine = item.myContribution { amountText = PotAmount.editableText(mine) }
        currency = lockedCurrency
            ?? links.first(where: { $0.currency != nil && $0.price != nil })?.currency
            ?? appState.profile?.currency
            ?? "CHF"
        if !isUpdate { amountFocused = true }
    }

    private func save() async {
        guard let amount else { return }
        isSaving = true
        errorText = nil
        defer { isSaving = false }
        do {
            try await appState.repository.joinPot(itemId: item.id, amount: amount, currency: lockedCurrency ?? currency)
            onJoined()
            dismiss()
        } catch {
            if error is CancellationError { return }
            errorText = GiftError(error).localizedDescription
        }
    }
}

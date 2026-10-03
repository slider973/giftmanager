import SwiftUI

/// Section « Budgets » de Mes achats (#40) : jauges privées par enfant et/ou par événement.
struct BudgetsSection: View {
    @Environment(AppState.self) private var appState
    @State private var budgets: [Budget] = []
    @State private var hasLoaded = false
    @State private var editing: BudgetEditorView.Target?

    private var profileCurrency: String? { appState.profile?.currency }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionHeader(title: "Budgets", actionSystemImage: "plus",
                          action: { editing = .new }, actionLabel: "Ajouter un budget")
            if hasLoaded && budgets.isEmpty {
                invitation
            }
            ForEach(budgets) { budget in
                Button {
                    editing = .existing(budget)
                } label: {
                    BudgetCard(budget: budget, profileCurrency: profileCurrency)
                }
                .buttonStyle(FCPressableStyle(pressedScale: 0.98))
                .accessibilityHint("Modifier ou supprimer ce budget")
            }
            if let total = indicativeTotal {
                Text(total.text)
                    .font(Font.Theme.captionBold)
                    .monospacedDigit()
                    .foregroundStyle(Color.Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .accessibilityLabel(total.spoken)
                    .padding(.top, Spacing.xs)
            }
        }
        .task(id: appState.itemsRevision) { await load() }
        .sheet(item: $editing) { target in
            NavigationStack {
                BudgetEditorView(target: target) {
                    editing = nil
                    Task { await load() }
                }
            }
        }
    }

    private var invitation: some View {
        Button {
            editing = .new
        } label: {
            HStack(spacing: Spacing.m) {
                Image(systemName: "chart.bar.fill")
                    .font(.title3)
                    .foregroundStyle(Color.Theme.mineFg)
                    .frame(width: 44, height: 44)
                    .background(Color.Theme.pastelBlue, in: Circle())
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Définir un budget")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text("Une limite par enfant ou par événement, visible de toi seul.")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .fcCard(padding: Spacing.m)
            .contentShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
        }
        .buttonStyle(FCPressableStyle())
        .accessibilityElement(children: .combine)
    }

    /// Total converti indicatif, seulement si les budgets mélangent les devises
    /// et ne se recoupent pas (sinon un même cadeau serait compté deux fois).
    private var indicativeTotal: (text: String, spoken: String)? {
        guard let profileCurrency, budgets.count > 1, BudgetMath.areDisjoint(budgets) else { return nil }
        let service = CurrencyService.shared
        guard let spent = service.convertedTotal(budgets.map { ($0.currency, $0.spent) }, to: profileCurrency),
              let amount = service.convertedTotal(budgets.map { ($0.currency, $0.amount) }, to: profileCurrency)
        else { return nil }
        let spentText = IndicativePrice.amountText(spent, currency: profileCurrency)
        let amountText = IndicativePrice.amountText(amount, currency: profileCurrency)
        return ("≈ \(spentText) sur \(amountText) au total · indicatif",
                "environ \(spentText) dépensés sur \(amountText) au total, conversion indicative")
    }

    private func load() async {
        do {
            budgets = try await appState.repository.myBudgets()
        } catch {
            appState.report(error)
        }
        hasLoaded = true
    }
}

/// Carte d'un budget : périmètre, « 180 / 200 CHF », jauge, reste ou dépassement.
struct BudgetCard: View {
    let budget: Budget
    let profileCurrency: String?

    @ScaledMetric(relativeTo: .caption) private var gaugeHeight: CGFloat = 8

    private var level: BudgetMath.Level { BudgetMath.level(budget) }

    private var fill: Color {
        switch level {
        case .comfortable: Color.Theme.secondary
        case .nearlyReached: Color.Theme.accentAmber
        case .over: Color.Theme.takenFg
        }
    }

    private var approx: String? {
        let service = CurrencyService.shared
        guard let profileCurrency,
              let spent = service.convert(budget.spent, from: budget.currency, to: profileCurrency),
              let amount = service.convert(budget.amount, from: budget.currency, to: profileCurrency)
        else { return nil }
        return "≈ " + BudgetMath.gaugeText(spent: IndicativePrice.rounded(spent), amount: IndicativePrice.rounded(amount),
                                           currency: profileCurrency)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                Text(budget.scopeTitle)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(BudgetMath.gaugeText(spent: budget.spent, amount: budget.amount, currency: budget.currency))
                    .font(Font.Theme.callout.weight(.semibold))
                    .monospacedDigit()
                    .foregroundStyle(level == .over ? Color.Theme.takenFg : Color.Theme.textPrimary)
            }

            Capsule()
                .fill(Color.Theme.separator)
                .frame(height: gaugeHeight)
                .overlay(alignment: .leading) {
                    GeometryReader { proxy in
                        Capsule()
                            .fill(fill)
                            .frame(width: max(budget.spent > 0 ? gaugeHeight : 0, proxy.size.width * budget.progress))
                    }
                }
                .animation(.spring(response: 0.4, dampingFraction: 0.85), value: budget.progress)
                .accessibilityHidden(true)

            HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                status
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let approx {
                    ApproxPriceText(text: approx)
                        .font(Font.Theme.caption)
                }
            }
        }
        .fcCard(padding: Spacing.l)
        .overlay {
            if level == .over {
                RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                    .strokeBorder(Color.Theme.takenFg.opacity(0.5), lineWidth: 1.5)
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var status: some View {
        switch level {
        case .over:
            Label("Dépassé de \(BudgetMath.amountText(-budget.remaining, currency: budget.currency))",
                  systemImage: "exclamationmark.triangle.fill")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.takenFg)
        case .nearlyReached:
            Label("Plus que \(BudgetMath.amountText(budget.remaining, currency: budget.currency))",
                  systemImage: "gauge.with.dots.needle.67percent")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.textPrimary)
        case .comfortable:
            Text("Reste \(BudgetMath.amountText(budget.remaining, currency: budget.currency))")
                .font(Font.Theme.caption)
                .foregroundStyle(Color.Theme.textSecondary)
        }
    }
}

#if DEBUG
private func previewBudget(_ title: String, spent: Decimal, amount: Decimal, currency: String) -> Budget {
    Budget(id: UUID(), childId: UUID(), childName: title, eventId: nil, eventTitle: nil,
           amount: amount, currency: currency, spent: spent)
}

#Preview("Cartes") {
    VStack(spacing: Spacing.m) {
        BudgetCard(budget: previewBudget("Léo", spent: 120, amount: 200, currency: "CHF"), profileCurrency: "CHF")
        BudgetCard(budget: previewBudget("Emma", spent: 180, amount: 200, currency: "CHF"), profileCurrency: "EUR")
        BudgetCard(budget: previewBudget("Noé", spent: 245, amount: 200, currency: "EUR"), profileCurrency: "EUR")
    }
    .padding(Spacing.xl)
    .fcScreenBackground()
}

#Preview("Sombre") {
    BudgetCard(budget: previewBudget("Noé", spent: 245, amount: 200, currency: "EUR"), profileCurrency: "CHF")
        .padding(Spacing.xl)
        .fcScreenBackground()
        .preferredColorScheme(.dark)
}
#endif

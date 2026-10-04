import SwiftUI

/// « Mes achats » : mes réservations par événement puis par enfant, total par devise (#12),
/// puis mes cagnottes avec ma part (#35).
struct MyPurchasesView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.openURL) private var openURL
    @State private var leavingPot: MyContribution?
    /// Données et calculs, testés sans réseau (voir `MyPurchasesModelTests`).
    @State private var model: MyPurchasesModel?

    private typealias EventGroup = MyPurchasesModel.EventGroup

    private var reservations: [MyReservation] { model?.reservations ?? [] }
    private var contributions: [MyContribution] { model?.contributions ?? [] }
    private var links: [UUID: [ItemLink]] { model?.links ?? [:] }
    private var thanksCount: Int { model?.thanksCount ?? 0 }
    private var hasLoaded: Bool { model?.hasLoaded ?? false }
    private var groups: [EventGroup] { model?.groups ?? [] }
    private var remainingCount: Int { model?.remainingCount ?? 0 }
    private var summaryCountLabel: String { model?.summaryCountLabel ?? "cadeau à acheter" }
    private var totals: [(currency: String, amount: Decimal)] { model?.totals ?? [] }

    var body: some View {
        NavigationStack {
            List {
                if thanksCount > 0 {
                    thanksBanner
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(rowInsets(top: Spacing.s, bottom: Spacing.xs))
                }
                if hasLoaded && reservations.isEmpty && contributions.isEmpty {
                    EmptyStateView(imageName: "mascot_sleeping", title: "Aucun achat prévu",
                                   message: "Réserve un cadeau ou participe à une cagnotte dans la liste d'un enfant : il apparaîtra ici.")
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
                if !reservations.isEmpty || !contributions.isEmpty {
                    summary
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(rowInsets(top: Spacing.s, bottom: Spacing.s))
                }
                // Un budget se prépare aussi avant toute réservation (#40).
                if hasLoaded {
                    BudgetsSection()
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                        .listRowInsets(rowInsets(top: Spacing.s, bottom: Spacing.s))
                }
                ForEach(groups) { group in
                    Section {
                        ForEach(group.children, id: \.name) { child in
                            Text("Pour \(child.name)")
                                .font(Font.Theme.captionBold)
                                .foregroundStyle(Color.Theme.textSecondary)
                                .accessibilityAddTraits(.isHeader)
                                .listRowBackground(Color.clear)
                                .listRowSeparator(.hidden)
                                .listRowInsets(rowInsets(top: Spacing.m, bottom: Spacing.xs))
                            ForEach(child.items) { reservation in
                                row(reservation)
                                    .listRowBackground(Color.clear)
                                    .listRowSeparator(.hidden)
                                    .listRowInsets(rowInsets(top: Spacing.xs, bottom: Spacing.xs))
                                    .swipeActions(edge: .trailing) {
                                        Button("Annuler", role: .destructive) {
                                            Task { await cancel(reservation) }
                                        }
                                    }
                            }
                        }
                    } header: {
                        HStack(alignment: .firstTextBaseline) {
                            Text(group.title)
                                .font(Font.Theme.headline)
                                .foregroundStyle(Color.Theme.textPrimary)
                            Spacer(minLength: Spacing.s)
                            if let date = group.date {
                                Text(Formatting.dateText(date))
                                    .font(Font.Theme.caption)
                                    .foregroundStyle(Color.Theme.textSecondary)
                            }
                        }
                        .textCase(nil)
                        .padding(.vertical, Spacing.s)
                        .accessibilityElement(children: .combine)
                        .accessibilityAddTraits(.isHeader)
                    }
                }
                if !contributions.isEmpty {
                    potsSection
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .fcScreenBackground()
            .confirmationDialog("Quitter la cagnotte ?", isPresented: Binding(
                get: { leavingPot != nil }, set: { if !$0 { leavingPot = nil } }
            ), titleVisibility: .visible, presenting: leavingPot) { contribution in
                Button("Quitter « \(contribution.title) »", role: .destructive) { Task { await leave(contribution) } }
            } message: { _ in
                Text("Ta part sera retirée de la cagnotte.")
            }
            .overlay {
                if !hasLoaded {
                    ProgressView("Chargement de tes achats…")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                        .tint(Color.Theme.primary)
                }
            }
            .navigationTitle("Mes achats")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink {
                        ThanksInboxView()
                    } label: {
                        Image(systemName: "envelope")
                    }
                    .accessibilityLabel("Remerciements reçus")
                }
            }
            .refreshable { await load() }
            .task(id: appState.itemsRevision) { await load() }
        }
    }

    private func rowInsets(top: CGFloat, bottom: CGFloat) -> EdgeInsets {
        EdgeInsets(top: top, leading: Spacing.xl, bottom: bottom, trailing: Spacing.xl)
    }

    private var summary: some View {
        HStack(spacing: Spacing.l) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(reservations.isEmpty ? contributions.count : remainingCount)")
                    .font(Font.Theme.title)
                    .monospacedDigit()
                    .foregroundStyle(Color.Theme.textPrimary)
                Text(summaryCountLabel)
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            Rectangle()
                .fill(Color.Theme.separator)
                .frame(width: 1, height: 36)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(totals.isEmpty ? "—" : totals.compactMap { Money.format($0.amount, currency: $0.currency) }.joined(separator: " + "))
                    .font(Font.Theme.headline)
                    .monospacedDigit()
                    .foregroundStyle(Color.Theme.textPrimary)
                Text("Budget estimé")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                ConvertedTotalLine(totals: totals, profileCurrency: appState.profile?.currency)
            }
            Spacer()
        }
        .fcCard()
        .accessibilityElement(children: .combine)
    }

    private func row(_ reservation: MyReservation) -> some View {
        let link = bestLink(reservation.itemId)
        return HStack(spacing: Spacing.m) {
            RemoteImage(url: reservation.imageUrl.flatMap(URL.init(string:)), placeholderSeed: reservation.title)
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(reservation.title)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .lineLimit(2)
                HStack(spacing: Spacing.xs) {
                    if let country = link?.country { CountryFlag(code: country) }
                    Text([link?.priceWithApprox(profileCurrency: appState.profile?.currency), link?.store].compactMap { $0 }.joined(separator: " · "))
                        .font(Font.Theme.caption)
                        .monospacedDigit()
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                if reservation.status == .purchased {
                    Label("Acheté", systemImage: "checkmark")
                        .font(Font.Theme.captionBold)
                        .foregroundStyle(Color.Theme.availableFg)
                }
                if reservation.owned {
                    ownedNotice(eventDate: reservation.eventDate)
                        .padding(.top, Spacing.xs)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(spacing: 0) {
                Button {
                    Task { await toggle(reservation) }
                } label: {
                    Image(systemName: reservation.status == .purchased ? "checkmark.circle.fill" : "circle")
                        .font(.title2)
                        .foregroundStyle(reservation.status == .purchased ? Color.Theme.availableFg : Color.Theme.textSecondary)
                        .contentTransition(.symbolEffect(.replace))
                        .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                        .contentShape(Rectangle())
                }
                .buttonStyle(FCPressableStyle(pressedScale: 0.85))
                .sensoryFeedback(.success, trigger: reservation.status == .purchased)
                .accessibilityLabel("Acheté")
                .accessibilityValue(reservation.status == .purchased ? "Oui" : "Non")
                .accessibilityAddTraits(reservation.status == .purchased ? .isSelected : [])
                .accessibilityHint("Coche quand le cadeau est acheté")
                if let url = link.flatMap({ URL(string: $0.url) }) {
                    Button {
                        openURL(url)
                    } label: {
                        Image(systemName: "arrow.up.right.square")
                            .font(Font.Theme.callout)
                            .frame(width: HitTarget.minimum, height: HitTarget.minimum)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(FCPressableStyle(pressedScale: 0.9))
                    .foregroundStyle(Color.Theme.primary)
                    .accessibilityLabel("Ouvrir la boutique")
                }
            }
            .padding(.vertical, -Spacing.s)
            .padding(.trailing, -Spacing.xs)
        }
        .fcCard(padding: Spacing.m)
    }

    // MARK: - Remerciements (#42)

    private var thanksBanner: some View {
        ZStack {
            NavigationLink { ThanksInboxView() } label: { EmptyView() }
                .opacity(0)
            HStack(spacing: Spacing.m) {
                Image("mascot_love")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 52, height: 52)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 2) {
                    Text(thanksCount > 1 ? "\(thanksCount) remerciements reçus" : "Un remerciement reçu")
                        .font(Font.Theme.headline)
                        .foregroundStyle(Color.Theme.textPrimary)
                    Text("Lis les messages des parents")
                        .font(Font.Theme.caption)
                        .foregroundStyle(Color.Theme.textSecondary)
                }
                Spacer(minLength: Spacing.s)
                Image(systemName: "chevron.right")
                    .font(Font.Theme.callout.weight(.semibold))
                    .foregroundStyle(Color.Theme.textSecondary)
                    .accessibilityHidden(true)
            }
            .fcCard(padding: Spacing.m)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    // MARK: - Cagnottes

    private var potsSection: some View {
        Section {
            ForEach(contributions) { contribution in
                potRow(contribution)
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .listRowInsets(rowInsets(top: Spacing.xs, bottom: Spacing.xs))
                    .swipeActions(edge: .trailing) {
                        Button("Quitter", role: .destructive) { leavingPot = contribution }
                    }
            }
        } header: {
            HStack(alignment: .firstTextBaseline) {
                Text("Cagnottes")
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                Spacer(minLength: Spacing.s)
                Text(contributions.count > 1 ? "\(contributions.count) participations" : "1 participation")
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
            }
            .textCase(nil)
            .padding(.vertical, Spacing.s)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
        }
    }

    private func potRow(_ contribution: MyContribution) -> some View {
        let share = Money.format(contribution.amount, currency: contribution.currency) ?? ""
        let total = Money.format(contribution.potTotal, currency: contribution.currency)
        let count = contribution.potCount ?? 1
        return HStack(alignment: .top, spacing: Spacing.m) {
            RemoteImage(url: contribution.imageUrl.flatMap(URL.init(string:)), placeholderSeed: contribution.title)
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: Radius.thumb, style: .continuous))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(contribution.title)
                    .font(Font.Theme.headline)
                    .foregroundStyle(Color.Theme.textPrimary)
                    .lineLimit(2)
                Text(["Pour \(contribution.childName)", contribution.eventTitle].compactMap { $0 }.joined(separator: " · "))
                    .font(Font.Theme.caption)
                    .foregroundStyle(Color.Theme.textSecondary)
                HStack(alignment: .firstTextBaseline, spacing: Spacing.s) {
                    Text("Ma part : \(share)")
                        .font(Font.Theme.captionBold)
                        .monospacedDigit()
                        .foregroundStyle(Color.Theme.potFg)
                        .padding(.horizontal, Spacing.s)
                        .padding(.vertical, 2)
                        .background(Color.Theme.potBg, in: Capsule())
                        .fixedSize()
                    if let approx = CurrencyService.shared.approxText(contribution.amount, from: contribution.currency,
                                                                       to: appState.profile?.currency) {
                        ApproxPriceText(text: approx).font(Font.Theme.caption)
                    }
                    if let total {
                        Text("\(total) réunis · \(count) participant\(count > 1 ? "s" : "")")
                            .font(Font.Theme.caption)
                            .monospacedDigit()
                            .foregroundStyle(Color.Theme.textSecondary)
                    }
                }
                .padding(.top, 2)
                if contribution.owned {
                    ownedNotice(eventDate: contribution.eventDate)
                        .padding(.top, Spacing.xs)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .fcCard(padding: Spacing.m)
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: "Quitter la cagnotte") { leavingPot = contribution }
    }

    /// Cadeau noté possédé : reçu si la fête est passée, sinon alerte de doublon.
    @ViewBuilder
    private func ownedNotice(eventDate: DayDate?) -> some View {
        if ReceiptRules.isReceived(owned: true, eventDate: eventDate) {
            Label("Bien reçu !", systemImage: "gift.fill")
                .font(Font.Theme.captionBold)
                .foregroundStyle(Color.Theme.availableFg)
        } else {
            FCNotice(systemImage: "exclamationmark.triangle",
                     text: "Les parents l'ont noté comme déjà possédé", tone: .warning)
        }
    }

    /// Le modèle, créé à la première utilisation (il dépend du profil).
    private func purchasesModel() -> MyPurchasesModel {
        if let model { return model }
        let created = MyPurchasesModel(actions: appState.repository,
                                       preferredCountry: appState.profile?.country)
        model = created
        return created
    }

    /// Remonte l'erreur éventuelle du modèle à l'alerte globale.
    private func drainError(_ model: MyPurchasesModel) {
        if let error = model.lastError {
            appState.report(error)
            model.lastError = nil
        }
    }

    private func bestLink(_ itemId: UUID) -> ItemLink? {
        model?.bestLink(itemId)
    }

    private func load() async {
        let model = purchasesModel()
        await model.load()
        drainError(model)
        await NotificationService.shared.scheduleReminders(reservations: model.reservations,
                                                           events: appState.events)
    }

    private func toggle(_ reservation: MyReservation) async {
        let model = purchasesModel()
        await model.togglePurchased(reservation)
        drainError(model)
    }

    private func cancel(_ reservation: MyReservation) async {
        let model = purchasesModel()
        await model.cancel(reservation)
        drainError(model)
        appState.itemsChanged()
    }

    private func leave(_ contribution: MyContribution) async {
        let model = purchasesModel()
        await model.leavePot(contribution)
        drainError(model)
        appState.itemsChanged()
    }
}

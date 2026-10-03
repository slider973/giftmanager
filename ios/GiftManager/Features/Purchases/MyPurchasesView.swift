import SwiftUI

/// « Mes achats » : mes réservations par événement puis par enfant, total par devise (#12),
/// puis mes cagnottes avec ma part (#35).
struct MyPurchasesView: View {
    @Environment(AppState.self) private var appState
    @Environment(\.openURL) private var openURL
    @State private var reservations: [MyReservation] = []
    @State private var contributions: [MyContribution] = []
    @State private var leavingPot: MyContribution?
    @State private var links: [UUID: [ItemLink]] = [:]
    @State private var hasLoaded = false

    private struct EventGroup: Identifiable {
        let id: String
        let title: String
        let date: DayDate?
        let children: [(name: String, items: [MyReservation])]
    }

    private var groups: [EventGroup] {
        let byEvent = Dictionary(grouping: reservations) { $0.eventId?.uuidString ?? "none" }
        return byEvent.map { key, items in
            let byChild = Dictionary(grouping: items, by: \.childName)
                .sorted { $0.key < $1.key }
                .map { (name: $0.key, items: $0.value.sorted { $0.title < $1.title }) }
            return EventGroup(id: key, title: items.first?.eventTitle ?? "Sans événement",
                              date: items.first?.eventDate, children: byChild)
        }
        .sorted { ($0.date?.date ?? .distantFuture) < ($1.date?.date ?? .distantFuture) }
    }

    private var remainingCount: Int { reservations.filter { $0.status == .reserved }.count }

    /// Sans réservation, le résumé compte les cagnottes.
    private var summaryCountLabel: String {
        if reservations.isEmpty { return contributions.count > 1 ? "cagnottes" : "cagnotte" }
        return remainingCount > 1 ? "cadeaux à acheter" : "cadeau à acheter"
    }

    /// Total par devise, à partir du lien le plus pertinent de chaque cadeau.
    private var totals: [(currency: String, amount: Decimal)] {
        var sums: [String: Decimal] = [:]
        for reservation in reservations {
            guard let link = bestLink(reservation.itemId), let price = link.price else { continue }
            sums[link.currency ?? "EUR", default: 0] += price
        }
        // Cagnottes : seule ma part compte dans mon budget.
        for contribution in contributions {
            sums[contribution.currency, default: 0] += contribution.amount
        }
        return sums.sorted { $0.key < $1.key }.map { (currency: $0.key, amount: $0.value) }
    }

    var body: some View {
        NavigationStack {
            List {
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
                    Text([link?.priceText, link?.store].compactMap { $0 }.joined(separator: " · "))
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

    private func leave(_ contribution: MyContribution) async {
        do {
            try await appState.repository.leavePot(itemId: contribution.itemId)
            appState.itemsChanged()
        } catch {
            appState.report(error)
        }
    }

    private func bestLink(_ itemId: UUID) -> ItemLink? {
        StoreCatalog.sorted(links[itemId] ?? [], preferredCountry: appState.profile?.country).first
    }

    private func load() async {
        do {
            async let pots = appState.repository.myContributions()
            reservations = try await appState.repository.myReservations()
            contributions = try await pots
            let fetched = try await appState.repository.links(itemIds: reservations.map(\.itemId))
            links = Dictionary(grouping: fetched, by: \.itemId)
            await NotificationService.shared.scheduleReminders(reservations: reservations, events: appState.events)
        } catch {
            appState.report(error)
        }
        hasLoaded = true
    }

    private func toggle(_ reservation: MyReservation) async {
        do {
            try await appState.repository.setPurchased(itemId: reservation.itemId, purchased: reservation.status != .purchased)
            await load()
        } catch {
            appState.report(error)
        }
    }

    private func cancel(_ reservation: MyReservation) async {
        do {
            try await appState.repository.cancelReservation(itemId: reservation.itemId)
            appState.itemsChanged()
        } catch {
            appState.report(error)
        }
    }
}

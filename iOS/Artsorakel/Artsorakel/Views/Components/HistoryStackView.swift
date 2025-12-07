import SwiftUI

/// A stacked card view showing recent history on the main screen
struct HistoryStackView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    @ObservedObject var historyStorage: HistoryStorage
    let onTap: () -> Void

    var body: some View {
        if let topItem = historyStorage.history.first {
            VStack(alignment: .leading, spacing: 8) {
                // Header
                Text(localizationManager.localize("identification_history", comment: "History"))
                    .font(DesignSystem.Typography.bodyBold())
                    .foregroundColor(Color.textPrimary)

                // Stack of cards - dummies rendered first (behind), then real card on top
                // Dummies are offset down so their bottom edge peeks out
                Button(action: onTap) {
                    HistoryCardView(item: topItem)
                        .background(
                            ZStack(alignment: .top) {
                                // Dummy card 2 (furthest back, most offset, narrowest)
                                if historyStorage.history.count >= 3 {
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color.surfacePrimary)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(Color.borderDefault, lineWidth: 1)
                                        )
                                        .padding(.horizontal, 16)
                                        .offset(y: 12)
                                }

                                // Dummy card 1 (middle, less offset)
                                if historyStorage.history.count >= 2 {
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color.surfacePrimary)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(Color.borderDefault, lineWidth: 1)
                                        )
                                        .padding(.horizontal, 8)
                                        .offset(y: 6)
                                }
                            }
                        )
                }
                .buttonStyle(PlainButtonStyle())
                // Add bottom padding to account for dummy cards peeking out
                .padding(.bottom, historyStorage.history.count >= 3 ? 12 : (historyStorage.history.count >= 2 ? 6 : 0))
            }
            .padding(.horizontal, DesignSystem.Spacing.standard)
            .padding(.bottom, DesignSystem.Spacing.standard)
        }
    }
}

/// Individual history card view
struct HistoryCardView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    @Environment(\.colorScheme) var colorScheme
    let item: IdentificationHistory

    var body: some View {
        HStack(spacing: DesignSystem.Spacing.standard) {
            // Thumbnail
            if let firstPath = item.imagePaths.first,
               let image = HistoryStorage.shared.loadImage(at: firstPath) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 64, height: 64)
                    .clipShape(RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.small))
            } else {
                RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.small)
                    .fill(Color.surfaceSubtle)
                    .frame(width: 64, height: 64)
            }

            // Species info
            VStack(alignment: .leading, spacing: 4) {
                // Name display
                let languageCode = localizationManager.currentLanguage == "system"
                    ? Locale.current.languageCode ?? "en"
                    : localizationManager.currentLanguage

                if let vernacularName = item.getVernacularName(for: languageCode), !vernacularName.isEmpty {
                    Text(vernacularName.prefix(1).capitalized + vernacularName.dropFirst())
                        .font(DesignSystem.Typography.bodyBold())
                        .foregroundColor(Color.textPrimary)
                        .lineLimit(1)

                    if let scientificName = item.bestMatchScientificName {
                        Text(scientificName)
                            .font(DesignSystem.Typography.caption())
                            .italic()
                            .foregroundColor(Color.textPrimary)
                            .lineLimit(1)
                    }
                } else if let scientificName = item.bestMatchScientificName {
                    Text(scientificName)
                        .font(DesignSystem.Typography.bodyBold())
                        .italic()
                        .foregroundColor(Color.textPrimary)
                        .lineLimit(1)
                }

                // Timestamp
                Text(formatDate(item.timestamp))
                    .font(DesignSystem.Typography.caption())
                    .foregroundColor(Color.textSecondary)
            }

            Spacer()

            // Chevron
            SVGWebView(svgName: "ic_chevron_right", width: DesignSystem.IconSize.small, height: DesignSystem.IconSize.small, tintColor: .textPrimary)
                .frame(width: DesignSystem.IconSize.small, height: DesignSystem.IconSize.small)
        }
        .padding(DesignSystem.Spacing.standard)
        .background(Color.surfacePrimary)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.borderDefault, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd. MMM yyyy"
        return formatter.string(from: date)
    }
}

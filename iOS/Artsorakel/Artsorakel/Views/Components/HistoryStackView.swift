import SwiftUI

/// A stacked card view showing recent history on the main screen
struct HistoryStackView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    @ObservedObject var historyStorage: HistoryStorage
    let onTap: () -> Void

    var body: some View {
        let recentHistory = historyStorage.getRecentHistory(limit: 3)

        if !recentHistory.isEmpty {
            Button(action: onTap) {
                ZStack {
                    // Background cards for stack effect (showing there's more)
                    if recentHistory.count >= 3 {
                        RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.medium)
                            .fill(Color.surfacePrimary)
                            .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
                            .offset(y: 8)
                            .padding(.horizontal, 8)
                    }

                    if recentHistory.count >= 2 {
                        RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.medium)
                            .fill(Color.surfacePrimary)
                            .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
                            .offset(y: 4)
                            .padding(.horizontal, 4)
                    }

                    // Top card with actual content
                    if let topItem = recentHistory.first {
                        HistoryCardView(item: topItem)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            .padding(.horizontal, DesignSystem.Spacing.standard)
            .padding(.bottom, DesignSystem.Spacing.small)
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
                    .overlay(
                        SVGWebView(svgName: "ic_image_placeholder", width: 32, height: 32, tintColor: .textPrimary)
                            .frame(width: 32, height: 32)
                    )
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
        .cornerRadius(DesignSystem.CornerRadius.medium)
        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd. MMM yyyy"
        return formatter.string(from: date)
    }
}

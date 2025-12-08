import SwiftUI

/// A stacked card view showing recent history on the main screen
struct HistoryStackView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    @ObservedObject var historyStorage: HistoryStorage
    let onTap: () -> Void

    var body: some View {
        if let topItem = historyStorage.history.first {
            VStack(alignment: .leading, spacing: 8) {
                // Header - 18sp, chivo_bold, text_primary
                Text(localizationManager.localize("identification_history", comment: "History"))
                    .font(.custom("Chivo-Bold", size: 18))
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
        HStack(spacing: 0) {
            // Thumbnail - 87x87dp
            if let firstPath = item.imagePaths.first,
               let image = HistoryStorage.shared.loadImage(at: firstPath) {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: 87, height: 87)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            } else {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color.surfaceSubtle)
                    .frame(width: 87, height: 87)
            }

            // Species info - paddingHorizontal 12dp
            VStack(alignment: .leading, spacing: 0) {
                // Name display
                let languageCode = localizationManager.currentLanguage == "system"
                    ? Locale.current.languageCode ?? "en"
                    : localizationManager.currentLanguage

                if let vernacularName = item.getVernacularName(for: languageCode), !vernacularName.isEmpty {
                    // Vernacular name: 18sp (matching results cards), text_accent, bold
                    Text(vernacularName.prefix(1).capitalized + vernacularName.dropFirst())
                        .font(DesignSystem.Typography.title())
                        .foregroundColor(Color.textAccent)
                        .lineLimit(1)
                        .padding(.bottom, 2)

                    if let scientificName = item.bestMatchScientificName {
                        // Scientific name: 14sp, text_secondary, italic, marginTop 2dp
                        Text(scientificName)
                            .font(.custom("Chivo-Italic", size: 14))
                            .foregroundColor(Color.textSecondary)
                            .lineLimit(1)
                            .padding(.top, 2)
                    }
                } else if let scientificName = item.bestMatchScientificName {
                    Text(scientificName)
                        .font(DesignSystem.Typography.title())
                        .italic()
                        .foregroundColor(Color.textAccent)
                        .lineLimit(1)
                        .padding(.bottom, 2)
                }

                // Timestamp: 12sp, text_secondary, marginTop 4dp
                Text(formatDate(item.timestamp))
                    .font(.custom("Chivo-Regular", size: 12))
                    .foregroundColor(Color.textSecondary)
                    .padding(.top, 4)
            }
            .padding(.horizontal, 12)

            Spacer()

            // Chevron - 36x36dp, tint surface_brand_1b
            SVGWebView(svgName: "ic_chevron_right", width: 36, height: 36, tintColor: .surfaceBrand1b)
                .frame(width: 36, height: 36)
        }
        .padding(6) // Card padding 6dp
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

        // Use the app's selected language for date formatting
        let languageCode = localizationManager.currentLanguage == "system"
            ? (Locale.current.languageCode ?? "en")
            : localizationManager.currentLanguage
        formatter.locale = Locale(identifier: languageCode)

        return formatter.string(from: date)
    }
}

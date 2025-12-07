import SwiftUI

/// Expanded history overlay showing all history items
struct ExpandedHistoryView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @ObservedObject var historyStorage: HistoryStorage
    @Binding var isPresented: Bool
    @Binding var isMenuOpen: Bool
    let onSelectItem: (IdentificationHistory) -> Void

    @State private var showSettings = false
    @State private var showAbout = false
    @State private var showFAQ = false
    @State private var itemToDelete: IdentificationHistory?

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // Header
                HStack(spacing: 0) {
                    // Back button
                    Button(action: { isPresented = false }) {
                        SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                            .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                    }
                    .padding(.leading, DesignSystem.Spacing.standard)

                    Spacer()

                    // Title (centered)
                    Text(localizationManager.localize("identification_history", comment: "History"))
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textPrimary)

                    Spacer()

                    // Menu button
                    Button(action: {
                        withAnimation {
                            isMenuOpen.toggle()
                        }
                    }) {
                        SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                            .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                    }
                    .padding(.trailing, DesignSystem.Spacing.standard)
                }
                .frame(height: DesignSystem.ComponentSize.headerHeight)
                .background(Color.surfacePrimary)

                Divider()
                    .frame(height: DesignSystem.ComponentSize.dividerHeight)
                    .background(Color.borderDefault)

                // Content
                ScrollView {
                    LazyVStack(spacing: DesignSystem.Spacing.small) {
                        ForEach(historyStorage.history) { item in
                            ExpandedHistoryRow(
                                item: item,
                                onTap: {
                                    onSelectItem(item)
                                },
                                onDelete: {
                                    itemToDelete = item
                                }
                            )
                        }
                    }
                    .padding(.horizontal, DesignSystem.Spacing.standard)
                    .padding(.vertical, DesignSystem.Spacing.small)
                }
                .background(Color.backgroundSubtle)
            }
            .background(Color.backgroundSubtle)

            if showSettings {
                SettingsView(isPresented: $showSettings, showMenuDrawer: $isMenuOpen)
                    .zIndex(1)
            }

            if showAbout {
                AboutView(isPresented: $showAbout, showMenuDrawer: $isMenuOpen)
                    .zIndex(1)
            }

            if showFAQ {
                FAQView(isPresented: $showFAQ, showMenuDrawer: $isMenuOpen)
                    .zIndex(1)
            }

            MenuDrawerView(isOpen: $isMenuOpen, showSettings: $showSettings, showAbout: $showAbout, showFAQ: $showFAQ)
                .zIndex(3)
        }
        .alert(localizationManager.localize("delete", comment: "Delete"), isPresented: Binding(
            get: { itemToDelete != nil },
            set: { if !$0 { itemToDelete = nil } }
        )) {
            Button(localizationManager.localize("cancel", comment: "Cancel"), role: .cancel) {
                itemToDelete = nil
            }
            Button(localizationManager.localize("delete", comment: "Delete"), role: .destructive) {
                if let item = itemToDelete {
                    historyStorage.deleteHistoryItem(item)
                }
                itemToDelete = nil
            }
        } message: {
            Text(localizationManager.localize("clear_history_confirmation_message", comment: "Are you sure?"))
        }
    }
}

/// Individual row in expanded history list
struct ExpandedHistoryRow: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let item: IdentificationHistory
    let onTap: () -> Void
    let onDelete: () -> Void

    @State private var offset: CGFloat = 0
    @State private var showDeleteButton = false

    var body: some View {
        ZStack(alignment: .trailing) {
            // Delete button background
            if showDeleteButton {
                Button(action: onDelete) {
                    HStack {
                        Spacer()
                        SVGWebView(svgName: "ic_delete", width: 24, height: 24, tintColor: .white)
                            .frame(width: 24, height: 24)
                    }
                    .frame(width: 80)
                    .frame(maxHeight: .infinity)
                    .background(Color.alertDangerBorderPrimary)
                    .cornerRadius(DesignSystem.CornerRadius.medium)
                }
            }

            // Main content
            Button(action: onTap) {
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
                .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(PlainButtonStyle())
            .offset(x: offset)
            .gesture(
                DragGesture()
                    .onChanged { value in
                        if value.translation.width < 0 {
                            offset = max(value.translation.width, -80)
                        } else if showDeleteButton {
                            offset = min(value.translation.width - 80, 0)
                        }
                    }
                    .onEnded { value in
                        withAnimation(.spring()) {
                            if value.translation.width < -40 {
                                offset = -80
                                showDeleteButton = true
                            } else {
                                offset = 0
                                showDeleteButton = false
                            }
                        }
                    }
            )
        }
    }

    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd. MMM yyyy"
        return formatter.string(from: date)
    }
}

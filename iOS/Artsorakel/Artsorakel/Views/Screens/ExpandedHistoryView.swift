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

                    // Title (centered) - 18sp regular like Android
                    Text(localizationManager.localize("identification_history", comment: "History"))
                        .font(DesignSystem.Typography.titleRegular())
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
                    LazyVStack(spacing: 6) { // 6dp to match Android's 3dp vertical margin per card
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
        .alert(localizationManager.localize("delete_item", comment: "Delete item"), isPresented: Binding(
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
            Text(localizationManager.localize("delete_item_confirmation", comment: "Are you sure you want to delete this item?"))
        }
    }
}

/// Individual row in expanded history list - reuses HistoryCardView
/// Swipe right to reveal delete button on the left (matching Android behavior)
struct ExpandedHistoryRow: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let item: IdentificationHistory
    let onTap: () -> Void
    let onDelete: () -> Void

    // Swipe state
    @State private var offset: CGFloat = 0
    @State private var isRevealed = false
    @State private var isDragging = false

    // Constants matching Android
    private let maxSwipeDistance: CGFloat = 80 // Width to reveal delete button
    private let swipeThreshold: CGFloat = 40 // Minimum swipe to trigger reveal

    var body: some View {
        ZStack(alignment: .leading) {
            // Delete button background (on the left, behind the card)
            Button(action: {
                // Delete action
                onDelete()
                // Close the swipe after delete
                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                    offset = 0
                    isRevealed = false
                }
            }) {
                ZStack {
                    // 40dp circle with 3dp border, matching Android FAB
                    Circle()
                        .fill(Color.backgroundDefault)
                        .frame(width: 40, height: 40)
                        .overlay(
                            Circle()
                                .stroke(Color.surfaceBrand1b, lineWidth: 3)
                        )

                    SVGWebView(svgName: "ic_delete", width: 20, height: 20, tintColor: .surfaceBrand1b)
                        .frame(width: 20, height: 20)
                }
            }
            .frame(width: maxSwipeDistance)
            .opacity(offset > 0 ? 1 : 0)

            // Main content - reuse HistoryCardView
            HistoryCardView(item: item)
                .contentShape(Rectangle())
                .offset(x: offset)
                .simultaneousGesture(
                    DragGesture(minimumDistance: 10, coordinateSpace: .local)
                        .onChanged { value in
                            // Only handle horizontal swipes
                            let horizontalAmount = abs(value.translation.width)
                            let verticalAmount = abs(value.translation.height)

                            // If this is more vertical than horizontal, ignore it (let ScrollView handle it)
                            if verticalAmount > horizontalAmount && !isDragging {
                                return
                            }

                            isDragging = true
                            let translation = value.translation.width

                            if isRevealed {
                                // When revealed, allow swiping back (left) to close
                                if translation < 0 {
                                    offset = max(maxSwipeDistance + translation, 0)
                                } else {
                                    // Don't allow swiping further right when already revealed
                                    offset = maxSwipeDistance
                                }
                            } else {
                                // When closed, only allow swiping right to reveal
                                if translation > 0 {
                                    offset = min(translation, maxSwipeDistance)
                                }
                            }
                        }
                        .onEnded { value in
                            let wasDragging = isDragging
                            isDragging = false

                            if !wasDragging {
                                return
                            }

                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                if isRevealed {
                                    // If already revealed, check if we should close
                                    if offset < maxSwipeDistance - swipeThreshold {
                                        offset = 0
                                        isRevealed = false
                                    } else {
                                        offset = maxSwipeDistance
                                    }
                                } else {
                                    // If not revealed, check if we should reveal
                                    if offset > swipeThreshold {
                                        offset = maxSwipeDistance
                                        isRevealed = true
                                    } else {
                                        offset = 0
                                    }
                                }
                            }
                        }
                )
                .onTapGesture {
                    if isRevealed {
                        // If swiped, close on tap
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            offset = 0
                            isRevealed = false
                        }
                    } else {
                        // Normal tap action
                        onTap()
                    }
                }
        }
        .clipped()
    }
}

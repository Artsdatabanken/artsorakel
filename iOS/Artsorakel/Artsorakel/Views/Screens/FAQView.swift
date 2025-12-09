import SwiftUI

struct FAQItem: Codable, Identifiable {
    let question: String
    let answer: String

    var id: String { question }
}

struct FAQData: Codable {
    let items: [FAQItem]
}

struct FAQView: View {
    @Binding var isPresented: Bool
    @Binding var showMenuDrawer: Bool
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @State private var faqItems: [FAQItem] = []
    @State private var expandedItems: Set<String> = []

    var body: some View {
        ZStack {
            // Background
            Color.backgroundDefault
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack(spacing: 0) {
                    // Back button
                    Button(action: {
                        withAnimation {
                            isPresented = false
                        }
                    }) {
                        SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                            .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                    }
                    .padding(.leading, DesignSystem.Spacing.standard)

                    Spacer()

                    // Title
                    Text(localizationManager.localize("faq", comment: "FAQ"))
                        .font(DesignSystem.Typography.titleRegular())
                        .foregroundColor(Color.textPrimary)

                    Spacer()

                    // Menu button
                    Button(action: {
                        withAnimation {
                            showMenuDrawer = true
                        }
                    }) {
                        SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                            .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                    }
                    .padding(.trailing, DesignSystem.Spacing.standard)
                }
                .frame(height: DesignSystem.ComponentSize.headerHeight)
                .background(Color.backgroundDefault)

                // Scrollable FAQ content
                ScrollView {
                    VStack(spacing: DesignSystem.Spacing.small) {
                        ForEach(faqItems) { item in
                            FAQItemView(
                                item: item,
                                isExpanded: expandedItems.contains(item.id)
                            ) {
                                withAnimation(.easeInOut(duration: 0.2)) {
                                    if expandedItems.contains(item.id) {
                                        expandedItems.remove(item.id)
                                    } else {
                                        expandedItems.insert(item.id)
                                    }
                                }
                            }
                        }
                    }
                    .padding(DesignSystem.Spacing.standard)
                }
                .background(Color.backgroundSubtle)
            }
        }
        .onAppear {
            loadFAQItems()
        }
    }

    private func loadFAQItems() {
        let lang = localizationManager.currentLanguage == "system"
            ? Locale.current.languageCode ?? "en"
            : localizationManager.currentLanguage

        let fileName = "faq_\(lang)"

        // Try to load the FAQ JSON file
        if let url = Bundle.main.url(forResource: fileName, withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            do {
                let decoder = JSONDecoder()
                let faqData = try decoder.decode(FAQData.self, from: data)
                faqItems = faqData.items
                return
            } catch {
                // Fall through to try English
            }
        }

        // Fallback to English
        if let url = Bundle.main.url(forResource: "faq_en", withExtension: "json"),
           let data = try? Data(contentsOf: url) {
            do {
                let decoder = JSONDecoder()
                let faqData = try decoder.decode(FAQData.self, from: data)
                faqItems = faqData.items
            } catch {
                // FAQ will remain empty
            }
        }
    }
}

struct FAQItemView: View {
    let item: FAQItem
    let isExpanded: Bool
    let onTap: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Question header (always visible)
            Button(action: onTap) {
                HStack(spacing: DesignSystem.Spacing.medium) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: DesignSystem.IconSize.small))
                        .foregroundColor(Color.textAccent)
                        .rotationEffect(.degrees(isExpanded ? 180 : 0))

                    Text(item.question)
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textPrimary)
                        .multilineTextAlignment(.leading)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(DesignSystem.Spacing.standard)
                .background(Color.surfacePrimary)
                .cornerRadius(DesignSystem.CornerRadius.medium)
            }
            .buttonStyle(PlainButtonStyle())

            // Answer (conditionally visible)
            if isExpanded {
                Text(item.answer)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary.opacity(DesignSystem.Opacity.subtle))
                    .multilineTextAlignment(.leading)
                    .padding(DesignSystem.Spacing.standard)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.surfacePrimary.opacity(0.5))
                    .cornerRadius(DesignSystem.CornerRadius.medium)
                    .padding(.top, DesignSystem.Spacing.xxSmall)
                    .transition(.opacity.combined(with: .scale(scale: 0.95, anchor: .top)))
            }
        }
    }
}

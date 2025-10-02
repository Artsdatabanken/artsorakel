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
                        if resourceExists("ic_arrow_back") {
                            SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.medium, height: DesignSystem.IconSize.medium, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.medium, height: DesignSystem.IconSize.medium)
                        } else {
                            Image(systemName: "chevron.left")
                                .font(.system(size: DesignSystem.IconSize.medium))
                                .foregroundColor(Color.textAccent)
                        }
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.leading, DesignSystem.Spacing.small)

                    // Title
                    Text(localizationManager.localize("faq", comment: "FAQ"))
                        .font(DesignSystem.Typography.title())
                        .foregroundColor(Color.textPrimary)
                        .frame(maxWidth: .infinity)

                    // Menu button
                    Button(action: {
                        withAnimation {
                            showMenuDrawer = true
                        }
                    }) {
                        if resourceExists("ic_menu") {
                            SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                        } else {
                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: DesignSystem.IconSize.standard))
                                .foregroundColor(Color.textAccent)
                        }
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.trailing, DesignSystem.Spacing.small)
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
                print("Error decoding FAQ JSON: \(error)")
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
                print("Error decoding fallback FAQ JSON: \(error)")
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

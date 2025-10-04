import SwiftUI

struct ResultsView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    let results: [PredictionResult]
    let images: [CroppedImageData]
    let onReset: () -> Void
    @Binding var isMenuOpen: Bool

    var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 0) {
                // Back button
                Button(action: onReset) {
                    if resourceExists("ic_arrow_back") {
                        SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                    } else {
                        Image(systemName: "arrow.left")
                            .font(.system(size: DesignSystem.IconSize.standard))
                            .foregroundColor(Color.textAccent)
                    }
                }
                .frame(width: DesignSystem.ButtonSize.standard, height: 60)
                .padding(.leading, DesignSystem.Spacing.standard)

                Spacer()

                // Title (centered)
                Text(localizationManager.localize("results", comment: "Results"))
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary)

                Spacer()

                // Menu button
                Button(action: {
                    withAnimation {
                        isMenuOpen.toggle()
                    }
                }) {
                    Image(systemName: "line.3.horizontal")
                        .font(.system(size: DesignSystem.IconSize.medium))
                        .foregroundColor(Color.textAccent)
                        .frame(width: DesignSystem.ButtonSize.standard, height: 60)
                }
                .padding(.trailing, DesignSystem.Spacing.standard)
            }
            .frame(height: DesignSystem.ComponentSize.headerHeight)
            .background(Color.surfacePrimary)

            Divider()
                .frame(height: DesignSystem.ComponentSize.dividerHeight)
                .background(Color.borderDefault)

            // Images at top
            VStack(spacing: DesignSystem.Spacing.standard) {
                GeometryReader { geometry in
                    ScrollViewReader { proxy in
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: DesignSystem.Spacing.small) {
                                ForEach(images) { imageData in
                                    Image(uiImage: imageData.image)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 90, height: 90)
                                        .cornerRadius(DesignSystem.CornerRadius.small)
                                        .clipped()
                                }

                                // Add button placeholder
                                ZStack {
                                    RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.small)
                                        .strokeBorder(Color.textAccent, style: StrokeStyle(lineWidth: 2, dash: [5, 5]))
                                        .frame(width: 90, height: 90)

                                    if resourceExists("ic_add") {
                                        SVGWebView(svgName: "ic_add", width: 24, height: 24, tintColor: .textAccent)
                                            .frame(width: 24, height: 24)
                                    } else {
                                        Image(systemName: "plus")
                                            .font(.system(size: 24))
                                            .foregroundColor(Color.textAccent)
                                    }
                                }
                                .frame(width: 90, height: 90)
                                .id("addButton")
                            }
                            .padding(.horizontal, max(DesignSystem.Spacing.small, (geometry.size.width - DesignSystem.Spacing.small * 2 - CGFloat(images.count + 1) * 90 - CGFloat(images.count) * DesignSystem.Spacing.small) / 2))
                        }
                        .onAppear {
                            proxy.scrollTo("addButton", anchor: .trailing)
                        }
                    }
                    .padding(.horizontal, DesignSystem.Spacing.small)
                }
                .frame(height: 90)
            }
            .padding(.vertical, DesignSystem.Spacing.standard)
            .background(Color.surfaceSecondary)

            // Results list
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(results) { result in
                        ResultRow(result: result)
                        Divider()
                            .background(Color.borderDefault)
                    }
                }
            }
            .background(Color.backgroundSubtle)

            // Reset button at bottom
            Button(action: onReset) {
                Text(localizationManager.localize("reset", comment: "Reset"))
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textAccent)
                    .padding(.horizontal, DesignSystem.Spacing.large)
                    .padding(.vertical, DesignSystem.Spacing.standard)
            }
            .background(Color.surfacePrimary)
            .overlay(
                RoundedRectangle(cornerRadius: 28)
                    .stroke(Color.borderAccent, lineWidth: 2)
            )
            .cornerRadius(28)
            .padding(.vertical, DesignSystem.Spacing.standard)
            .shadow(radius: 8)
        }
        .background(Color.backgroundSubtle)
    }
}

struct ResultRow: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let result: PredictionResult

    var body: some View {
        HStack(spacing: DesignSystem.Spacing.standard) {
            // Placeholder for image (64x64)
            Circle()
                .fill(Color.surfaceSubtle)
                .frame(width: 64, height: 64)

            VStack(alignment: .leading, spacing: 4) {
                // Vernacular name (if available)
                if let vernacularName = result.getVernacularName(for: Locale.current.languageCode ?? "en"), !vernacularName.isEmpty {
                    Text(vernacularName)
                        .font(DesignSystem.Typography.title())
                        .foregroundColor(Color.surfaceAccent)
                }

                // Scientific name
                if let scientificName = result.scientificName {
                    Text(scientificName)
                        .font(DesignSystem.Typography.caption())
                        .italic()
                        .foregroundColor(Color.textPrimary)
                }

                // Group name (if available)
                if let groupName = result.getGroupName(for: Locale.current.languageCode ?? "en"), !groupName.isEmpty {
                    Text(groupName)
                        .font(DesignSystem.Typography.caption())
                        .foregroundColor(Color.textPrimary)
                }

                // Probability gauge (simplified)
                GeometryReader { geometry in
                    ZStack(alignment: .leading) {
                        Rectangle()
                            .fill(Color.surfaceSubtle)
                            .frame(height: 8)
                            .cornerRadius(4)

                        Rectangle()
                            .fill(Color.surfaceAccent)
                            .frame(width: geometry.size.width * CGFloat(result.probability), height: 8)
                            .cornerRadius(4)
                    }
                }
                .frame(height: 8)
            }

            Spacer()

            // Arrow
            if resourceExists("ic_chevron_right") {
                SVGWebView(svgName: "ic_chevron_right", width: 24, height: 24, tintColor: .surfaceBrand1b)
            } else {
                Image(systemName: "chevron.right")
                    .foregroundColor(Color.surfaceAccent)
            }
        }
        .padding(DesignSystem.Spacing.standard)
        .background(Color.surfacePrimary)
    }
}

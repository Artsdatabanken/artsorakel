import SwiftUI

struct ResultsView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    let results: [PredictionResult]
    let images: [CroppedImageData]
    let onReset: () -> Void
    let onAddImage: () -> Void
    let onImageTap: (CroppedImageData) -> Void
    let onResultTap: (PredictionResult) -> Void
    @Binding var isMenuOpen: Bool

    var body: some View {
        VStack(spacing: 0) {
            headerView

            Divider()
                .frame(height: DesignSystem.ComponentSize.dividerHeight)
                .background(Color.borderDefault)

            imagesSection

            resultsListView

            resetButton
        }
        .background(Color.backgroundSubtle)
    }

    private var headerView: some View {
        HStack(spacing: 0) {
            Button(action: onReset) {
                SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
            }
            .frame(width: DesignSystem.ButtonSize.standard, height: 60)
            .padding(.leading, DesignSystem.Spacing.standard)

            Spacer()

            Text(localizationManager.localize("results", comment: "Results"))
                .font(DesignSystem.Typography.body())
                .foregroundColor(Color.textPrimary)

            Spacer()

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
    }

    private var imagesSection: some View {
        VStack(spacing: DesignSystem.Spacing.standard) {
            GeometryReader { geometry in
                ScrollViewReader { proxy in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: DesignSystem.Spacing.small) {
                            ForEach(images) { imageData in
                                Button(action: {
                                    onImageTap(imageData)
                                }) {
                                    Image(uiImage: imageData.image)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 90, height: 90)
                                        .cornerRadius(DesignSystem.CornerRadius.small)
                                        .clipped()
                                }
                            }

                            Button(action: onAddImage) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.small)
                                        .strokeBorder(Color.borderAccent, style: StrokeStyle(lineWidth: 2, dash: [4, 4]))
                                        .frame(width: 90, height: 90)

                                    SVGWebView(svgName: "ic_add", width: 24, height: 24, tintColor: .textAccent)
                                        .frame(width: 24, height: 24)
                                }
                                .frame(width: 90, height: 90)
                            }
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
    }

    private var resetButton: some View {
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

    private var resultsListView: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(results) { result in
                    ResultRow(result: result)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            onResultTap(result)
                        }
                    Divider()
                        .background(Color.borderDefault)
                }
            }
        }
        .background(Color.backgroundSubtle)
    }
}

struct ResultRow: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let result: PredictionResult

    var body: some View {
        let languageCode = localizationManager.currentLanguage == "system"
            ? Locale.current.languageCode ?? "en"
            : localizationManager.currentLanguage

        HStack(spacing: 0) {
            // Placeholder for image (64x64)
            Circle()
                .fill(Color.surfaceSubtle)
                .frame(width: 64, height: 64)

            VStack(alignment: .leading, spacing: 4) {
                // Vernacular name or scientific name as header
                if let vernacularName = result.getVernacularName(for: languageCode), !vernacularName.isEmpty {
                    // Show vernacular name as header
                    Text(vernacularName.prefix(1).capitalized + vernacularName.dropFirst())
                        .font(DesignSystem.Typography.title())
                        .foregroundColor(Color.surfaceAccent)

                    // Show scientific name below only if different from vernacular
                    if let scientificName = result.scientificName, scientificName != vernacularName {
                        Text(scientificName)
                            .font(DesignSystem.Typography.caption())
                            .italic()
                            .foregroundColor(Color.textPrimary)
                    }
                } else if let scientificName = result.scientificName {
                    // No vernacular name - show scientific name as header in italic
                    Text(scientificName)
                        .font(DesignSystem.Typography.title())
                        .italic()
                        .foregroundColor(Color.surfaceAccent)
                }

                // Group name (if available)
                if let groupName = result.getGroupName(for: languageCode), !groupName.isEmpty {
                    Text(groupName)
                        .font(DesignSystem.Typography.caption())
                        .foregroundColor(Color.textPrimary)
                }

                // Certainty circles
                CertaintyCircles(probability: result.probability)
                    .frame(height: 14)
                    .padding(.top, 4)
            }
            .padding(.leading, DesignSystem.Spacing.standard)
            .padding(.trailing, DesignSystem.Spacing.small)

            Spacer()

                SVGWebView(svgName: "ic_chevron_right", width: 36, height: 36, tintColor: .surfaceBrand1b)
                    .frame(width: 36, height: 36)
           
        }
        .padding(DesignSystem.Spacing.standard)
        .background(Color.surfacePrimary)
    }
}

struct CertaintyCircles: View {
    let probability: Double

    private let thresholds: [Double] = [0.35, 0.65, 0.85, 0.95]

    private let colors: [Color] = [
        Color(red: 170/255, green: 0/255, blue: 0/255),
        Color(red: 195/255, green: 107/255, blue: 22/255),
        Color(red: 220/255, green: 214/255, blue: 43/255),
        Color(red: 148/255, green: 195/255, blue: 62/255),
        Color(red: 76/255, green: 175/255, blue: 80/255)
    ]

    private let circleDiameter: CGFloat = 14
    private var gapSize: CGFloat { circleDiameter / 5 }

    var body: some View {
        HStack(spacing: gapSize) {
            ForEach(0..<5, id: \.self) { index in
                Circle()
                    .fill(circleColor(for: index))
                    .frame(width: circleDiameter, height: circleDiameter)
            }
            Spacer()
        }
    }

    private func circleColor(for index: Int) -> Color {
        let filledCount = getFilledCount()

        if index < filledCount {
            // Filled circle - use color based on filled count
            return colors[filledCount - 1]
        } else {
            // Unfilled circle - use surface_subtle with 1/3 opacity
            return Color.surfaceSubtle.opacity(1.0/3.0)
        }
    }

    private func getFilledCount() -> Int {
        // First circle is always filled
        var count = 1

        // Check each threshold
        for threshold in thresholds {
            if probability > threshold {
                count += 1
            }
        }

        return min(count, 5)
    }
}

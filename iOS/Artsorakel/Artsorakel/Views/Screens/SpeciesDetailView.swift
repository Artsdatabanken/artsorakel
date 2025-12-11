import SwiftUI

struct SpeciesDetailView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    let result: PredictionResult
    let images: [CroppedImageData]
    var uploadId: String?
    var uploadSecret: String?
    var identificationTimestamp: Date?
    var isHistorical: Bool = false
    let onClose: () -> Void
    @Binding var isMenuOpen: Bool
    @State private var currentImageIndex = 0
    @State private var showSettings = false
    @State private var showAbout = false
    @State private var showFAQ = false
    @State private var isUploading = false
    @State private var showReportDialog = false

    var body: some View {
        ZStack {
        VStack(spacing: 0) {
            // Header
            HStack(spacing: 0) {
                // Back button
                Button(action: onClose) {
                    SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                        .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                }
                .padding(.leading, DesignSystem.Spacing.standard)

                Spacer()

                // Title (centered) - 18sp regular like Android
                Text(localizationManager.localize("details", comment: "Details"))
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

            // Content
            ScrollView {
                VStack(spacing: 0) {
                    // Image Carousel
                    if images.count > 0 {
                        TabView(selection: $currentImageIndex) {
                            ForEach(Array(images.enumerated()), id: \.element.id) { index, imageData in
                                Image(uiImage: imageData.image)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: UIScreen.main.bounds.width / 2)
                                    .clipped()
                                    .tag(index)
                            }
                        }
                        .frame(height: UIScreen.main.bounds.width / 2)
                        .tabViewStyle(PageTabViewStyle(indexDisplayMode: .never))
                        .overlay(
                            // Indicator dots
                            VStack {
                                Spacer()
                                if images.count > 1 {
                                    HStack(spacing: 4) {
                                        ForEach(0..<images.count, id: \.self) { index in
                                            Circle()
                                                .fill(index == currentImageIndex ? Color.textInvert : Color.neutralBorderDefault)
                                                .frame(width: index == currentImageIndex ? 8 : 6, height: index == currentImageIndex ? 8 : 6)
                                        }
                                    }
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 6)
                                    .background(Color.surfaceInvert.opacity(0.9))
                                    .cornerRadius(12)
                                    .padding(.bottom, 12)
                                }
                            }
                        )
                    }

                    // Species Info Section
                    HStack(alignment: .center, spacing: 16) {
                        // Circular Profile Image
                        if let pictureUrl = result.pictureUrl, let url = URL(string: pictureUrl) {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 80, height: 80)
                                        .clipShape(Circle())
                                case .failure(_), .empty:
                                    SpeciesDetailPlaceholderView(placeholderName: result.getPlaceholderName())
                                @unknown default:
                                    SpeciesDetailPlaceholderView(placeholderName: result.getPlaceholderName())
                                }
                            }
                        } else {
                            SpeciesDetailPlaceholderView(placeholderName: result.getPlaceholderName())
                        }

                        // Names Container
                        VStack(alignment: .leading, spacing: 4) {
                            // Vernacular name or scientific name as header
                            let languageCode = localizationManager.currentLanguage == "system"
                                ? Locale.current.languageCode ?? "en"
                                : localizationManager.currentLanguage

                            if let vernacularName = result.getVernacularName(for: languageCode), !vernacularName.isEmpty {
                                Text(vernacularName.prefix(1).capitalized + vernacularName.dropFirst())
                                    .font(DesignSystem.Typography.titleLarge())
                                    .foregroundColor(Color.textPrimary)

                                if let scientificName = result.scientificName, scientificName != vernacularName {
                                    Text(scientificName)
                                        .font(DesignSystem.Typography.subheadline())
                                        .italic()
                                        .foregroundColor(Color.textPrimary)
                                }
                            } else if let scientificName = result.scientificName {
                                Text(scientificName)
                                    .font(DesignSystem.Typography.titleLarge())
                                    .italic()
                                    .foregroundColor(Color.textPrimary)
                            }

                            // Group name
                            if let groupName = result.getGroupName(for: languageCode), !groupName.isEmpty {
                                Text(groupName)
                                    .font(DesignSystem.Typography.caption())
                                    .foregroundColor(Color.textPrimary)
                            }
                        }

                        Spacer()
                    }
                    .padding(DesignSystem.Spacing.xLarge)
                    .background(Color.backgroundSubtle)

                    // Category Badges
                    if redListColor(for: result.redListCategory) != nil || invasiveColor(for: result.invasiveCategory) != nil {
                        HStack(spacing: DesignSystem.Spacing.standard) {
                            if let redListCategory = result.redListCategory,
                               let color = redListColor(for: redListCategory) {
                                CategoryBadge(
                                    code: redListCategory,
                                    name: localizationManager.localize("redlist_\(redListCategory.lowercased())", comment: ""),
                                    color: color
                                )
                            }

                            if let invasiveCategory = result.invasiveCategory,
                               let color = invasiveColor(for: invasiveCategory) {
                                CategoryBadge(
                                    code: invasiveCategory,
                                    name: localizationManager.localize("invasive_\(invasiveCategory.lowercased())", comment: ""),
                                    color: color
                                )
                            }

                            Spacer()
                        }
                        .padding(.horizontal, DesignSystem.Spacing.standard)
                        .padding(.bottom, DesignSystem.Spacing.standard)
                        .background(Color.backgroundSubtle)
                    }

                    // Certainty Text
                    let certaintyPercentage = Int(result.probability * 100)
                    let certaintyParameter = getCertaintyParameter()

                    // Replace Android positional format specifiers with Swift format specifiers
                    let formatString = localizationManager.localize("certainty_text", comment: "")
                        .replacingOccurrences(of: "%1$d", with: "%d")
                        .replacingOccurrences(of: "%2$s", with: "%@")

                    let certaintyText = String(format: formatString, certaintyPercentage, certaintyParameter)

                    Text(certaintyText.strippingHTMLTags())
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textPrimary)
                        .padding(DesignSystem.Spacing.standard)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.surfacePrimary)

                    // Read More Link
                    if let infoUrl = result.infoUrl, !infoUrl.isEmpty, let url = URL(string: infoUrl) {
                        let languageCode = localizationManager.currentLanguage == "system"
                            ? Locale.current.languageCode ?? "en"
                            : localizationManager.currentLanguage
                        let displayName = result.getVernacularName(for: languageCode) ?? result.scientificName ?? localizationManager.localize("unknown_species", comment: "")

                        // Replace Android positional format specifiers with Swift format specifiers
                        let formatString = localizationManager.localize("read_more_detailed", comment: "")
                            .replacingOccurrences(of: "%1$s", with: "%@")

                        let readMoreText = String(format: formatString, displayName)

                        Button(action: {
                            UIApplication.shared.open(url)
                        }) {
                            (Text(readMoreText) + Text(" ") + Text(Image("ic_external_link")).baselineOffset(-4))
                                .font(DesignSystem.Typography.subheadline())
                                .foregroundColor(Color.textAccent)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(DesignSystem.Spacing.standard)
                        }
                    }

                    // Distribution Map Section
                    if let extractedId = extractIdAfterColon(result.id),
                       !extractedId.isEmpty {

                        VStack(alignment: .leading, spacing: 12) {
                            Text(localizationManager.localize("distribution_title", comment: "Observations in Norway"))
                                .font(DesignSystem.Typography.subheadlineBold())
                                .foregroundColor(Color.textPrimary)

                            let distributionUrl = "https://artskart.artsdatabanken.no/appapi/api/raster/distribution/?BBOX=-350770,6400000,1100000,9000000&height=800&width=500&ScientificNameId=\(extractedId)"

                            if let url = URL(string: distributionUrl) {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(maxWidth: .infinity)
                                            .background(Color.surfacePrimary)
                                            .cornerRadius(DesignSystem.CornerRadius.medium)
                                    case .failure(_):
                                        EmptyView()
                                    case .empty:
                                        ZStack {
                                            Rectangle()
                                                .fill(Color.surfacePrimary)
                                                .cornerRadius(DesignSystem.CornerRadius.medium)
                                            ProgressView()
                                        }
                                        .frame(height: 300)
                                    @unknown default:
                                        EmptyView()
                                    }
                                }
                            }
                        }
                        .padding(DesignSystem.Spacing.standard)
                    }

                    // Report Link (only for Norway observations)
                    if let extractedId = extractIdAfterColon(result.id),
                       !extractedId.isEmpty,
                       result.modelInfo?.country == "NO" || result.modelInfo?.country == "Norway" {

                        Button(action: {
                            showReportDialog = true
                        }) {
                            Group {
                                if isUploading {
                                    HStack(spacing: 8) {
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: Color.textAccent))
                                            .scaleEffect(0.8)
                                        Text(localizationManager.localize("report_uploading", comment: "Uploading..."))
                                            .font(DesignSystem.Typography.subheadline())
                                            .foregroundColor(Color.textAccent)
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                } else {
                                    (Text(localizationManager.localize("report", comment: "Report on artsobservasjoner.no")) + Text(" ") + Text(Image("ic_external_link")).baselineOffset(-4))
                                        .font(DesignSystem.Typography.subheadline())
                                        .foregroundColor(Color.textAccent)
                                        .multilineTextAlignment(.leading)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                            .padding(DesignSystem.Spacing.standard)
                        }
                        .disabled(isUploading)
                        .padding(.top, DesignSystem.Spacing.standard)
                    }

                    // Bottom spacing
                    Spacer()
                        .frame(height: DesignSystem.Spacing.xLarge)
                }
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
        .alert(localizationManager.localize("report_dialog_title", comment: "Report observation"), isPresented: $showReportDialog) {
            Button(localizationManager.localize("cancel", comment: "Cancel"), role: .cancel) { }
            Button(localizationManager.localize("report_dialog_continue", comment: "Continue")) {
                uploadImagesAndReport()
            }
        } message: {
            Text(localizationManager.localize("report_dialog_message", comment: "You will be redirected to artsobservasjoner.no"))
        }
    }

    private func uploadImagesAndReport() {
        guard let extractedId = extractIdAfterColon(result.id) else { return }

        // Check if we have fresh upload credentials (less than 25 minutes old)
        if let uploadId = uploadId,
           let uploadSecret = uploadSecret,
           let timestamp = identificationTimestamp {
            let ageMinutes = Date().timeIntervalSince(timestamp) / 60
            if ageMinutes < 25 {
                // Upload credentials are fresh, use them directly
                openReportUrl(scientificNameId: extractedId, imageId: uploadId, password: uploadSecret)
                return
            }
        }

        // Upload credentials are stale or not available, need to get new ones
        let imagesToUpload = images.map { $0.image }

        if imagesToUpload.isEmpty {
            // No images, open URL without image reference
            openReportUrl(scientificNameId: extractedId, imageId: nil, password: nil)
            return
        }

        isUploading = true

        Task {
            do {
                let response = try await SpeciesAPIService.shared.saveImagesForReport(images: imagesToUpload)

                await MainActor.run {
                    isUploading = false
                    openReportUrl(scientificNameId: extractedId, imageId: response.id, password: response.password)
                }
            } catch {
                await MainActor.run {
                    isUploading = false
                    // Open URL without image reference on failure
                    openReportUrl(scientificNameId: extractedId, imageId: nil, password: nil)
                }
            }
        }
    }

    private func openReportUrl(scientificNameId: String, imageId: String?, password: String?) {
        var urlString = "https://mobil.artsobservasjoner.no/contribute/submit-sightings?ReportByScientificName=\(scientificNameId)"

        if let imageId = imageId, let password = password {
            urlString += "&id=\(imageId)&password=\(password)"
        }

        if let url = URL(string: urlString) {
            UIApplication.shared.open(url)
        }
    }


    private func getCertaintyParameter() -> String {
        let languageCode = localizationManager.currentLanguage == "system"
            ? Locale.current.languageCode ?? "en"
            : localizationManager.currentLanguage

        if let vernacularName = result.getVernacularName(for: languageCode), !vernacularName.isEmpty {
            return vernacularName
        } else if let scientificName = result.scientificName {
            return "<i>\(scientificName)</i>"
        } else {
            return localizationManager.localize("unknown_species", comment: "this species")
        }
    }

    private func extractIdAfterColon(_ fullId: String) -> String? {
        // First, remove the probability suffix if present (format: "NBIC:12345_0.95")
        let idWithoutProbability = fullId.split(separator: "_").first.map(String.init) ?? fullId

        // Then extract the ID after the colon
        let components = idWithoutProbability.split(separator: ":")
        if components.count > 1 {
            let id = String(components[1])
            return id.isEmpty ? nil : id
        }
        return nil
    }

    private func redListColor(for category: String?) -> Color? {
        guard let category = category else { return nil }
        switch category.uppercased() {
        case "CR": return Color("Color_redlistCr")
        case "EN": return Color("Color_redlistEn")
        case "VU": return Color("Color_redlistVu")
        case "NT": return Color("Color_redlistNt")
        case "DD": return Color("Color_redlistDd")
        case "LC": return Color("Color_redlistLc")
        default: return nil
        }
    }

    private func invasiveColor(for category: String?) -> Color? {
        guard let category = category else { return nil }
        switch category.uppercased() {
        case "SE": return Color("Color_invasiveSe")
        case "HI": return Color("Color_invasiveHi")
        case "PH": return Color("Color_invasivePh")
        case "LO": return Color("Color_invasiveLo")
        case "NK": return Color("Color_invasiveNk")
        default: return nil
        }
    }
}

struct CategoryBadge: View {
    let code: String
    let name: String
    let color: Color

    var body: some View {
        HStack(spacing: 8) {
            // Round circle with code
            ZStack {
                Circle()
                    .fill(color)
                    .frame(width: 32, height: 32)

                Text(code.uppercased())
                    .font(DesignSystem.Typography.caption())
                    .fontWeight(.bold)
                    .foregroundColor(.white)
            }

            // Label outside the circle
            Text(name)
                .font(DesignSystem.Typography.body())
                .foregroundColor(Color.textPrimary)
        }
    }
}

struct SpeciesDetailPlaceholderView: View {
    let placeholderName: String

    var body: some View {
        SVGWebView(svgName: placeholderName, width: 80, height: 80)
            .frame(width: 80, height: 80)
            .clipShape(Circle())
    }
}


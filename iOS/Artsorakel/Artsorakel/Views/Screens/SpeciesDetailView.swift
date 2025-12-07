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
                                    HStack(spacing: 6) {
                                        ForEach(0..<images.count, id: \.self) { index in
                                            Circle()
                                                .fill(index == currentImageIndex ? Color.surfaceAccent : Color.textPrimary.opacity(1.0/3.0))
                                                .frame(width: index == currentImageIndex ? 14 : 10.5, height: index == currentImageIndex ? 14 : 10.5)
                                        }
                                    }
                                    .padding(8)
                                    .background(Color.surfacePrimary.opacity(0.9))
                                    .cornerRadius(16)
                                    .padding(.bottom, 16)
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
                                    placeholderImage
                                @unknown default:
                                    placeholderImage
                                }
                            }
                        } else {
                            placeholderImage
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

                    Text(certaintyText.htmlToAttributedString() ?? certaintyText)
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
                            HStack(spacing: 8) {
                                Text(readMoreText)
                                    .font(DesignSystem.Typography.subheadline())
                                    .foregroundColor(Color.textAccent)
                                SVGWebView(svgName: "ic_external_link", width: 16, height: 16, tintColor: .textAccent)
                                    .frame(width: 16, height: 16)
                            }
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
                            HStack(spacing: 8) {
                                if isUploading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: Color.textAccent))
                                        .scaleEffect(0.8)
                                    Text(localizationManager.localize("report_uploading", comment: "Uploading..."))
                                        .font(DesignSystem.Typography.subheadline())
                                        .foregroundColor(Color.textAccent)
                                } else {
                                    Text(localizationManager.localize("report", comment: "Report on artsobservasjoner.no"))
                                        .font(DesignSystem.Typography.subheadline())
                                        .foregroundColor(Color.textAccent)
                                    SVGWebView(svgName: "ic_external_link", width: 16, height: 16, tintColor: .textAccent)
                                        .frame(width: 16, height: 16)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
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

    private var placeholderImage: some View {
        Circle()
            .fill(Color.surfaceSubtle)
            .frame(width: 80, height: 80)
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
        case "CR": return Color(red: 152/255, green: 25/255, blue: 25/255)
        case "EN": return Color(red: 217/255, green: 15/255, blue: 40/255)
        case "VU": return Color(red: 234/255, green: 79/255, blue: 52/255)
        case "NT": return Color(red: 238/255, green: 108/255, blue: 38/255)
        case "DD": return Color(red: 246/255, green: 166/255, blue: 31/255)
        case "LC": return Color(red: 97/255, green: 190/255, blue: 179/255)
        default: return nil
        }
    }

    private func invasiveColor(for category: String?) -> Color? {
        guard let category = category else { return nil }
        switch category.uppercased() {
        case "SE": return Color(red: 79/255, green: 15/255, blue: 82/255)
        case "HI": return Color(red: 45/255, green: 64/255, blue: 114/255)
        case "PH": return Color(red: 41/255, green: 100/255, blue: 114/255)
        case "LO": return Color(red: 92/255, green: 157/255, blue: 148/255)
        case "NK": return Color(red: 148/255, green: 164/255, blue: 97/255)
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

// Helper extension to convert HTML to AttributedString
extension String {
    func htmlToAttributedString() -> String? {
        guard let data = data(using: .utf8) else { return nil }
        do {
            let attributed = try NSAttributedString(
                data: data,
                options: [.documentType: NSAttributedString.DocumentType.html,
                         .characterEncoding: String.Encoding.utf8.rawValue],
                documentAttributes: nil
            )
            return attributed.string
        } catch {
            return nil
        }
    }
}

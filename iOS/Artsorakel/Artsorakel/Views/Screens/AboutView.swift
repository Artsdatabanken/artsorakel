import SwiftUI
import WebKit

struct AboutView: View {
    @Binding var isPresented: Bool
    @Binding var showMenuDrawer: Bool
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager

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
                    Text(localizationManager.localize("about", comment: "About"))
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

                // WebView content
                AboutWebView(language: localizationManager.currentLanguage)
                    .background(Color.backgroundSubtle)
            }
        }
    }
}

struct AboutWebView: UIViewRepresentable {
    let language: String
    @Environment(\.colorScheme) var colorScheme

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = true
        webView.isOpaque = false
        webView.configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard let htmlContent = loadHTMLContent() else { return }

        let textColor = Color.textPrimary
        let textColorHex = colorToHex(textColor)

        let processedContent = processContent(htmlContent, textColorHex: textColorHex)
        let styledContent = injectStyling(processedContent)

        webView.loadHTMLString(styledContent, baseURL: Bundle.main.resourceURL)
    }

    private func loadHTMLContent() -> String? {
        let lang = language == "system" ? Locale.current.languageCode ?? "en" : language
        let fileName = "about_\(lang)"

        // Try to load the HTML file
        if let url = Bundle.main.url(forResource: fileName, withExtension: "html"),
           let content = try? String(contentsOf: url) {
            return content
        }

        // Fallback to English
        if let url = Bundle.main.url(forResource: "about_en", withExtension: "html"),
           let content = try? String(contentsOf: url) {
            return content
        }

        return nil
    }

    private func processContent(_ htmlContent: String, textColorHex: String) -> String {
        // Get app version
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
        var processed = htmlContent.replacingOccurrences(of: "[Version number]", with: version)

        // Fix image paths for iOS
        processed = processed.replacingOccurrences(of: "file:///android_asset/", with: "")

        // Inline SVG content so it can inherit CSS color
        processed = inlineSvgImages(processed, textColorHex: textColorHex)

        return processed
    }

    private func inlineSvgImages(_ htmlContent: String, textColorHex: String) -> String {
        var result = htmlContent

        // Find all <img src="Logo_*.svg"> tags and replace with inline SVG
        let pattern = #"<img\s+src="(Logo_[^"]+\.svg)"([^>]*)>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: []) else {
            print("DEBUG: Failed to create regex")
            return htmlContent
        }

        let matches = regex.matches(in: htmlContent, options: [], range: NSRange(htmlContent.startIndex..., in: htmlContent))
        print("DEBUG: Found \(matches.count) logo matches")

        // Process matches in reverse to avoid index issues
        for match in matches.reversed() {
            guard match.numberOfRanges >= 3,
                  let svgFileRange = Range(match.range(at: 1), in: htmlContent),
                  let attributesRange = Range(match.range(at: 2), in: htmlContent),
                  let fullMatchRange = Range(match.range(at: 0), in: htmlContent) else {
                continue
            }

            let svgFileName = String(htmlContent[svgFileRange])
            let imgAttributes = String(htmlContent[attributesRange])

            print("DEBUG: Trying to load \(svgFileName)")

            // Load SVG content - try multiple possible locations
            var url: URL?
            var svgContent: String?

            // Try Resources/Content subdirectory
            if let tryUrl = Bundle.main.url(forResource: svgFileName.replacingOccurrences(of: ".svg", with: ""), withExtension: "svg", subdirectory: "Resources/Content") {
                url = tryUrl
                svgContent = try? String(contentsOf: tryUrl)
            }

            // Try root of bundle
            if svgContent == nil, let tryUrl = Bundle.main.url(forResource: svgFileName.replacingOccurrences(of: ".svg", with: ""), withExtension: "svg") {
                url = tryUrl
                svgContent = try? String(contentsOf: tryUrl)
            }

            if let svgContent = svgContent {
                print("DEBUG: Successfully loaded \(svgFileName) from \(url?.path ?? "unknown"), adding color \(textColorHex)")

                // Extract width from img tag style attribute
                let widthPattern = #"width:\s*(\d+)%"#
                let widthRegex = try? NSRegularExpression(pattern: widthPattern, options: [])
                let widthMatch = widthRegex?.firstMatch(in: imgAttributes, options: [], range: NSRange(imgAttributes.startIndex..., in: imgAttributes))
                let width: String
                if let widthMatch = widthMatch, let widthRange = Range(widthMatch.range(at: 1), in: imgAttributes) {
                    width = String(imgAttributes[widthRange])
                } else {
                    width = "80"
                }

                // Remove XML declaration and add color style to SVG tag
                let cleanedSvg = svgContent
                    .replacingOccurrences(of: #"<\?xml[^>]+\?>"#, with: "", options: .regularExpression)
                    .replacingOccurrences(of: "<svg", with: "<svg style=\"color: \(textColorHex); fill: \(textColorHex);\" ")
                    .trimmingCharacters(in: .whitespacesAndNewlines)

                let inlinedSvg = "<div style=\"width: \(width)%; margin-top: 20px; margin-left: auto; margin-right: auto;\">\(cleanedSvg)</div>"

                result = result.replacingCharacters(in: fullMatchRange, with: inlinedSvg)
            }
        }

        return result
    }

    private func injectStyling(_ htmlContent: String) -> String {
        let backgroundColor = Color.backgroundSubtle
        let textColor = Color.textPrimary
        let linkColor = Color.textAccent

        let css = """
        <style>
            @font-face {
                font-family: 'Chivo';
                src: local('Chivo');
                font-weight: normal;
                font-style: normal;
            }

            @font-face {
                font-family: 'Chivo';
                src: local('Chivo-Bold');
                font-weight: bold;
                font-style: normal;
            }

            body {
                font-family: 'Chivo', -apple-system, Arial, sans-serif;
                font-size: 14px;
                line-height: 1.5;
                color: \(colorToHex(textColor));
                margin: 0;
                padding: 16px;
                background-color: \(colorToHex(backgroundColor));
            }

            h3 {
                color: \(colorToHex(textColor));
                opacity: 0.9;
                margin-top: 0;
                margin-bottom: 16px;
                font-family: 'Chivo', -apple-system, Arial, sans-serif;
                font-weight: bold;
            }

            p {
                margin-bottom: 12px;
                text-align: left;
                color: \(colorToHex(textColor));
            }

            strong {
                font-weight: bold;
                font-family: 'Chivo', -apple-system, Arial, sans-serif;
                color: \(colorToHex(textColor));
            }

            a {
                color: \(colorToHex(linkColor));
                text-decoration: underline;
            }

            a:visited {
                color: \(colorToHex(linkColor));
            }

            img {
                display: block;
                margin: 20px auto 0 auto;
                width: 100%;
                height: auto;
                max-width: 200px;
            }

            svg {
                color: \(colorToHex(textColor));
                fill: \(colorToHex(textColor));
                max-width: 100%;
                height: auto;
            }
        </style>
        """

        return """
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <meta name="color-scheme" content="light dark">
            \(css)
        </head>
        <body>
            \(htmlContent)
        </body>
        </html>
        """
    }

    private func colorToHex(_ color: Color) -> String {
        let uiColor = UIColor(color)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0

        uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)

        return String(format: "#%02X%02X%02X",
                     Int(red * 255),
                     Int(green * 255),
                     Int(blue * 255))
    }
}

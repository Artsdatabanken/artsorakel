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
                    Text(localizationManager.localize("about", comment: "About"))
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

        let processedContent = processContent(htmlContent)
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

    private func processContent(_ htmlContent: String) -> String {
        // Get app version
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "Unknown"
        var processed = htmlContent.replacingOccurrences(of: "[Version number]", with: version)

        // Fix image paths for iOS
        processed = processed.replacingOccurrences(of: "file:///android_asset/", with: "")

        return processed
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

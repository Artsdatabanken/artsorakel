import SwiftUI
import WebKit

struct SVGWebView: UIViewRepresentable {
    let svgName: String
    let width: CGFloat
    let height: CGFloat
    var tintColor: Color?
    @Environment(\.colorScheme) var colorScheme

    func makeUIView(context: Context) -> WKWebView {
        let webView = WKWebView()
        webView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        webView.scrollView.showsVerticalScrollIndicator = false
        webView.scrollView.showsHorizontalScrollIndicator = false
        webView.isOpaque = false
        webView.isUserInteractionEnabled = false // Don't intercept touches - let parent handle them
        webView.configuration.defaultWebpagePreferences.allowsContentJavaScript = false
        return webView
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        var svgURL: URL?

        svgURL = Bundle.main.url(forResource: svgName, withExtension: "svg")

        if svgURL == nil {
            svgURL = Bundle.main.url(forResource: "Resources/Images/\(svgName)", withExtension: "svg")
        }
        if svgURL == nil {
            svgURL = Bundle.main.url(forResource: "Resources/Vectors/\(svgName)", withExtension: "svg")
        }

        guard let url = svgURL,
              let svgData = try? Data(contentsOf: url),
              var svgString = String(data: svgData, encoding: .utf8) else {
            return
        }

        svgString = svgString.replacingOccurrences(of: "width=\"24px\"", with: "")
        svgString = svgString.replacingOccurrences(of: "height=\"24px\"", with: "")
        svgString = svgString.replacingOccurrences(of: "width=\"24\"", with: "")
        svgString = svgString.replacingOccurrences(of: "height=\"24\"", with: "")

        var fillStyle = ""
        if let tintColor = tintColor {
            let uiColor = UIColor(tintColor)
            // Use the colorScheme from environment to create the correct trait collection
            let traitCollection = UITraitCollection(userInterfaceStyle: colorScheme == .dark ? .dark : .light)
            let resolvedColor = uiColor.resolvedColor(with: traitCollection)
            var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
            resolvedColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            let hexColor = String(format: "#%02X%02X%02X", Int(red * 255), Int(green * 255), Int(blue * 255))
            fillStyle = "svg path, svg circle, svg rect, svg polygon { fill: \(hexColor) !important; }"
        }

        let html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=\(Int(width)), height=\(Int(height)), initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: transparent;
                }
                body {
                    display: flex;
                    justify-content: center;
                    align-items: center;
                }
                svg {
                    width: 100%;
                    height: 100%;
                    max-width: 100%;
                    max-height: 100%;
                }
                \(fillStyle)
            </style>
        </head>
        <body>
            \(svgString)
        </body>
        </html>
        """

        webView.loadHTMLString(html, baseURL: nil)
    }
}

struct SVGView: View {
    let svgName: String
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        if Bundle.main.url(forResource: svgName, withExtension: "svg") != nil {
            SVGWebView(svgName: svgName, width: width, height: height)
                .frame(width: width, height: height)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color(.systemRed).opacity(0.1))
                .frame(width: width, height: height)
                .overlay(
                    VStack {
                        Image(systemName: "xmark.circle")
                            .foregroundColor(.red)
                        Text("SVG not found")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                )
        }
    }
}

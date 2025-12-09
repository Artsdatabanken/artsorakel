import Foundation

extension String {
    /// Strips HTML tags from the string using a simple regex.
    ///
    /// Note: This uses regex instead of NSAttributedString with HTML because
    /// NSAttributedString's HTML parsing uses WebKit which requires main thread
    /// and can crash during background snapshots (SIGABRT in NSHTMLReader).
    func strippingHTMLTags() -> String {
        guard let regex = try? NSRegularExpression(pattern: "<[^>]+>", options: .caseInsensitive) else {
            return self
        }
        let range = NSRange(startIndex..<endIndex, in: self)
        return regex.stringByReplacingMatches(in: self, options: [], range: range, withTemplate: "")
    }
}

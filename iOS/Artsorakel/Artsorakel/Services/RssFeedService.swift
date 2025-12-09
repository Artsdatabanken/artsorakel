//
//  RssFeedService.swift
//  Artsorakel
//

import Foundation

class RssFeedService: ObservableObject {
    static let shared = RssFeedService()

    @Published private(set) var currentFeedItem: RssFeedItem?

    private let userDefaultsKey = "rss_dismissed_guids"
    private let sessionIdKey = "rss_session_id"
    private let feedFetchedSessionKey = "rss_feed_fetched_session"

    private var cachedAllItems: [RssFeedItem]?
    private var sessionId: String

    private init() {
        if let savedSessionId = UserDefaults.standard.string(forKey: sessionIdKey) {
            self.sessionId = savedSessionId
        } else {
            let newSessionId = String(Date().timeIntervalSince1970)
            UserDefaults.standard.set(newSessionId, forKey: sessionIdKey)
            self.sessionId = newSessionId
        }
    }

    func resetSessionOnAppStart() {
        let newSessionId = String(Date().timeIntervalSince1970)
        UserDefaults.standard.set(newSessionId, forKey: sessionIdKey)
        self.sessionId = newSessionId
        cachedAllItems = nil
    }

    @MainActor
    func loadRssFeed() async {
        let item = await fetchFilteredRssItem()
        self.currentFeedItem = item
    }

    @MainActor
    func dismissCurrentItem() {
        guard let item = currentFeedItem else { return }
        dismissItem(item)
        let nextItem = refilterCachedItems()
        self.currentFeedItem = nextItem
    }

    @MainActor
    func onLanguageChanged() {
        let item = refilterCachedItems()
        self.currentFeedItem = item
    }

    private func fetchFilteredRssItem() async -> RssFeedItem? {
        let fetchedSession = UserDefaults.standard.string(forKey: feedFetchedSessionKey)

        let allItems: [RssFeedItem]
        if fetchedSession == sessionId, let cached = cachedAllItems {
            allItems = cached
        } else {
            allItems = await fetchAllRssItems()
            cachedAllItems = allItems
            UserDefaults.standard.set(sessionId, forKey: feedFetchedSessionKey)
            cleanupOldDismissedGuids(currentGuids: allItems.map { $0.id })
        }

        let dismissedGuids = getDismissedGuids()

        let filteredItems = allItems.filter { item in
            passesAllFilters(item) && !isDismissed(item, dismissedGuids: dismissedGuids)
        }

        return filteredItems.first
    }

    private func refilterCachedItems() -> RssFeedItem? {
        guard let allItems = cachedAllItems else { return nil }

        let dismissedGuids = getDismissedGuids()

        let filteredItems = allItems.filter { item in
            passesAllFilters(item) && !isDismissed(item, dismissedGuids: dismissedGuids)
        }

        return filteredItems.first
    }

    private func fetchAllRssItems() async -> [RssFeedItem] {
        guard let urlString = AppConfig.shared.rssFeedURL,
              let url = URL(string: urlString) else {
            return []
        }

        do {
            let (data, _) = try await URLSession.shared.data(from: url)
            return parseRssFeed(data: data)
        } catch {
            return []
        }
    }

    private func parseRssFeed(data: Data) -> [RssFeedItem] {
        let parser = RssXmlParser()
        return parser.parse(data: data)
    }

    // MARK: - Filtering

    private func passesAllFilters(_ item: RssFeedItem) -> Bool {
        return passesLanguageFilter(item) &&
               passesVersionFilter(item) &&
               passesPlatformFilter(item)
    }

    private func passesLanguageFilter(_ item: RssFeedItem) -> Bool {
        guard let itemLanguage = item.language else { return true }

        // Use app's language setting, falling back to device locale
        let savedLanguage = UserDefaults.standard.string(forKey: "selectedLanguage") ?? "system"
        let currentLanguage: String
        if savedLanguage == "system" {
            currentLanguage = Locale.current.languageCode ?? "en"
        } else {
            currentLanguage = savedLanguage
        }

        return itemLanguage == currentLanguage ||
               (itemLanguage == "nb" && currentLanguage == "no") ||
               (itemLanguage == "no" && currentLanguage == "nb")
    }

    private func passesVersionFilter(_ item: RssFeedItem) -> Bool {
        let versionRequirements = item.categories.filter { $0.hasPrefix("Version:") }
        if versionRequirements.isEmpty { return true }

        let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"

        return versionRequirements.allSatisfy { requirement in
            let versionPart = String(requirement.dropFirst("Version:".count)).trimmingCharacters(in: .whitespaces)
            return checkVersionRequirement(currentVersion: currentVersion, requirement: versionPart)
        }
    }

    private func checkVersionRequirement(currentVersion: String, requirement: String) -> Bool {
        let operators = ["<=", ">=", "<", ">", "="]
        var op = "="
        var versionToCompare = requirement

        for operatorStr in operators {
            if requirement.hasPrefix(operatorStr) {
                op = operatorStr
                versionToCompare = String(requirement.dropFirst(operatorStr.count)).trimmingCharacters(in: .whitespaces)
                break
            }
        }

        // Validate version format
        let versionRegex = try? NSRegularExpression(pattern: "^\\d+(\\.\\d+)*$")
        let range = NSRange(versionToCompare.startIndex..., in: versionToCompare)
        if versionRegex?.firstMatch(in: versionToCompare, range: range) == nil {
            versionToCompare = requirement
            op = "="
        }

        let comparison = compareVersions(currentVersion, versionToCompare)

        switch op {
        case "<": return comparison < 0
        case "<=": return comparison <= 0
        case ">": return comparison > 0
        case ">=": return comparison >= 0
        case "=": return comparison == 0
        default: return comparison == 0
        }
    }

    private func compareVersions(_ version1: String, _ version2: String) -> Int {
        let parts1 = version1.split(separator: ".").map { Int($0) ?? 0 }
        let parts2 = version2.split(separator: ".").map { Int($0) ?? 0 }

        let maxLength = max(parts1.count, parts2.count)

        for i in 0..<maxLength {
            let part1 = i < parts1.count ? parts1[i] : 0
            let part2 = i < parts2.count ? parts2[i] : 0

            if part1 < part2 { return -1 }
            if part1 > part2 { return 1 }
        }

        return 0
    }

    private func passesPlatformFilter(_ item: RssFeedItem) -> Bool {
        let platformRequirements = item.categories.filter { $0.hasPrefix("Platform:") }
        if platformRequirements.isEmpty { return true }

        let currentPlatform = "iOS"

        return platformRequirements.contains { requirement in
            let platform = String(requirement.dropFirst("Platform:".count)).trimmingCharacters(in: .whitespaces)
            return platform.caseInsensitiveCompare(currentPlatform) == .orderedSame
        }
    }

    // MARK: - Dismissal

    private func isDismissed(_ item: RssFeedItem, dismissedGuids: Set<String>) -> Bool {
        // Permanent items are never considered dismissed since they can't be dismissed
        if item.isPermanent { return false }
        return dismissedGuids.contains(item.id)
    }

    private func dismissItem(_ item: RssFeedItem) {
        // Permanent items cannot be dismissed
        if item.isPermanent { return }

        var dismissedGuids = getDismissedGuids()
        dismissedGuids.insert(item.id)
        saveDismissedGuids(dismissedGuids)
    }

    private func getDismissedGuids() -> Set<String> {
        guard let guidsString = UserDefaults.standard.string(forKey: userDefaultsKey),
              !guidsString.isEmpty else {
            return []
        }
        return Set(guidsString.split(separator: ",").map { String($0) })
    }

    private func saveDismissedGuids(_ guids: Set<String>) {
        UserDefaults.standard.set(guids.joined(separator: ","), forKey: userDefaultsKey)
    }

    private func cleanupOldDismissedGuids(currentGuids: [String]) {
        let dismissedGuids = getDismissedGuids()
        let currentGuidsSet = Set(currentGuids)
        let cleanedGuids = dismissedGuids.intersection(currentGuidsSet)

        if cleanedGuids.count < dismissedGuids.count {
            saveDismissedGuids(cleanedGuids)
        }
    }
}

// MARK: - XML Parser

private class RssXmlParser: NSObject, XMLParserDelegate {
    private var items: [RssFeedItem] = []

    private var currentGuid: String?
    private var currentTitle: String?
    private var currentDescription: String?
    private var currentLink: String?
    private var currentPubDate: String?
    private var currentLanguage: String?
    private var currentCategories: [String] = []
    private var currentRssCategory: RssCategory = .info

    private var insideItem = false
    private var insideChannel = false
    private var channelLanguage: String?
    private var currentElement: String = ""
    private var currentText: String = ""

    func parse(data: Data) -> [RssFeedItem] {
        items = []
        let parser = XMLParser(data: data)
        parser.delegate = self
        parser.parse()
        return items
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String: String] = [:]) {
        currentElement = elementName.lowercased()
        currentText = ""

        switch currentElement {
        case "channel":
            insideChannel = true
        case "item":
            insideItem = true
            currentLanguage = channelLanguage
            currentCategories = []
            currentRssCategory = .info
            currentGuid = nil
            currentTitle = nil
            currentDescription = nil
            currentLink = nil
            currentPubDate = nil
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        currentText += string
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let element = elementName.lowercased()
        let text = currentText.trimmingCharacters(in: .whitespacesAndNewlines)

        switch element {
        case "channel":
            insideChannel = false
            channelLanguage = nil
        case "language":
            if insideChannel && !insideItem {
                channelLanguage = text
            }
        case "item":
            if insideItem,
               let guid = currentGuid,
               let title = currentTitle,
               let description = currentDescription {
                let item = RssFeedItem(
                    guid: guid,
                    title: title,
                    description: cleanDescription(description),
                    link: currentLink,
                    pubDate: currentPubDate,
                    language: currentLanguage,
                    categories: currentCategories,
                    category: currentRssCategory
                )
                items.append(item)
            }
            insideItem = false
        case "guid":
            if insideItem { currentGuid = text }
        case "title":
            if insideItem { currentTitle = text }
        case "description":
            if insideItem { currentDescription = text }
        case "link":
            if insideItem { currentLink = text }
        case "pubdate":
            if insideItem { currentPubDate = text }
        case "category":
            if insideItem {
                let upperText = text.uppercased()
                switch upperText {
                case "DANGER":
                    currentRssCategory = .danger
                case "WARNING":
                    currentRssCategory = .warning
                case "INFO":
                    currentRssCategory = .info
                default:
                    currentCategories.append(text)
                }
            }
        default:
            break
        }

        currentElement = ""
        currentText = ""
    }

    private func cleanDescription(_ description: String) -> String {
        var result = description
        // Remove HTML tags
        if let regex = try? NSRegularExpression(pattern: "<[^>]*>", options: []) {
            result = regex.stringByReplacingMatches(in: result, options: [], range: NSRange(result.startIndex..., in: result), withTemplate: "")
        }
        // Decode HTML entities
        result = result.replacingOccurrences(of: "&nbsp;", with: " ")
        result = result.replacingOccurrences(of: "&amp;", with: "&")
        result = result.replacingOccurrences(of: "&lt;", with: "<")
        result = result.replacingOccurrences(of: "&gt;", with: ">")
        result = result.replacingOccurrences(of: "&quot;", with: "\"")
        result = result.replacingOccurrences(of: "&#39;", with: "'")
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

//
//  RssFeedItem.swift
//  Artsorakel
//

import Foundation

enum RssCategory: String {
    case danger = "DANGER"
    case warning = "WARNING"
    case info = "INFO"
}

struct RssFeedItem: Identifiable {
    let id: String // guid
    let title: String
    let description: String
    let link: String?
    let pubDate: String?
    let language: String?
    let categories: [String]
    let category: RssCategory

    var isPermanent: Bool {
        categories.contains { $0.caseInsensitiveCompare("Permanent") == .orderedSame }
    }

    init(
        guid: String,
        title: String,
        description: String,
        link: String? = nil,
        pubDate: String? = nil,
        language: String? = nil,
        categories: [String] = [],
        category: RssCategory = .info
    ) {
        self.id = guid
        self.title = title
        self.description = description
        self.link = link
        self.pubDate = pubDate
        self.language = language
        self.categories = categories
        self.category = category
    }
}

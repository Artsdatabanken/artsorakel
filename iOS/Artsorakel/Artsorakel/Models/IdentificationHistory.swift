import Foundation
import UIKit

/// Entity representing a saved identification history entry
struct IdentificationHistory: Identifiable, Codable {
    let id: UUID
    let timestamp: Date
    let bestMatchVernacularNames: [String: String]?
    let bestMatchScientificName: String?
    let bestMatchProbability: Double
    let bestMatchGroupNames: [String: String]?
    let bestMatchInfoUrl: String?
    let allResults: String // JSON string of all PredictionResults
    let imagePaths: [String] // Local paths to saved images (1024x1024)
    let warnings: String? // JSON string of Warnings object
    let uploadId: String? // Upload ID from server for reporting
    let uploadSecret: String? // Upload secret from server for reporting

    init(
        id: UUID = UUID(),
        timestamp: Date = Date(),
        bestMatchVernacularNames: [String: String]?,
        bestMatchScientificName: String?,
        bestMatchProbability: Double,
        bestMatchGroupNames: [String: String]?,
        bestMatchInfoUrl: String?,
        allResults: String,
        imagePaths: [String],
        warnings: String? = nil,
        uploadId: String? = nil,
        uploadSecret: String? = nil
    ) {
        self.id = id
        self.timestamp = timestamp
        self.bestMatchVernacularNames = bestMatchVernacularNames
        self.bestMatchScientificName = bestMatchScientificName
        self.bestMatchProbability = bestMatchProbability
        self.bestMatchGroupNames = bestMatchGroupNames
        self.bestMatchInfoUrl = bestMatchInfoUrl
        self.allResults = allResults
        self.imagePaths = imagePaths
        self.warnings = warnings
        self.uploadId = uploadId
        self.uploadSecret = uploadSecret
    }

    /// Gets the vernacular name for a specific language
    func getVernacularName(for languageCode: String) -> String? {
        return bestMatchVernacularNames?[languageCode]
    }

    /// Gets the group name for a specific language
    func getGroupName(for languageCode: String) -> String? {
        return bestMatchGroupNames?[languageCode]
    }
}

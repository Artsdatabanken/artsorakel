import Foundation
import CoreLocation

struct ModelInfo: Codable {
    let model: String?
    let country: String?
    let locationSource: String?
}

struct PredictionResult: Identifiable, Codable {
    let id: String
    let vernacularNames: [String: String]?
    let scientificName: String?
    let groupNames: [String: String]?
    let probability: Double
    let pictureUrl: String?
    let infoUrl: String?
    let modelInfo: ModelInfo?
    let redListCategory: String?
    let invasiveCategory: String?

    func getVernacularName(for language: String) -> String? {
        return vernacularNames?[language]
    }

    func getGroupName(for language: String) -> String? {
        return groupNames?[language]
    }
}

// API Response structures
struct APIResponse: Codable {
    let predictions: [PredictionDTO]?
    let modelInfo: ModelInfoDTO?
}

struct ModelInfoDTO: Codable {
    let model: String?
    let country: String?
    let locationSource: String?
}

struct PredictionDTO: Codable {
    let regionGroupId: String?
    let taxa: TaxaInfoDTO?

    enum CodingKeys: String, CodingKey {
        case regionGroupId = "region_group_id"
        case taxa
    }
}

struct TaxaInfoDTO: Codable {
    let items: [TaxonItemDTO]?
    let type: String?
}

struct TaxonItemDTO: Codable {
    let probability: Double?
    let scientificName: String?
    let scientificNameId: String?
    let vernacularNames: [String: String]?
    let groupNames: [String: String]?
    let name: String?
    let infoUrl: String?
    let picture: String?
    let redListCategory: String?
    let invasiveCategory: String?

    enum CodingKeys: String, CodingKey {
        case probability
        case scientificName = "scientific_name"
        case scientificNameId = "scientific_name_id"
        case vernacularNames, groupNames, name, infoUrl, picture, redListCategory, invasiveCategory
    }
}

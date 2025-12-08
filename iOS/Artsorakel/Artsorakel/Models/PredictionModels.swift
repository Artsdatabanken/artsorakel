import Foundation
import CoreLocation

struct ModelInfo: Codable, Equatable {
    let model: String?
    let country: String?
    let locationSource: String?
}

struct PredictionResult: Identifiable, Codable, Equatable {
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

    /// Get the placeholder SVG name based on the Norwegian group name
    func getPlaceholderName() -> String {
        let norwegianGroupName = groupNames?["nb"] ?? groupNames?["nn"] ?? ""
        let normalized = norwegianGroupName.lowercased().trimmingCharacters(in: .whitespaces)

        switch normalized {
        case "karplanter":
            return "placeholder_karplanter"
        case "fugler":
            return "placeholder_fugler"
        case "pattedyr":
            return "placeholder_pattedyr"
        case "lav":
            return "placeholder_lav"
        case "sommerfugler":
            return "placeholder_sommerfugler"
        case "sopper":
            return "placeholder_sopper"
        case "nebbmunner":
            return "placeholder_nebbmunner"
        case "moser":
            return "placeholder_moser"
        case "bløtdyr":
            return "placeholder_bløtdyr"
        case "edderkoppdyr":
            return "placeholder_edderkoppdyr"
        case "nettvinger, kakerlakker, saksedyr":
            return "placeholder_nettvinger_osv"
        case "veps":
            return "placeholder_veps"
        case "biller":
            return "placeholder_biller"
        case "tovinger":
            return "placeholder_tovinger"
        case "fisker":
            return "placeholder_fisker"
        case "amfibier, reptiler":
            return "placeholder_reptiler_osv"
        case "døgnfluer, øyenstikkere, steinfluer, vårfluer":
            return "placeholder_døgnfluer_osv"
        case "armfotinger, pigghuder, kappedyr":
            return "placeholder_pigghuder_osv"
        default:
            return "placeholder_generic"
        }
    }
}

// API Response structures
struct APIResponse: Codable {
    let predictions: [PredictionDTO]?
    let modelInfo: ModelInfoDTO?
    let uploadId: String?
    let uploadSecret: String?
}

/// Response from the /save endpoint containing image reference data
struct SaveImageResponse: Codable {
    let id: String
    let password: String
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

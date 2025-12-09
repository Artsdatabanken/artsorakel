//
//  ArtsorakelTests.swift
//  ArtsorakelTests
//
//  Created by Wouter Koch on 04/09/2025.
//

import Testing
import Foundation
@testable import Artsorakel

// MARK: - PredictionResult Tests

struct PredictionResultTests {

    @Test func getVernacularNameReturnsCorrectLanguage() {
        let result = PredictionResult(
            id: "test_1",
            vernacularNames: ["en": "Oak", "nb": "Eik", "es": "Roble"],
            scientificName: "Quercus",
            groupNames: nil,
            probability: 0.95,
            pictureUrl: nil,
            infoUrl: nil,
            modelInfo: nil,
            redListCategory: nil,
            invasiveCategory: nil
        )

        #expect(result.getVernacularName(for: "en") == "Oak")
        #expect(result.getVernacularName(for: "nb") == "Eik")
        #expect(result.getVernacularName(for: "es") == "Roble")
        #expect(result.getVernacularName(for: "fr") == nil)
    }

    @Test func getVernacularNameHandlesNilDictionary() {
        let result = PredictionResult(
            id: "test_2",
            vernacularNames: nil,
            scientificName: "Quercus",
            groupNames: nil,
            probability: 0.95,
            pictureUrl: nil,
            infoUrl: nil,
            modelInfo: nil,
            redListCategory: nil,
            invasiveCategory: nil
        )

        #expect(result.getVernacularName(for: "en") == nil)
    }

    @Test func getGroupNameReturnsCorrectLanguage() {
        let result = PredictionResult(
            id: "test_3",
            vernacularNames: nil,
            scientificName: "Quercus",
            groupNames: ["en": "Trees", "nb": "Trær"],
            probability: 0.95,
            pictureUrl: nil,
            infoUrl: nil,
            modelInfo: nil,
            redListCategory: nil,
            invasiveCategory: nil
        )

        #expect(result.getGroupName(for: "en") == "Trees")
        #expect(result.getGroupName(for: "nb") == "Trær")
        #expect(result.getGroupName(for: "de") == nil)
    }

    @Test func getPlaceholderNameReturnsCorrectPlaceholder() {
        let testCases: [(groupName: String, expected: String)] = [
            ("Karplanter", "placeholder_karplanter"),
            ("Fugler", "placeholder_fugler"),
            ("Pattedyr", "placeholder_pattedyr"),
            ("Lav", "placeholder_lav"),
            ("Sommerfugler", "placeholder_sommerfugler"),
            ("Sopper", "placeholder_sopper"),
            ("Nebbmunner", "placeholder_nebbmunner"),
            ("Moser", "placeholder_moser"),
            ("Bløtdyr", "placeholder_bløtdyr"),
            ("Edderkoppdyr", "placeholder_edderkoppdyr"),
            ("Veps", "placeholder_veps"),
            ("Biller", "placeholder_biller"),
            ("Tovinger", "placeholder_tovinger"),
            ("Fisker", "placeholder_fisker"),
            ("Unknown Group", "placeholder_generic"),
            ("", "placeholder_generic")
        ]

        for testCase in testCases {
            let result = PredictionResult(
                id: "test",
                vernacularNames: nil,
                scientificName: nil,
                groupNames: ["nb": testCase.groupName],
                probability: 0.5,
                pictureUrl: nil,
                infoUrl: nil,
                modelInfo: nil,
                redListCategory: nil,
                invasiveCategory: nil
            )
            #expect(result.getPlaceholderName() == testCase.expected, "Expected \(testCase.expected) for group '\(testCase.groupName)'")
        }
    }

    @Test func predictionResultIsCodable() throws {
        let original = PredictionResult(
            id: "NBIC:12345_0.95",
            vernacularNames: ["en": "Oak", "nb": "Eik"],
            scientificName: "Quercus robur",
            groupNames: ["en": "Trees"],
            probability: 0.95,
            pictureUrl: "https://example.com/oak.jpg",
            infoUrl: "https://example.com/oak",
            modelInfo: ModelInfo(model: "v1", country: "NO", locationSource: "gps"),
            redListCategory: "LC",
            invasiveCategory: nil
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(PredictionResult.self, from: data)

        #expect(decoded.id == original.id)
        #expect(decoded.scientificName == original.scientificName)
        #expect(decoded.probability == original.probability)
        #expect(decoded.vernacularNames?["en"] == "Oak")
        #expect(decoded.redListCategory == "LC")
    }
}

// MARK: - APIError Tests

struct APIErrorTests {

    @Test func invalidURLHasDescription() {
        let error = APIError.invalidURL
        #expect(error.errorDescription == "Invalid API URL")
    }

    @Test func invalidResponseHasDescription() {
        let error = APIError.invalidResponse
        #expect(error.errorDescription == "Invalid server response")
    }

    @Test func httpErrorIncludesStatusCode() {
        let error = APIError.httpError(statusCode: 404)
        #expect(error.errorDescription == "Server error: 404")

        let error500 = APIError.httpError(statusCode: 500)
        #expect(error500.errorDescription == "Server error: 500")
    }

    @Test func decodingErrorIncludesDetails() {
        struct TestError: Error, LocalizedError {
            var errorDescription: String? { "Test decoding failure" }
        }
        let error = APIError.decodingError(TestError())
        #expect(error.errorDescription?.contains("Failed to parse response") == true)
    }

    @Test func networkErrorIncludesDetails() {
        struct TestNetworkError: Error, LocalizedError {
            var errorDescription: String? { "Connection lost" }
        }
        let error = APIError.networkError(TestNetworkError())
        #expect(error.errorDescription?.contains("Network error") == true)
    }
}

// MARK: - AppConfigError Tests

struct AppConfigErrorTests {

    @Test func configFileNotFoundHasDescription() {
        let error = AppConfigError.configFileNotFound
        #expect(error.errorDescription?.contains("Configuration file not found") == true)
    }

    @Test func secretsFileNotFoundHasDescription() {
        let error = AppConfigError.secretsFileNotFound
        #expect(error.errorDescription?.contains("Authentication configuration not found") == true)
    }

    @Test func missingBearerTokenHasDescription() {
        let error = AppConfigError.missingBearerToken
        #expect(error.errorDescription?.contains("Authentication token") == true)
    }

    @Test func allErrorsRecommendReinstall() {
        let errors: [AppConfigError] = [
            .configFileNotFound,
            .configFileInvalid,
            .missingBaseURL,
            .secretsFileNotFound,
            .secretsFileInvalid,
            .missingBearerToken
        ]

        for error in errors {
            #expect(error.errorDescription?.contains("reinstall") == true,
                    "Error \(error) should recommend reinstall")
        }
    }
}

// MARK: - IdentificationHistory Tests

struct IdentificationHistoryTests {

    @Test func identificationHistoryIsCodable() throws {
        let original = IdentificationHistory(
            timestamp: Date(),
            bestMatchVernacularNames: ["en": "Oak"],
            bestMatchScientificName: "Quercus",
            bestMatchProbability: 0.95,
            bestMatchGroupNames: ["en": "Trees"],
            bestMatchInfoUrl: "https://example.com",
            allResults: "[]",
            imagePaths: ["img_1.jpg", "img_2.jpg"],
            warnings: nil,
            uploadId: "upload123",
            uploadSecret: "secret456"
        )

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(IdentificationHistory.self, from: data)

        #expect(decoded.id == original.id)
        #expect(decoded.bestMatchScientificName == "Quercus")
        #expect(decoded.bestMatchProbability == 0.95)
        #expect(decoded.imagePaths.count == 2)
        #expect(decoded.uploadId == "upload123")
    }

    @Test func identificationHistoryGeneratesUniqueIds() {
        let history1 = IdentificationHistory(
            timestamp: Date(),
            bestMatchVernacularNames: nil,
            bestMatchScientificName: "Test1",
            bestMatchProbability: 0.5,
            bestMatchGroupNames: nil,
            bestMatchInfoUrl: nil,
            allResults: "[]",
            imagePaths: [],
            warnings: nil,
            uploadId: nil,
            uploadSecret: nil
        )

        let history2 = IdentificationHistory(
            timestamp: Date(),
            bestMatchVernacularNames: nil,
            bestMatchScientificName: "Test2",
            bestMatchProbability: 0.5,
            bestMatchGroupNames: nil,
            bestMatchInfoUrl: nil,
            allResults: "[]",
            imagePaths: [],
            warnings: nil,
            uploadId: nil,
            uploadSecret: nil
        )

        #expect(history1.id != history2.id)
    }
}

// MARK: - API Response Parsing Tests

struct APIResponseParsingTests {

    @Test func parsesValidAPIResponse() throws {
        let json = """
        {
            "predictions": [{
                "region_group_id": "NO",
                "taxa": {
                    "type": "species",
                    "items": [{
                        "probability": 0.95,
                        "scientific_name": "Quercus robur",
                        "scientific_name_id": "NBIC:12345",
                        "vernacularNames": {"en": "Oak", "nb": "Eik"},
                        "groupNames": {"en": "Trees"},
                        "infoUrl": "https://example.com",
                        "picture": "https://example.com/pic.jpg",
                        "redListCategory": "LC",
                        "invasiveCategory": null
                    }]
                }
            }],
            "modelInfo": {
                "model": "v2",
                "country": "NO",
                "locationSource": "gps"
            },
            "uploadId": "abc123",
            "uploadSecret": "secret789"
        }
        """

        let data = json.data(using: .utf8)!
        let response = try JSONDecoder().decode(APIResponse.self, from: data)

        #expect(response.predictions?.count == 1)
        #expect(response.modelInfo?.country == "NO")
        #expect(response.uploadId == "abc123")
        #expect(response.uploadSecret == "secret789")

        let taxa = response.predictions?.first?.taxa
        #expect(taxa?.items?.first?.scientificName == "Quercus robur")
        #expect(taxa?.items?.first?.probability == 0.95)
    }

    @Test func handlesEmptyPredictions() throws {
        let json = """
        {
            "predictions": [],
            "modelInfo": null,
            "uploadId": null,
            "uploadSecret": null
        }
        """

        let data = json.data(using: .utf8)!
        let response = try JSONDecoder().decode(APIResponse.self, from: data)

        #expect(response.predictions?.isEmpty == true)
        #expect(response.modelInfo == nil)
    }

    @Test func parsesSaveImageResponse() throws {
        let json = """
        {
            "id": "img_12345",
            "password": "secretpass"
        }
        """

        let data = json.data(using: .utf8)!
        let response = try JSONDecoder().decode(SaveImageResponse.self, from: data)

        #expect(response.id == "img_12345")
        #expect(response.password == "secretpass")
    }
}

// MARK: - ModelInfo Tests

struct ModelInfoTests {

    @Test func modelInfoIsCodableAndEquatable() throws {
        let original = ModelInfo(model: "v2", country: "NO", locationSource: "gps")

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(ModelInfo.self, from: data)

        #expect(decoded == original)
        #expect(decoded.model == "v2")
        #expect(decoded.country == "NO")
        #expect(decoded.locationSource == "gps")
    }

    @Test func modelInfoHandlesNilValues() throws {
        let original = ModelInfo(model: nil, country: nil, locationSource: nil)

        let encoder = JSONEncoder()
        let data = try encoder.encode(original)

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(ModelInfo.self, from: data)

        #expect(decoded.model == nil)
        #expect(decoded.country == nil)
        #expect(decoded.locationSource == nil)
    }
}

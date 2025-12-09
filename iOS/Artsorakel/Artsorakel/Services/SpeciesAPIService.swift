import Foundation
import UIKit
import CoreLocation
import SwiftUI

/// Result of species identification including upload credentials
struct IdentificationResult {
    let predictions: [PredictionResult]
    let warnings: Warnings?
    let uploadId: String?
    let uploadSecret: String?
    let timestamp: Date
}

class SpeciesAPIService {
    static let shared = SpeciesAPIService()

    private let baseURL = AppConfig.API.baseURL
    private let bearerToken = AppConfig.API.bearerToken
    private let timeout = AppConfig.API.timeout

    private var currentTask: URLSessionDataTask?

    private init() {}

    /// Identifies species from images and returns results with upload credentials
    func identifySpecies(images: [UIImage], location: CLLocation?) async throws -> IdentificationResult {
        guard let url = URL(string: baseURL + "identify") else {
            throw APIError.invalidURL
        }

        let boundary = UUID().uuidString
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout

        // Create multipart form data
        var body = Data()

        // Add application type
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"application\"\r\n\r\n".data(using: .utf8)!)
        body.append("iOSApp\r\n".data(using: .utf8)!)

        // Add location if available and if user has enabled location sharing
        let useLocation = UserDefaults.standard.object(forKey: "useLocation") as? Bool ?? true
        if useLocation, let location = location {
            let lat = String(format: "%.1f", location.coordinate.latitude)
            let lon = String(format: "%.1f", location.coordinate.longitude)

            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"latitude\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(lat)\r\n".data(using: .utf8)!)

            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"longitude\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(lon)\r\n".data(using: .utf8)!)
        }

        // Add images
        for (index, image) in images.enumerated() {
            // If location sharing is disabled, strip EXIF data
            let imageData: Data?
            if useLocation {
                imageData = image.jpegData(compressionQuality: 0.85)
            } else {
                imageData = stripEXIFData(from: image, compressionQuality: 0.85)
            }

            guard let finalImageData = imageData else { continue }

            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"image\"; filename=\"image_\(index).jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(finalImageData)
            body.append("\r\n".data(using: .utf8)!)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        // Make request
        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            throw APIError.httpError(statusCode: httpResponse.statusCode)
        }

        // Parse response
        let apiResponse = try JSONDecoder().decode(APIResponse.self, from: data)

        // Map to domain models
        let predictions = mapAPIResponseToPredictionResults(apiResponse)
        let warnings = mapWarnings(apiResponse.warnings)

        return IdentificationResult(
            predictions: predictions,
            warnings: warnings,
            uploadId: apiResponse.uploadId,
            uploadSecret: apiResponse.uploadSecret,
            timestamp: Date()
        )
    }

    /// Saves images to server for reporting (when upload credentials have expired)
    func saveImagesForReport(images: [UIImage]) async throws -> SaveImageResponse {
        guard let url = URL(string: baseURL + "save") else {
            throw APIError.invalidURL
        }

        let boundary = UUID().uuidString
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(bearerToken)", forHTTPHeaderField: "Authorization")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = timeout

        // Create multipart form data
        var body = Data()

        // Add images
        for (index, image) in images.enumerated() {
            // Resize to 1024x1024 and compress
            let resizedImage = resizeImage(image, maxDimension: 1024)
            guard let imageData = resizedImage.jpegData(compressionQuality: 0.85) else { continue }

            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"image\"; filename=\"image_\(index).jpg\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
            body.append(imageData)
            body.append("\r\n".data(using: .utf8)!)
        }

        body.append("--\(boundary)--\r\n".data(using: .utf8)!)

        request.httpBody = body

        // Make request
        let (data, response) = try await URLSession.shared.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            throw APIError.httpError(statusCode: httpResponse.statusCode)
        }

        // Parse response
        return try JSONDecoder().decode(SaveImageResponse.self, from: data)
    }

    private func resizeImage(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let originalWidth = image.size.width
        let originalHeight = image.size.height

        let scale = min(maxDimension / originalWidth, maxDimension / originalHeight)

        // Don't upscale
        if scale >= 1.0 {
            return image
        }

        let newWidth = originalWidth * scale
        let newHeight = originalHeight * scale
        let newSize = CGSize(width: newWidth, height: newHeight)

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    func cancelIdentification() {
        currentTask?.cancel()
    }

    private func stripEXIFData(from image: UIImage, compressionQuality: CGFloat) -> Data? {
        // Re-render the image without metadata using UIGraphicsImageRenderer
        let size = image.size
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale

        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let strippedImage = renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: size))
        }

        return strippedImage.jpegData(compressionQuality: compressionQuality)
    }

    private func mapWarnings(_ warningsDTO: WarningsDTO?) -> Warnings? {
        guard let dto = warningsDTO else { return nil }

        let generalWarnings = dto.general?.compactMap { itemDTO -> WarningItem? in
            guard let category = WarningCategory(rawValue: itemDTO.category.lowercased()) else {
                return WarningItem(
                    category: .info,
                    title: itemDTO.title,
                    message: itemDTO.message,
                    link: itemDTO.link
                )
            }
            return WarningItem(
                category: category,
                title: itemDTO.title,
                message: itemDTO.message,
                link: itemDTO.link
            )
        } ?? []

        var predictionWarnings: [String: [WarningItem]] = [:]
        dto.predictions?.forEach { key, items in
            predictionWarnings[key] = items.compactMap { itemDTO in
                let category = WarningCategory(rawValue: itemDTO.category.lowercased()) ?? .info
                return WarningItem(
                    category: category,
                    title: itemDTO.title,
                    message: itemDTO.message,
                    link: itemDTO.link
                )
            }
        }

        // Return nil if there are no warnings
        if generalWarnings.isEmpty && predictionWarnings.isEmpty {
            return nil
        }

        return Warnings(general: generalWarnings, predictions: predictionWarnings)
    }

    private func mapAPIResponseToPredictionResults(_ apiResponse: APIResponse) -> [PredictionResult] {
        guard let predictions = apiResponse.predictions,
              let firstPrediction = predictions.first,
              let taxa = firstPrediction.taxa,
              let items = taxa.items else {
            return []
        }

        let modelInfo = apiResponse.modelInfo.map { dto in
            ModelInfo(
                model: dto.model,
                country: dto.country,
                locationSource: dto.locationSource
            )
        }

        return items.compactMap { item in
            guard let scientificNameId = item.scientificNameId,
                  !scientificNameId.isEmpty,
                  let probability = item.probability,
                  probability > 0.0 else {
                return nil
            }

            // Create unique ID by combining scientificNameId with probability to handle duplicates
            let uniqueId = "\(scientificNameId)_\(probability)"

            return PredictionResult(
                id: uniqueId,
                vernacularNames: item.vernacularNames?.filter { !$0.value.isEmpty },
                scientificName: item.scientificName?.isEmpty == false ? item.scientificName : nil,
                groupNames: item.groupNames?.filter { !$0.value.isEmpty },
                probability: probability,
                pictureUrl: item.picture?.isEmpty == false ? item.picture : nil,
                infoUrl: item.infoUrl?.isEmpty == false ? item.infoUrl : nil,
                modelInfo: modelInfo,
                redListCategory: item.redListCategory?.isEmpty == false ? item.redListCategory : nil,
                invasiveCategory: item.invasiveCategory?.isEmpty == false ? item.invasiveCategory : nil
            )
        }
    }
}

enum APIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(statusCode: Int)
    case decodingError(Error)
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "Invalid API URL"
        case .invalidResponse:
            return "Invalid server response"
        case .httpError(let code):
            return "Server error: \(code)"
        case .decodingError(let error):
            return "Failed to parse response: \(error.localizedDescription)"
        case .networkError(let error):
            return "Network error: \(error.localizedDescription)"
        }
    }
}

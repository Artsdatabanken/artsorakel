import Foundation
import UIKit
import CoreLocation
import SwiftUI

class SpeciesAPIService {
    static let shared = SpeciesAPIService()

    private let baseURL = AppConfig.API.baseURL
    private let bearerToken = AppConfig.API.bearerToken
    private let timeout = AppConfig.API.timeout

    private var currentTask: URLSessionDataTask?

    private init() {}

    func identifySpecies(images: [UIImage], location: CLLocation?) async throws -> [PredictionResult] {
        guard let url = URL(string: baseURL) else {
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
        return mapAPIResponseToPredictionResults(apiResponse)
    }

    func cancelIdentification() {
        currentTask?.cancel()
    }

    private func stripEXIFData(from image: UIImage, compressionQuality: CGFloat) -> Data? {
        // Create a new image context to re-render the image without metadata
        guard let cgImage = image.cgImage else { return nil }

        // Create a new bitmap context
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue)

        guard let context = CGContext(
            data: nil,
            width: cgImage.width,
            height: cgImage.height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: bitmapInfo.rawValue
        ) else { return nil }

        // Draw the image in the context (this strips metadata)
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: cgImage.width, height: cgImage.height))

        // Get the new CGImage without metadata
        guard let newCGImage = context.makeImage() else { return nil }

        // Convert to UIImage and then to JPEG data (without EXIF)
        let newImage = UIImage(cgImage: newCGImage, scale: image.scale, orientation: .up)
        return newImage.jpegData(compressionQuality: compressionQuality)
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

            return PredictionResult(
                id: scientificNameId,
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

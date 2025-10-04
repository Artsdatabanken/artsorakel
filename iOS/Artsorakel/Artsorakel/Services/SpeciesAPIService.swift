import Foundation
import UIKit
import CoreLocation

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

        // Add location if available
        if let location = location {
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
            guard let imageData = image.jpegData(compressionQuality: 0.85) else { continue }

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
        let apiResponse = try JSONDecoder().decode(APIResponse.self, from: data)

        // Map to domain models
        return mapAPIResponseToPredictionResults(apiResponse)
    }

    func cancelIdentification() {
        currentTask?.cancel()
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

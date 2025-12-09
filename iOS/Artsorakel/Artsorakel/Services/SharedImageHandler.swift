import Foundation
import UIKit
import CoreLocation
import ImageIO

/// Handles shared image data between the Share Extension and the main app
/// Uses App Groups to store data in a shared container
class SharedImageHandler {
    static let shared = SharedImageHandler()

    // App Group identifier - must match the one configured in Xcode for both targets
    static let appGroupIdentifier = "group.no.artsdatabanken.orakel"

    private var sharedContainerURL: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: SharedImageHandler.appGroupIdentifier)
    }

    private var pendingImageURL: URL? {
        sharedContainerURL?.appendingPathComponent("pending_shared_image.jpg")
    }

    private var pendingMetadataURL: URL? {
        sharedContainerURL?.appendingPathComponent("pending_shared_metadata.json")
    }

    struct SharedImageMetadata: Codable {
        let latitude: Double?
        let longitude: Double?
        let altitude: Double?
        let timestamp: Date
    }

    /// Saves a shared image and its location to the shared container
    /// Called from the Share Extension
    func saveSharedImage(_ image: UIImage, location: CLLocation?) -> Bool {
        guard let pendingImageURL = pendingImageURL,
              let pendingMetadataURL = pendingMetadataURL else {
            return false
        }

        // Save image as JPEG
        guard let imageData = image.jpegData(compressionQuality: 0.95) else {
            return false
        }

        do {
            try imageData.write(to: pendingImageURL)

            // Save metadata
            let metadata = SharedImageMetadata(
                latitude: location?.coordinate.latitude,
                longitude: location?.coordinate.longitude,
                altitude: location?.altitude,
                timestamp: Date()
            )
            let encoder = JSONEncoder()
            let metadataData = try encoder.encode(metadata)
            try metadataData.write(to: pendingMetadataURL)

            return true
        } catch {
            return false
        }
    }

    /// Checks if there's a pending shared image from the Share Extension
    func hasPendingSharedImage() -> Bool {
        guard let pendingImageURL = pendingImageURL else { return false }
        return FileManager.default.fileExists(atPath: pendingImageURL.path)
    }

    /// Loads the pending shared image and its location
    /// Returns nil if no pending image or if loading fails
    func loadPendingSharedImage() -> (image: UIImage, location: CLLocation?)? {
        guard let pendingImageURL = pendingImageURL,
              let pendingMetadataURL = pendingMetadataURL else {
            return nil
        }

        guard FileManager.default.fileExists(atPath: pendingImageURL.path) else {
            return nil
        }

        do {
            let imageData = try Data(contentsOf: pendingImageURL)
            guard let image = UIImage(data: imageData) else {
                return nil
            }

            // Normalize image orientation (fix EXIF rotation from shared images)
            let normalizedImage = normalizeImageOrientation(image)

            var location: CLLocation? = nil

            if FileManager.default.fileExists(atPath: pendingMetadataURL.path) {
                let metadataData = try Data(contentsOf: pendingMetadataURL)
                let decoder = JSONDecoder()
                let metadata = try decoder.decode(SharedImageMetadata.self, from: metadataData)

                if let lat = metadata.latitude, let lon = metadata.longitude {
                    location = CLLocation(
                        coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
                        altitude: metadata.altitude ?? 0,
                        horizontalAccuracy: 0,
                        verticalAccuracy: 0,
                        timestamp: metadata.timestamp
                    )
                }
            }

            return (normalizedImage, location)
        } catch {
            return nil
        }
    }

    /// Clears any pending shared image
    func clearPendingSharedImage() {
        guard let pendingImageURL = pendingImageURL,
              let pendingMetadataURL = pendingMetadataURL else {
            return
        }

        try? FileManager.default.removeItem(at: pendingImageURL)
        try? FileManager.default.removeItem(at: pendingMetadataURL)
    }

    /// Extracts GPS location from image data (EXIF metadata)
    /// Used in the Share Extension to get location from shared images
    static func extractLocation(from imageData: Data) -> CLLocation? {
        guard let imageSource = CGImageSourceCreateWithData(imageData as CFData, nil),
              let properties = CGImageSourceCopyPropertiesAtIndex(imageSource, 0, nil) as? [String: Any],
              let gpsInfo = properties[kCGImagePropertyGPSDictionary as String] as? [String: Any],
              let latitude = gpsInfo[kCGImagePropertyGPSLatitude as String] as? Double,
              let longitude = gpsInfo[kCGImagePropertyGPSLongitude as String] as? Double,
              let latRef = gpsInfo[kCGImagePropertyGPSLatitudeRef as String] as? String,
              let lonRef = gpsInfo[kCGImagePropertyGPSLongitudeRef as String] as? String else {
            return nil
        }

        let lat = latRef == "S" ? -latitude : latitude
        let lon = lonRef == "W" ? -longitude : longitude

        let altitude = gpsInfo[kCGImagePropertyGPSAltitude as String] as? Double ?? 0

        return CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lon),
            altitude: altitude,
            horizontalAccuracy: 0,
            verticalAccuracy: 0,
            timestamp: Date()
        )
    }

    /// Normalizes image orientation by rendering with correct rotation applied
    private func normalizeImageOrientation(_ image: UIImage) -> UIImage {
        // If already in up orientation, return as-is
        if image.imageOrientation == .up {
            return image
        }

        // Render the image in the correct orientation
        let format = UIGraphicsImageRendererFormat()
        format.scale = image.scale
        let renderer = UIGraphicsImageRenderer(size: image.size, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: image.size))
        }
    }
}

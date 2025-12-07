import Foundation
import UIKit

/// Service for storing and managing identification history
class HistoryStorage: ObservableObject {
    static let shared = HistoryStorage()

    private let historyKey = "identification_history"
    private let imagesDirectory = "history_images"
    private let maxHistoryItems = 100

    @Published var history: [IdentificationHistory] = []

    private init() {
        loadHistory()
    }

    // MARK: - Public Methods

    /// Saves an identification result to history
    func saveIdentification(
        results: [PredictionResult],
        images: [UIImage],
        uploadId: String?,
        uploadSecret: String?
    ) {
        guard let bestMatch = results.first else { return }

        // Check if saving history is enabled
        let saveHistoryEnabled = UserDefaults.standard.object(forKey: "saveHistory") as? Bool ?? true
        guard saveHistoryEnabled else { return }

        // Save images to disk
        let imagePaths = saveImages(images)

        // Convert results to JSON
        let encoder = JSONEncoder()
        let allResultsJson: String
        do {
            let data = try encoder.encode(results)
            allResultsJson = String(data: data, encoding: .utf8) ?? "[]"
        } catch {
            print("Failed to encode results: \(error)")
            allResultsJson = "[]"
        }

        // Create history entry
        let historyEntry = IdentificationHistory(
            timestamp: Date(),
            bestMatchVernacularNames: bestMatch.vernacularNames,
            bestMatchScientificName: bestMatch.scientificName,
            bestMatchProbability: bestMatch.probability,
            bestMatchGroupNames: bestMatch.groupNames,
            bestMatchInfoUrl: bestMatch.infoUrl,
            allResults: allResultsJson,
            imagePaths: imagePaths,
            warnings: nil,
            uploadId: uploadId,
            uploadSecret: uploadSecret
        )

        // Add to history
        history.insert(historyEntry, at: 0)

        // Trim history if needed
        if history.count > maxHistoryItems {
            let itemsToRemove = history.suffix(from: maxHistoryItems)
            for item in itemsToRemove {
                deleteImages(for: item)
            }
            history = Array(history.prefix(maxHistoryItems))
        }

        // Save to UserDefaults
        saveHistory()
    }

    /// Deletes a specific history item
    func deleteHistoryItem(_ item: IdentificationHistory) {
        deleteImages(for: item)
        history.removeAll { $0.id == item.id }
        saveHistory()
    }

    /// Deletes all history
    func deleteAllHistory() {
        for item in history {
            deleteImages(for: item)
        }
        history.removeAll()
        saveHistory()
    }

    /// Gets recent history items for display
    func getRecentHistory(limit: Int = 3) -> [IdentificationHistory] {
        return Array(history.prefix(limit))
    }

    /// Parses stored JSON back to PredictionResult list
    func parseResultsFromJson(_ json: String) -> [PredictionResult] {
        guard let data = json.data(using: .utf8) else { return [] }
        do {
            return try JSONDecoder().decode([PredictionResult].self, from: data)
        } catch {
            print("Failed to decode results: \(error)")
            return []
        }
    }

    /// Loads an image from a history entry (path can be absolute or relative filename)
    func loadImage(at path: String) -> UIImage? {
        let url: URL

        // Check if it's just a filename (relative) or full path (legacy)
        if path.contains("/") {
            // Legacy absolute path - try to extract filename and resolve
            let filename = (path as NSString).lastPathComponent
            url = getImagesDirectory().appendingPathComponent(filename)
        } else {
            // Relative filename
            url = getImagesDirectory().appendingPathComponent(path)
        }

        guard let data = try? Data(contentsOf: url) else { return nil }
        return UIImage(data: data)
    }

    /// Loads all images for a history entry
    func loadImages(for item: IdentificationHistory) -> [UIImage] {
        return item.imagePaths.compactMap { loadImage(at: $0) }
    }

    // MARK: - Private Methods

    private func loadHistory() {
        guard let data = UserDefaults.standard.data(forKey: historyKey) else { return }
        do {
            history = try JSONDecoder().decode([IdentificationHistory].self, from: data)
        } catch {
            print("Failed to load history: \(error)")
            history = []
        }
    }

    private func saveHistory() {
        do {
            let data = try JSONEncoder().encode(history)
            UserDefaults.standard.set(data, forKey: historyKey)
        } catch {
            print("Failed to save history: \(error)")
        }
    }

    private func getImagesDirectory() -> URL {
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let imagesPath = documentsPath.appendingPathComponent(imagesDirectory)

        // Create directory if it doesn't exist
        if !FileManager.default.fileExists(atPath: imagesPath.path) {
            try? FileManager.default.createDirectory(at: imagesPath, withIntermediateDirectories: true)
        }

        return imagesPath
    }

    private func saveImages(_ images: [UIImage]) -> [String] {
        let imagesDir = getImagesDirectory()
        let timestamp = ISO8601DateFormatter().string(from: Date())
            .replacingOccurrences(of: ":", with: "-")
            .replacingOccurrences(of: "+", with: "-")

        var filenames: [String] = []

        for (index, image) in images.enumerated() {
            // Resize to 1024x1024 max while preserving aspect ratio
            let resizedImage = resizeImage(image, maxDimension: 1024)

            // Save as JPEG
            guard let data = resizedImage.jpegData(compressionQuality: 0.85) else { continue }

            let filename = "img_\(timestamp)_\(index).jpg"
            let filePath = imagesDir.appendingPathComponent(filename)

            do {
                try data.write(to: filePath)
                // Store only the filename, not the full path
                filenames.append(filename)
            } catch {
                print("Failed to save image: \(error)")
            }
        }

        return filenames
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

        UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
        image.draw(in: CGRect(origin: .zero, size: newSize))
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return newImage ?? image
    }

    private func deleteImages(for item: IdentificationHistory) {
        let imagesDir = getImagesDirectory()
        for path in item.imagePaths {
            let filename = path.contains("/") ? (path as NSString).lastPathComponent : path
            let fullPath = imagesDir.appendingPathComponent(filename)
            try? FileManager.default.removeItem(at: fullPath)
        }
    }
}

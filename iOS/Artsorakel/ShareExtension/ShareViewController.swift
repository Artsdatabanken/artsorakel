//
//  ShareViewController.swift
//  ShareExtension
//
//  Created by Wouter Koch on 07/12/2025.
//

import UIKit
import UniformTypeIdentifiers
import CoreLocation
import ImageIO

class ShareViewController: UIViewController {

    // App Group identifier - must match SharedImageHandler and Xcode configuration
    static let appGroupIdentifier = "group.no.artsdatabanken.orakel"

    override func viewDidLoad() {
        super.viewDidLoad()
        // Make the view invisible - we don't need UI
        view.backgroundColor = .clear
        handleSharedImage()
    }

    private func handleSharedImage() {
        guard let extensionItem = extensionContext?.inputItems.first as? NSExtensionItem,
              let itemProviders = extensionItem.attachments else {
            completeRequest(success: false)
            return
        }

        // Find image item provider
        for provider in itemProviders {
            // Check for image types
            if provider.hasItemConformingToTypeIdentifier(UTType.image.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.image.identifier, options: nil) { [weak self] data, error in
                    guard error == nil else {
                        self?.completeRequest(success: false)
                        return
                    }

                    self?.processSharedItem(data)
                }
                return
            }
        }

        // No image found
        completeRequest(success: false)
    }

    private func processSharedItem(_ data: Any?) {
        var image: UIImage?
        var location: CLLocation?

        if let url = data as? URL {
            // Image provided as file URL - load with full EXIF access
            if let data = try? Data(contentsOf: url) {
                image = UIImage(data: data)
                location = Self.extractLocation(from: data)
            }
        } else if let data = data as? Data {
            // Image provided as raw data
            image = UIImage(data: data)
            location = Self.extractLocation(from: data)
        } else if let uiImage = data as? UIImage {
            // Image provided as UIImage (no EXIF available)
            image = uiImage
        }

        guard let finalImage = image else {
            completeRequest(success: false)
            return
        }

        // Save to shared container
        let success = saveToSharedContainer(image: finalImage, location: location)

        if success {
            // Open main app
            openMainApp()
        }

        completeRequest(success: success)
    }

    private func saveToSharedContainer(image: UIImage, location: CLLocation?) -> Bool {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier
        ) else {
            return false
        }

        let imageURL = containerURL.appendingPathComponent("pending_shared_image.jpg")
        let metadataURL = containerURL.appendingPathComponent("pending_shared_metadata.json")

        // Save image
        guard let imageData = image.jpegData(compressionQuality: 0.95) else {
            return false
        }

        do {
            try imageData.write(to: imageURL)

            // Save metadata with location
            struct Metadata: Codable {
                let latitude: Double?
                let longitude: Double?
                let altitude: Double?
                let timestamp: Date
            }

            let metadata = Metadata(
                latitude: location?.coordinate.latitude,
                longitude: location?.coordinate.longitude,
                altitude: location?.altitude,
                timestamp: Date()
            )

            let encoder = JSONEncoder()
            let metadataData = try encoder.encode(metadata)
            try metadataData.write(to: metadataURL)

            return true
        } catch {
            print("Error saving shared image: \(error)")
            return false
        }
    }

    private func openMainApp() {
        // Use URL scheme to open main app
        guard let url = URL(string: "artsorakel://shared-image") else { return }

        // Share extensions can't directly open URLs, but we can use the responder chain
        var responder: UIResponder? = self
        while responder != nil {
            if let application = responder as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                return
            }
            responder = responder?.next
        }

        // Alternative: use openURL selector
        let selector = sel_registerName("openURL:")
        responder = self
        while responder != nil {
            if responder!.responds(to: selector) {
                responder!.perform(selector, with: url)
                return
            }
            responder = responder?.next
        }
    }

    private func completeRequest(success: Bool) {
        DispatchQueue.main.async {
            if success {
                self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            } else {
                let error = NSError(domain: "no.artsdatabanken.orakel.share", code: 0, userInfo: nil)
                self.extensionContext?.cancelRequest(withError: error)
            }
        }
    }

    /// Extracts GPS location from image data (EXIF metadata)
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
}

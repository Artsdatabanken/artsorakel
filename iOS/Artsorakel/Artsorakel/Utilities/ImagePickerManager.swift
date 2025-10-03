import SwiftUI
import UIKit
import CoreLocation

struct ImagePickerManager: UIViewControllerRepresentable {
    @Binding var isPresented: Bool
    let sourceType: UIImagePickerController.SourceType
    let onImagePicked: (UIImage, CLLocation?) -> Void
    let onUnavailable: (() -> Void)?

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()

        picker.sourceType = sourceType
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePickerManager

        init(_ parent: ImagePickerManager) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            parent.isPresented = false

            guard let image = info[.originalImage] as? UIImage else {
                return
            }

            // Extract location from image metadata if available
            var location: CLLocation?
            if let imageURL = info[.imageURL] as? URL {
                location = extractLocation(from: imageURL)
            } else if let asset = info[.phAsset] as? PHAsset {
                location = asset.location
            }

            parent.onImagePicked(image, location)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.isPresented = false
        }

        private func extractLocation(from url: URL) -> CLLocation? {
            guard let imageSource = CGImageSourceCreateWithURL(url as CFURL, nil),
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
}

import Photos

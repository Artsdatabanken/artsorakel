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

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, CLLocationManagerDelegate {
        let parent: ImagePickerManager
        let locationManager = CLLocationManager()
        var currentLocation: CLLocation?

        init(_ parent: ImagePickerManager) {
            self.parent = parent
            super.init()

            // Set up location manager for camera
            if parent.sourceType == .camera {
                locationManager.delegate = self
                locationManager.desiredAccuracy = kCLLocationAccuracyBest

                // Request permission if needed
                if locationManager.authorizationStatus == .notDetermined {
                    locationManager.requestWhenInUseAuthorization()
                }

                // Start getting location
                if locationManager.authorizationStatus == .authorizedWhenInUse ||
                   locationManager.authorizationStatus == .authorizedAlways {
                    locationManager.startUpdatingLocation()
                }
            }
        }

        func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            currentLocation = locations.last
        }

        func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
            if status == .authorizedWhenInUse || status == .authorizedAlways {
                locationManager.startUpdatingLocation()
            }
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            parent.isPresented = false
            locationManager.stopUpdatingLocation()

            guard let image = info[.originalImage] as? UIImage else {
                return
            }

            // Extract location from image metadata if available (gallery)
            var location: CLLocation?
            if let imageURL = info[.imageURL] as? URL {
                location = extractLocation(from: imageURL)
            } else if let asset = info[.phAsset] as? PHAsset {
                location = asset.location
            } else if parent.sourceType == .camera {
                // For camera, use current location
                location = currentLocation
            }

            // Normalize image orientation - rotate the actual image data to .up orientation
            let normalizedImage = normalizeImageOrientation(image)

            parent.onImagePicked(normalizedImage, location)
        }

        private func normalizeImageOrientation(_ image: UIImage) -> UIImage {
            // If already in up orientation, return as-is
            if image.imageOrientation == .up {
                return image
            }

            // Render the image in the correct orientation
            UIGraphicsBeginImageContextWithOptions(image.size, false, image.scale)
            image.draw(in: CGRect(origin: .zero, size: image.size))
            let normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            return normalizedImage ?? image
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.isPresented = false
            locationManager.stopUpdatingLocation()
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

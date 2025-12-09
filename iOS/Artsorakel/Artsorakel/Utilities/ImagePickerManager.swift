import SwiftUI
import UIKit
import CoreLocation
import Photos
import PhotosUI
import ImageIO

struct ImagePickerManager: UIViewControllerRepresentable {
    @Binding var isPresented: Bool
    let sourceType: UIImagePickerController.SourceType
    let onImagePicked: (UIImage, CLLocation?) -> Void
    let onUnavailable: (() -> Void)?

    func makeUIViewController(context: Context) -> UIViewController {
        if sourceType == .camera {
            // Use UIImagePickerController for camera
            let picker = UIImagePickerController()
            picker.sourceType = .camera
            picker.delegate = context.coordinator
            return picker
        } else {
            // Use PHPickerViewController for photo library to get proper asset access
            var config = PHPickerConfiguration(photoLibrary: .shared())
            config.filter = .images
            config.selectionLimit = 1
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = context.coordinator
            return picker
        }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate, CLLocationManagerDelegate {
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

        // MARK: - CLLocationManagerDelegate

        func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
            currentLocation = locations.last
        }

        func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
            if status == .authorizedWhenInUse || status == .authorizedAlways {
                locationManager.startUpdatingLocation()
            }
        }

        // MARK: - PHPickerViewControllerDelegate (Photo Library)

        func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
            parent.isPresented = false

            guard let result = results.first else { return }

            // Get location from PHAsset using the asset identifier
            // This requires photo library read access
            let assetIdentifier = result.assetIdentifier

            // Load the image first
            result.itemProvider.loadObject(ofClass: UIImage.self) { [weak self] object, error in
                guard let self = self, let image = object as? UIImage else { return }

                // Normalize orientation
                let normalizedImage = self.normalizeImageOrientation(image)

                // Try to get location from PHAsset (requires read permission)
                self.fetchLocationFromAsset(identifier: assetIdentifier) { location in
                    DispatchQueue.main.async {
                        self.parent.onImagePicked(normalizedImage, location)
                    }
                }
            }
        }

        private func fetchLocationFromAsset(identifier: String?, completion: @escaping (CLLocation?) -> Void) {
            guard let identifier = identifier else {
                completion(nil)
                return
            }

            // Check current authorization status
            let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)

            switch status {
            case .authorized, .limited:
                // We have access, fetch the asset
                let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
                completion(fetchResult.firstObject?.location)

            case .notDetermined:
                // Request access
                PHPhotoLibrary.requestAuthorization(for: .readWrite) { newStatus in
                    if newStatus == .authorized || newStatus == .limited {
                        let fetchResult = PHAsset.fetchAssets(withLocalIdentifiers: [identifier], options: nil)
                        completion(fetchResult.firstObject?.location)
                    } else {
                        completion(nil)
                    }
                }

            default:
                // Denied or restricted
                completion(nil)
            }
        }

        // MARK: - UIImagePickerControllerDelegate (Camera)

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            parent.isPresented = false
            locationManager.stopUpdatingLocation()

            guard let image = info[.originalImage] as? UIImage else {
                return
            }

            // For camera, use current location
            let location = currentLocation

            // Save camera image to Artsorakel album with location
            saveCameraImageToAlbum(image: image, location: location)

            // Normalize image orientation
            let normalizedImage = normalizeImageOrientation(image)

            parent.onImagePicked(normalizedImage, location)
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.isPresented = false
            locationManager.stopUpdatingLocation()
        }

        // MARK: - Image Helpers

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

        // MARK: - Photo Library Helpers

        private func saveCameraImageToAlbum(image: UIImage, location: CLLocation?) {
            PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
                guard status == .authorized else { return }

                // Fetch or create album BEFORE the change block to avoid deadlock
                let album = self.fetchOrCreateArtsorakelAlbum()

                PHPhotoLibrary.shared().performChanges({
                    let creationRequest = PHAssetCreationRequest.forAsset()
                    creationRequest.addResource(with: .photo, data: image.jpegData(compressionQuality: 0.95)!, options: nil)

                    if let location = location {
                        creationRequest.location = location
                    }
                    creationRequest.creationDate = Date()

                    // Try to add to Artsorakel album
                    if let album = album {
                        if let placeholder = creationRequest.placeholderForCreatedAsset {
                            let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                            albumChangeRequest?.addAssets([placeholder] as NSArray)
                        }
                    }
                }, completionHandler: { success, error in
                    if let error = error {
                        print("Error saving image to photo library: \(error)")
                    }
                })
            }
        }

        private func fetchOrCreateArtsorakelAlbum() -> PHAssetCollection? {
            // Try to fetch existing album
            let fetchOptions = PHFetchOptions()
            fetchOptions.predicate = NSPredicate(format: "localizedTitle = %@", "Artsorakel")
            let collections = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .albumRegular, options: fetchOptions)

            if let album = collections.firstObject {
                return album
            }

            // Create new album if it doesn't exist
            var localIdentifier: String?
            do {
                try PHPhotoLibrary.shared().performChangesAndWait {
                    let createAlbumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: "Artsorakel")
                    localIdentifier = createAlbumRequest.placeholderForCreatedAssetCollection.localIdentifier
                }

                if let identifier = localIdentifier {
                    let fetchResult = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [identifier], options: nil)
                    return fetchResult.firstObject
                }
            } catch {
                print("Error creating album: \(error)")
            }

            return nil
        }
    }
}

import SwiftUI
import CoreLocation

struct ImageCropperView: View {
    @Binding var isPresented: Bool
    let image: UIImage
    let location: CLLocation?
    let onCropComplete: (UIImage, CLLocation?) -> Void

    @State private var scale: CGFloat = 1.5
    @State private var lastScale: CGFloat = 1.5
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var cropSize: CGFloat = 0
    @State private var debugMessage: String?
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager

    var body: some View {
        VStack(spacing: 0) {
            // Image area with overlay
            Color.black
                .overlay(
                    GeometryReader { geometry in
                        let viewSize = geometry.size
                        let imageAspect = image.size.width / image.size.height
                        let viewAspect = viewSize.width / viewSize.height

                        let displayWidth = imageAspect > viewAspect ? viewSize.width : viewSize.height * imageAspect
                        let displayHeight = imageAspect > viewAspect ? viewSize.width / imageAspect : viewSize.height

                        let cropSquareSize = min(viewSize.width, viewSize.height) * 0.8

                        ZStack {
                            // Image with gestures
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFit()
                                .frame(width: displayWidth, height: displayHeight)
                                .scaleEffect(scale)
                                .offset(offset)
                                .simultaneousGesture(
                                    MagnificationGesture()
                                        .onChanged { value in
                                            let newScale = lastScale * value
                                            let minScale = cropSquareSize / min(displayWidth, displayHeight)
                                            scale = max(newScale, minScale)
                                        }
                                        .onEnded { value in
                                            lastScale = scale
                                        }
                                )
                                .simultaneousGesture(
                                    DragGesture(minimumDistance: 0)
                                        .onChanged { value in
                                            let newOffset = CGSize(
                                                width: lastOffset.width + value.translation.width,
                                                height: lastOffset.height + value.translation.height
                                            )

                                            let scaledWidth = displayWidth * scale
                                            let scaledHeight = displayHeight * scale

                                            let maxOffsetX = (scaledWidth - cropSquareSize) / 2
                                            let maxOffsetY = (scaledHeight - cropSquareSize) / 2

                                            offset = CGSize(
                                                width: min(maxOffsetX, max(-maxOffsetX, newOffset.width)),
                                                height: min(maxOffsetY, max(-maxOffsetY, newOffset.height))
                                            )
                                        }
                                        .onEnded { value in
                                            lastOffset = offset
                                        }
                                )

                            // Crop overlay
                            CropOverlay(size: viewSize)
                                .allowsHitTesting(false)
                        }
                        .onAppear {
                            cropSize = cropSquareSize
                        }
                    }
                )

            // Hint text
            Text(localizationManager.localize("crop_hint", comment: "Pinch and drag to adjust the crop area"))
                .font(DesignSystem.Typography.body())
                .foregroundColor(Color.textPrimary)
                .multilineTextAlignment(.center)
                .padding(DesignSystem.Spacing.small)
                .frame(maxWidth: .infinity)
                .background(Color.backgroundSubtle)

            // Bottom buttons
            HStack(spacing: DesignSystem.Spacing.small) {
                // Cancel button
                Button(action: {
                    isPresented = false
                }) {
                    if resourceExists("ic_arrow_back") {
                        SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .surfacePrimary)
                    } else {
                        Image(systemName: "arrow.left")
                            .font(.system(size: DesignSystem.IconSize.standard))
                            .foregroundColor(Color.surfacePrimary)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)

                // Continue button
                Button(action: {
                    cropImage()
                }) {
                    if resourceExists("ic_check") {
                        SVGWebView(svgName: "ic_check", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .surfacePrimary)
                    } else {
                        Image(systemName: "checkmark")
                            .font(.system(size: DesignSystem.IconSize.standard))
                            .foregroundColor(Color.surfacePrimary)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)
            }
            .padding(.horizontal, DesignSystem.Spacing.standard)
            .padding(.vertical, DesignSystem.Spacing.standard)
            .background(Color.surfaceAccent)
        }
        .overlay(
            Group {
                if let message = debugMessage {
                    VStack {
                        Spacer()
                        Text(message)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white)
                            .padding()
                            .background(Color.black.opacity(0.8))
                            .cornerRadius(8)
                            .padding()
                    }
                    .transition(.move(edge: .bottom))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                            debugMessage = nil
                        }
                    }
                }
            }
        )
        .edgesIgnoringSafeArea(.all)
    }

    private func cropImage() {
        guard let cgImage = image.cgImage else {
            debugMessage = "ERROR: No cgImage\nOrientation: \(image.imageOrientation.rawValue)"
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                isPresented = false
            }
            return
        }

        let imageSize = image.size
        let screenCropSize = cropSize

        // Validate crop size is initialized
        guard screenCropSize > 0 && screenCropSize.isFinite else {
            debugMessage = "ERROR: Invalid crop size\nSize: \(screenCropSize)"
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                isPresented = false
            }
            return
        }

        // Calculate display dimensions
        let imageAspect = imageSize.width / imageSize.height
        let screenImageWidth: CGFloat
        let screenImageHeight: CGFloat

        if imageAspect > 1 {
            screenImageWidth = screenCropSize / 0.8
            screenImageHeight = screenImageWidth / imageAspect
        } else {
            screenImageHeight = screenCropSize / 0.8
            screenImageWidth = screenImageHeight * imageAspect
        }

        let scaledScreenWidth = screenImageWidth * scale
        let scaledScreenHeight = screenImageHeight * scale

        let screenCropX = (scaledScreenWidth - screenCropSize) / 2 - offset.width
        let screenCropY = (scaledScreenHeight - screenCropSize) / 2 - offset.height

        let pixelRatio = imageSize.width / screenImageWidth
        let cropX = screenCropX * pixelRatio / scale
        let cropY = screenCropY * pixelRatio / scale
        let cropSizeInPixels = screenCropSize * pixelRatio / scale

        // Validate calculations
        guard cropX.isFinite && cropY.isFinite && cropSizeInPixels.isFinite && cropSizeInPixels > 0 else {
            debugMessage = "ERROR: Invalid crop calculations\nX:\(cropX) Y:\(cropY) Size:\(cropSizeInPixels)"
            DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
                isPresented = false
            }
            return
        }

        // Clamp to image bounds
        let clampedX = max(0, min(cropX, imageSize.width - cropSizeInPixels))
        let clampedY = max(0, min(cropY, imageSize.height - cropSizeInPixels))
        let clampedSize = min(cropSizeInPixels, imageSize.width - clampedX, imageSize.height - clampedY)

        let cropRect = CGRect(
            x: clampedX,
            y: clampedY,
            width: clampedSize,
            height: clampedSize
        )

        // Crop the image
        if let croppedCGImage = cgImage.cropping(to: cropRect) {
            let croppedUIImage = UIImage(cgImage: croppedCGImage, scale: image.scale, orientation: image.imageOrientation)

            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 500, height: 500))
            let finalImage = renderer.image { context in
                croppedUIImage.draw(in: CGRect(x: 0, y: 0, width: 500, height: 500))
            }

            isPresented = false
            onCropComplete(finalImage, location)
        } else {
            let rectXInt = cropRect.origin.x.isFinite ? Int(cropRect.origin.x) : -1
            let rectYInt = cropRect.origin.y.isFinite ? Int(cropRect.origin.y) : -1
            let rectWInt = cropRect.width.isFinite ? Int(cropRect.width) : -1
            let rectHInt = cropRect.height.isFinite ? Int(cropRect.height) : -1
            let imgWInt = imageSize.width.isFinite ? Int(imageSize.width) : -1
            let imgHInt = imageSize.height.isFinite ? Int(imageSize.height) : -1

            debugMessage = "CROP FAILED!\nRect: \(rectXInt),\(rectYInt) \(rectWInt)x\(rectHInt)\nImage: \(imgWInt)x\(imgHInt)"
            DispatchQueue.main.asyncAfter(deadline: .now() + 8) {
                isPresented = false
            }
        }
    }
}

struct CropOverlay: View {
    let size: CGSize

    var body: some View {
        let cropSize = min(size.width, size.height) * 0.8

        ZStack {
            // Dark overlay
            Color.black.opacity(0.5)

            // Clear center square
            Rectangle()
                .frame(width: cropSize, height: cropSize)
                .blendMode(.destinationOut)
        }
        .compositingGroup()
        .overlay(
            // White border for the crop square
            Rectangle()
                .stroke(Color.white, lineWidth: 2)
                .frame(width: cropSize, height: cropSize)
        )
        .allowsHitTesting(false)
    }
}

import SwiftUI
import CoreLocation

struct ImageCropperView: View {
    @Binding var isPresented: Bool
    let image: UIImage
    let location: CLLocation?
    let onCropComplete: (UIImage, CLLocation?) -> Void

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var cropSquareSize: CGFloat = 0
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager

    var body: some View {
        VStack(spacing: 0) {
            // Image area with overlay
            GeometryReader { geometry in
                let viewSize = geometry.size
                let squareSize = min(viewSize.width, viewSize.height) * 0.8

                ZStack {
                    Color.black

                    // Image with gestures
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: squareSize, height: squareSize)
                        .scaleEffect(scale)
                        .offset(offset)
                        .clipped()
                        .simultaneousGesture(
                            MagnificationGesture()
                                .onChanged { value in
                                    scale = max(lastScale * value, 1.0)
                                }
                                .onEnded { _ in
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

                                    // Calculate bounds
                                    let imageWidth = image.size.width
                                    let imageHeight = image.size.height
                                    let smallerDimension = min(imageWidth, imageHeight)
                                    let displayScale = squareSize / smallerDimension

                                    let scaledWidth = imageWidth * displayScale * scale
                                    let scaledHeight = imageHeight * displayScale * scale

                                    let maxOffsetX = (scaledWidth - squareSize) / 2
                                    let maxOffsetY = (scaledHeight - squareSize) / 2

                                    offset = CGSize(
                                        width: min(maxOffsetX, max(-maxOffsetX, newOffset.width)),
                                        height: min(maxOffsetY, max(-maxOffsetY, newOffset.height))
                                    )
                                }
                                .onEnded { _ in
                                    lastOffset = offset
                                }
                        )

                    // Crop overlay
                    CropOverlay(size: viewSize, squareSize: squareSize)
                        .allowsHitTesting(false)
                }
                .onAppear {
                    cropSquareSize = squareSize
                }
                .onChange(of: squareSize) { newSize in
                    cropSquareSize = newSize
                }
            }

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
        .edgesIgnoringSafeArea(.all)
    }

    private func cropImage() {
        guard let cgImage = image.cgImage else {
            isPresented = false
            return
        }

        // Image is already rotated to correct orientation by ImagePickerManager
        let imageWidth = CGFloat(cgImage.width)
        let imageHeight = CGFloat(cgImage.height)

        // Find the smaller dimension to determine base scale
        let smallerDimension = min(imageWidth, imageHeight)

        // This is how many pixels fit in the crop square at scale 1.0
        let baseDisplayScale = cropSquareSize / smallerDimension

        // Account for user zoom
        let totalScale = baseDisplayScale * scale

        // Convert offset from screen points to image pixels
        // Offset is how far from center the image has been dragged
        let pixelOffsetX = -offset.width / baseDisplayScale / scale
        let pixelOffsetY = -offset.height / baseDisplayScale / scale

        // The crop square in pixel coordinates
        // It's centered on the image, then offset by the drag amount
        let cropSizeInPixels = smallerDimension / scale
        let cropX = (imageWidth - cropSizeInPixels) / 2 + pixelOffsetX
        let cropY = (imageHeight - cropSizeInPixels) / 2 + pixelOffsetY

        // Clamp to image bounds
        let clampedX = max(0, min(cropX, imageWidth - cropSizeInPixels))
        let clampedY = max(0, min(cropY, imageHeight - cropSizeInPixels))
        let clampedSize = min(cropSizeInPixels, imageWidth - clampedX, imageHeight - clampedY)

        let cropRect = CGRect(
            x: clampedX,
            y: clampedY,
            width: clampedSize,
            height: clampedSize
        )

        // Perform the crop
        if let croppedCGImage = cgImage.cropping(to: cropRect) {
            // Create final 500x500 image
            let renderer = UIGraphicsImageRenderer(size: CGSize(width: 500, height: 500))
            let finalImage = renderer.image { context in
                UIImage(cgImage: croppedCGImage).draw(in: CGRect(x: 0, y: 0, width: 500, height: 500))
            }

            isPresented = false
            onCropComplete(finalImage, location)
        } else {
            isPresented = false
        }
    }
}

struct CropOverlay: View {
    let size: CGSize
    let squareSize: CGFloat

    var body: some View {
        ZStack {
            // Dark overlay
            Color.black.opacity(0.5)

            // Clear center square
            Rectangle()
                .frame(width: squareSize, height: squareSize)
                .blendMode(.destinationOut)
        }
        .compositingGroup()
        .overlay(
            // White border for the crop square
            Rectangle()
                .stroke(Color.white, lineWidth: 2)
                .frame(width: squareSize, height: squareSize)
        )
        .allowsHitTesting(false)
    }
}

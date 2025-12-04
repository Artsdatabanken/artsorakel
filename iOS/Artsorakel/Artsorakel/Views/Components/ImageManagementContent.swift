import SwiftUI

struct ImageManagementContent: View {
    let images: [CroppedImageData]
    let onReset: () -> Void
    let onAddImage: () -> Void
    let onImageTap: (CroppedImageData) -> Void
    let onIdentify: () -> Void
    let onCameraTap: () -> Void
    let onGalleryTap: () -> Void
    @EnvironmentObject var localizationManager: LocalizationManager

    var body: some View {
        VStack(spacing: 0) {
            // Image card centered in available space
            Spacer()

            VStack(spacing: 0) {
                // Close button
                HStack {
                    Spacer()
                    Button(action: onReset) {
                        ZStack {
                            Circle()
                                .fill(Color.surfaceSubtle)
                                .frame(width: 32, height: 32)

                            if resourceExists("ic_close") {
                                SVGWebView(svgName: "ic_close", width: 16, height: 16, tintColor: .surfaceAccent)
                                    .frame(width: 16, height: 16)
                            } else {
                                Image(systemName: "xmark")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color.surfaceAccent)
                            }
                        }
                    }
                    .padding(DesignSystem.Spacing.small)
                }

                // Hint text
                Text(localizationManager.localize("add_pictures_hint", comment: "Add more pictures or identify"))
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, DesignSystem.Spacing.xxxLarge)
                    .padding(.top, DesignSystem.Spacing.medium)

                // Image thumbnails with horizontal scroll and add button
                GeometryReader { geometry in
                    ScrollViewReader { proxy in
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: DesignSystem.Spacing.small) {
                                ForEach(images) { imageData in
                                    Button(action: {
                                        onImageTap(imageData)
                                    }) {
                                        Image(uiImage: imageData.image)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 90, height: 90)
                                            .cornerRadius(DesignSystem.CornerRadius.medium)
                                            .clipped()
                                    }
                                }

                                // Add button
                                Button(action: onAddImage) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.medium)
                                            .strokeBorder(Color.borderAccent, style: StrokeStyle(lineWidth: 2, dash: [4, 4]))
                                            .frame(width: 90, height: 90)

                                        if resourceExists("ic_add") {
                                            SVGWebView(svgName: "ic_add", width: 24, height: 24, tintColor: .textAccent)
                                                .frame(width: 24, height: 24)
                                        } else {
                                            Image(systemName: "plus")
                                                .font(.system(size: 24))
                                                .foregroundColor(Color.textAccent)
                                        }
                                    }
                                    .frame(width: 90, height: 90)
                                }
                                .id("addButton")
                            }
                            .padding(.horizontal, max(0, (geometry.size.width - DesignSystem.Spacing.small * 2 - CGFloat(images.count + 1) * 90 - CGFloat(images.count) * DesignSystem.Spacing.small) / 2))
                        }
                        .onAppear {
                            proxy.scrollTo("addButton", anchor: .trailing)
                        }
                        .onChange(of: images.count) { _ in
                            proxy.scrollTo("addButton", anchor: .trailing)
                        }
                    }
                    .padding(.horizontal, DesignSystem.Spacing.small)
                }
                .frame(height: 90)
                .padding(.top, DesignSystem.Spacing.medium)

                // Identify button
                Button(action: onIdentify) {
                    Text(localizationManager.localize("identify", comment: "Identify"))
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.surfacePrimary)
                        .frame(height: 56)
                        .padding(.horizontal, 22)
                }
                .background(Color.surfaceAccent)
                .cornerRadius(28)
                .padding(.top, DesignSystem.Spacing.xxLarge)
                .padding(.bottom, DesignSystem.Spacing.xLarge)
            }
            .background(Color.surfacePrimary)
            .cornerRadius(DesignSystem.CornerRadius.medium)
            .padding(.horizontal, DesignSystem.Spacing.standard)

            Spacer()

            // Camera and gallery buttons
            CameraButtonsView(
                onCameraTap: onCameraTap,
                onGalleryTap: onGalleryTap
            )
            .padding(.bottom, DesignSystem.Spacing.standard)
        }
        .background(Color.backgroundSubtle)
    }
}

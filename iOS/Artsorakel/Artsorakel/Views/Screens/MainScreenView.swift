import SwiftUI
import CoreLocation

// Wrapper to make UIImage identifiable for sheet presentation
struct IdentifiableImage: Identifiable {
    let id = UUID()
    let image: UIImage
    let location: CLLocation?
}

// Wrapper for cropped images with locations and original images
struct CroppedImageData: Identifiable {
    let id: UUID
    let image: UIImage
    let originalImage: UIImage
    let location: CLLocation?

    init(id: UUID = UUID(), image: UIImage, originalImage: UIImage, location: CLLocation?) {
        self.id = id
        self.image = image
        self.originalImage = originalImage
        self.location = location
    }
}

struct MainScreenView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @State private var showCamera = false
    @State private var showGallery = false
    @State private var isMenuOpen = false
    @State private var showSettings = false
    @State private var showAbout = false
    @State private var showFAQ = false
    @State private var showImagePicker = false
    @State private var imagePickerSourceType: UIImagePickerController.SourceType = .camera
    @State private var imageToCrop: IdentifiableImage?
    @State private var croppedImages: [CroppedImageData] = []
    @State private var lastInputMethodIsCamera = true
    @State private var recropContext: (imageData: CroppedImageData, index: Int)?
    @State private var isIdentifying = false
    @State private var identificationResults: [PredictionResult]?
    @State private var identificationTask: Task<Void, Never>?

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                HeaderView(isMenuOpen: $isMenuOpen)

                Divider()
                    .frame(height: DesignSystem.ComponentSize.dividerHeight)
                    .background(Color.borderDefault)

                VStack(spacing: 0) {
                    Text(localizationManager.localize("main_title", comment: "Main screen tagline"))
                        .font(DesignSystem.Typography.titleLarge())
                        .foregroundColor(Color.textPrimary)
                        .padding(.horizontal, DesignSystem.Spacing.extraHuge)
                        .padding(.vertical, DesignSystem.Spacing.standard)
                        .multilineTextAlignment(.center)

                    ZStack {
                        Color.backgroundSubtle
                            .ignoresSafeArea()

                        if isIdentifying {
                            LoadingView(onAbort: {
                                identificationTask?.cancel()
                                isIdentifying = false
                            })
                        } else if !croppedImages.isEmpty {
                            ImageManagementContent(
                                images: croppedImages,
                                onReset: {
                                    croppedImages = []
                                },
                                onAddImage: {
                                    if lastInputMethodIsCamera {
                                        showCamera = true
                                    } else {
                                        showGallery = true
                                    }
                                },
                                onImageTap: { imageData in
                                    if let index = croppedImages.firstIndex(where: { $0.id == imageData.id }) {
                                        recropContext = (imageData, index)
                                        imageToCrop = IdentifiableImage(image: imageData.originalImage, location: imageData.location)
                                    }
                                },
                                onIdentify: {
                                    identifySpecies()
                                }
                            )
                            .padding(DesignSystem.Spacing.standard)
                        } else {
                            AvatarView()
                                .padding(DesignSystem.Spacing.xxLarge)
                        }
                    }

                    CameraButtonsView(
                        onCameraTap: {
                            lastInputMethodIsCamera = true
                            showCamera = true
                        },
                        onGalleryTap: {
                            lastInputMethodIsCamera = false
                            showGallery = true
                        }
                    )
                    .padding(.bottom, DesignSystem.Spacing.standard)
                }
                .background(Color.backgroundSubtle)
            }
            .ignoresSafeArea(edges: .bottom)

            if showSettings {
                SettingsView(isPresented: $showSettings, showMenuDrawer: $isMenuOpen)
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }

            if showAbout {
                AboutView(isPresented: $showAbout, showMenuDrawer: $isMenuOpen)
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }

            if showFAQ {
                FAQView(isPresented: $showFAQ, showMenuDrawer: $isMenuOpen)
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
            }

            // Results overlay (like settings/about/faq)
            if let results = identificationResults {
                ResultsView(
                    results: results,
                    images: croppedImages,
                    onReset: {
                        identificationResults = nil
                        croppedImages = []
                    },
                    onAddImage: {
                        // Dismiss results and show image picker based on last method used
                        identificationResults = nil
                        if lastInputMethodIsCamera {
                            showCamera = true
                        } else {
                            showGallery = true
                        }
                    },
                    isMenuOpen: $isMenuOpen
                )
                .transition(.move(edge: .trailing))
                .zIndex(2)
            }

            MenuDrawerView(isOpen: $isMenuOpen, showSettings: $showSettings, showAbout: $showAbout, showFAQ: $showFAQ)
                .zIndex(3)
        }
        .fullScreenCover(item: $imageToCrop) { identifiableImage in
            let capturedRecropContext = recropContext
            ImageCropperView(
                isPresented: Binding(
                    get: { imageToCrop != nil },
                    set: { if !$0 {
                        imageToCrop = nil
                        recropContext = nil
                    } }
                ),
                image: identifiableImage.image,
                location: identifiableImage.location,
                isRecropping: capturedRecropContext != nil,
                onCropComplete: { croppedImg, loc in
                    if let context = capturedRecropContext {
                        // Replace existing image - preserve the ID
                        croppedImages[context.index] = CroppedImageData(
                            id: context.imageData.id,
                            image: croppedImg,
                            originalImage: identifiableImage.image,
                            location: loc
                        )
                    } else {
                        // Add new image
                        croppedImages.append(CroppedImageData(
                            image: croppedImg,
                            originalImage: identifiableImage.image,
                            location: loc
                        ))
                    }
                    imageToCrop = nil
                    recropContext = nil
                },
                onDelete: {
                    if let context = capturedRecropContext {
                        croppedImages.remove(at: context.index)
                    }
                    imageToCrop = nil
                    recropContext = nil
                }
            )
        }
        .sheet(isPresented: $showGallery) {
            ImagePickerManager(
                isPresented: $showGallery,
                sourceType: .photoLibrary,
                onImagePicked: { image, location in
                    imageToCrop = IdentifiableImage(image: image, location: location)
                },
                onUnavailable: nil
            )
        }
        .sheet(isPresented: $showCamera) {
            ImagePickerManager(
                isPresented: $showCamera,
                sourceType: .camera,
                onImagePicked: { image, location in
                    imageToCrop = IdentifiableImage(image: image, location: location)
                },
                onUnavailable: nil
            )
        }
    }

    private func identifySpecies() {
        isIdentifying = true

        identificationTask = Task {
            do {
                let images = croppedImages.map { $0.image }
                // Use the most recent image that has coordinates
                let location = croppedImages.last(where: { $0.location != nil })?.location

                let results = try await SpeciesAPIService.shared.identifySpecies(images: images, location: location)

                await MainActor.run {
                    isIdentifying = false
                    identificationResults = results
                }
            } catch {
                await MainActor.run {
                    isIdentifying = false
                    // TODO: Show error message
                    print("Identification error: \(error)")
                }
            }
        }
    }
}

struct HeaderView: View {
    @Environment(\.colorScheme) var colorScheme
    @Binding var isMenuOpen: Bool

    var body: some View {
        HStack(spacing: 0) {
            if Bundle.main.url(forResource: colorScheme == .dark ? "ic_logo_color_dark" : "ic_logo_color_light", withExtension: "svg") != nil {
                SVGWebView(svgName: colorScheme == .dark ? "ic_logo_color_dark" : "ic_logo_color_light", width: DesignSystem.IconSize.medium, height: 60)
                    .frame(width: DesignSystem.IconSize.medium, height: 60)
                    .padding(.leading, DesignSystem.Spacing.standard)
            }

            SVGWebView(svgName: "chevron_separator", width: DesignSystem.ButtonSize.standard, height: DesignSystem.ComponentSize.headerHeight, tintColor: .borderDefault)
                .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ComponentSize.headerHeight)

            Text("Artsorakel")
                .font(DesignSystem.Typography.title())
                .foregroundColor(Color.textPrimary)
                .padding(.leading, 0)

            Spacer()

            Button(action: {
                withAnimation {
                    isMenuOpen.toggle()
                }
            }) {
                SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                    .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)            }
            .padding(.trailing, DesignSystem.Spacing.standard)
        }
        .frame(height: DesignSystem.ComponentSize.headerHeight)
        .background(Color.surfacePrimary)
    }
}

struct AvatarView: View {
    @Environment(\.colorScheme) var colorScheme

    var avatarName: String {
        colorScheme == .dark ? "Avatar_photo_dark" : "Avatar_photo_light"
    }

    var body: some View {
        GeometryReader { geometry in
                SVGWebView(svgName: avatarName, width: geometry.size.width, height: geometry.size.height)
                    .frame(width: geometry.size.width, height: geometry.size.height)
           
        }
    }
}

struct CameraButtonsView: View {
    let onCameraTap: () -> Void
    let onGalleryTap: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Spacer()
                .frame(width: 72)

            Button(action: onGalleryTap) {
                ZStack {
                    Circle()
                        .fill(Color.surfaceAccent)
                        .frame(width: DesignSystem.ButtonSize.medium, height: DesignSystem.ButtonSize.medium)
                        .applyShadow(DesignSystem.Shadow.small)

                    SVGWebView(svgName: "ic_gallery", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .surfacePrimary)
                        .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                }
                .frame(width: DesignSystem.ButtonSize.medium, height: DesignSystem.ButtonSize.medium)
            }
            .padding(.trailing, DesignSystem.Spacing.standard)
            .padding(.top, DesignSystem.Spacing.xxxLarge)

            Button(action: onCameraTap) {
                ZStack {
                    Circle()
                        .fill(Color.surfaceAccent)
                        .frame(width: DesignSystem.ButtonSize.large, height: DesignSystem.ButtonSize.large)
                        .applyShadow(DesignSystem.Shadow.medium)

                    SVGWebView(svgName: "ic_camera", width: DesignSystem.IconSize.large, height: DesignSystem.IconSize.large, tintColor: .surfacePrimary)
                        .frame(width: DesignSystem.IconSize.large, height: DesignSystem.IconSize.large)
                }
                .frame(width: DesignSystem.ButtonSize.large, height: DesignSystem.ButtonSize.large)
            }

            Spacer()
        }
        .padding(.vertical, DesignSystem.Spacing.standard)
    }
}

#Preview {
    MainScreenView()
}

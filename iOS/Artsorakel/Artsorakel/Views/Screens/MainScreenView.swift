import SwiftUI
import CoreLocation
import Combine

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

// Helper class to request location permission before opening camera
class LocationPermissionHelper: NSObject, ObservableObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    private var permissionCallback: (() -> Void)?

    override init() {
        super.init()
        locationManager.delegate = self
    }

    func requestPermissionThenExecute(_ callback: @escaping () -> Void) {
        let status = locationManager.authorizationStatus
        if status == .notDetermined {
            permissionCallback = callback
            locationManager.requestWhenInUseAuthorization()
        } else {
            callback()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus != .notDetermined {
            DispatchQueue.main.async {
                self.permissionCallback?()
                self.permissionCallback = nil
            }
        }
    }
}

struct MainScreenView: View {
    @Environment(\.colorScheme) var colorScheme
    @Environment(\.scenePhase) var scenePhase
    @EnvironmentObject var localizationManager: LocalizationManager
    @StateObject private var historyStorage = HistoryStorage.shared
    @StateObject private var locationHelper = LocationPermissionHelper()
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
    @State private var recropFromResults = false
    @State private var selectedResult: PredictionResult?

    // History-related state
    @State private var showExpandedHistory = false
    @State private var selectedHistoryItem: IdentificationHistory?
    @State private var currentUploadId: String?
    @State private var currentUploadSecret: String?
    @State private var currentIdentificationTimestamp: Date?
    @State private var isViewingHistoricalResults = false

    // Overlay z-index tracking - each overlay gets assigned a zIndex when opened
    // so the most recently opened overlay appears on top
    @State private var nextZIndex: Double = 1.0
    @State private var settingsZIndex: Double = 0
    @State private var aboutZIndex: Double = 0
    @State private var faqZIndex: Double = 0
    @State private var resultsZIndex: Double = 0
    @State private var speciesDetailZIndex: Double = 0
    @State private var expandedHistoryZIndex: Double = 0

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                HeaderView(isMenuOpen: $isMenuOpen)

                Divider()
                    .frame(height: DesignSystem.ComponentSize.dividerHeight)
                    .background(Color.borderDefault)

                ZStack {
                    // Base content - always present
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

                            AvatarView()
                                .padding(DesignSystem.Spacing.xxLarge)
                        }

                        // History stack (when there's history and no images selected) - below avatar
                        if croppedImages.isEmpty && !historyStorage.history.isEmpty {
                            HistoryStackView(
                                historyStorage: historyStorage,
                                onTap: {
                                    showExpandedHistory = true
                                }
                            )
                        }

                        CameraButtonsView(
                            onCameraTap: {
                                openCamera()
                            },
                            onGalleryTap: {
                                lastInputMethodIsCamera = false
                                showGallery = true
                            }
                        )
                        .padding(.bottom, DesignSystem.Spacing.standard)
                    }
                    .background(Color.backgroundSubtle)

                    // Image management overlay
                    if !croppedImages.isEmpty {
                        ImageManagementContent(
                            images: croppedImages,
                            onReset: {
                                croppedImages = []
                            },
                            onAddImage: {
                                if lastInputMethodIsCamera {
                                    openCamera()
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
                            },
                            onCameraTap: {
                                openCamera()
                            },
                            onGalleryTap: {
                                lastInputMethodIsCamera = false
                                showGallery = true
                            }
                        )
                    }

                    // Loading overlay
                    if isIdentifying {
                        LoadingView(onAbort: {
                            identificationTask?.cancel()
                            isIdentifying = false
                        })
                        .background(Color.backgroundSubtle)
                    }
                }
            }
            .ignoresSafeArea(edges: .bottom)

            if showSettings {
                SettingsView(isPresented: $showSettings, showMenuDrawer: $isMenuOpen)
                    .zIndex(settingsZIndex)
            }

            if showAbout {
                AboutView(isPresented: $showAbout, showMenuDrawer: $isMenuOpen)
                    .zIndex(aboutZIndex)
            }

            if showFAQ {
                FAQView(isPresented: $showFAQ, showMenuDrawer: $isMenuOpen)
                    .zIndex(faqZIndex)
            }

            // Results overlay (like settings/about/faq)
            if let results = identificationResults {
                ResultsView(
                    results: results,
                    images: croppedImages,
                    isHistorical: isViewingHistoricalResults,
                    historicalDate: isViewingHistoricalResults ? currentIdentificationTimestamp : nil,
                    onReset: {
                        // If viewing historical results from expanded history, just close results
                        // to reveal the expanded history beneath
                        if isViewingHistoricalResults && showExpandedHistory {
                            identificationResults = nil
                            croppedImages = []
                            isViewingHistoricalResults = false
                            currentUploadId = nil
                            currentUploadSecret = nil
                            currentIdentificationTimestamp = nil
                            // showExpandedHistory stays true - user returns to history list
                        } else {
                            // Fresh results or not from history - go back to main screen
                            identificationResults = nil
                            croppedImages = []
                            isViewingHistoricalResults = false
                            currentUploadId = nil
                            currentUploadSecret = nil
                            currentIdentificationTimestamp = nil
                            showExpandedHistory = false
                        }
                    },
                    onAddImage: {
                        // Dismiss results and show image picker based on last method used
                        identificationResults = nil
                        isViewingHistoricalResults = false
                        if lastInputMethodIsCamera {
                            openCamera()
                        } else {
                            showGallery = true
                        }
                    },
                    onImageTap: { imageData in
                        // Don't dismiss results yet - only dismiss on OK/delete
                        recropFromResults = true
                        if let index = croppedImages.firstIndex(where: { $0.id == imageData.id }) {
                            recropContext = (imageData, index)
                            imageToCrop = IdentifiableImage(image: imageData.originalImage, location: imageData.location)
                        }
                    },
                    onResultTap: { result in
                        selectedResult = result
                    },
                    isMenuOpen: $isMenuOpen
                )
                .zIndex(resultsZIndex)
            }

            // Species detail overlay
            if let result = selectedResult {
                SpeciesDetailView(
                    result: result,
                    images: croppedImages,
                    uploadId: currentUploadId,
                    uploadSecret: currentUploadSecret,
                    identificationTimestamp: currentIdentificationTimestamp,
                    isHistorical: isViewingHistoricalResults,
                    onClose: {
                        selectedResult = nil
                    },
                    isMenuOpen: $isMenuOpen
                )
                .zIndex(speciesDetailZIndex)
            }

            // Expanded history overlay
            if showExpandedHistory {
                ExpandedHistoryView(
                    historyStorage: historyStorage,
                    isPresented: $showExpandedHistory,
                    isMenuOpen: $isMenuOpen,
                    onSelectItem: { item in
                        loadHistoryResults(item)
                        // Don't close expanded history - it stays beneath results view
                    }
                )
                .zIndex(expandedHistoryZIndex)
            }

            MenuDrawerView(isOpen: $isMenuOpen, showSettings: $showSettings, showAbout: $showAbout, showFAQ: $showFAQ)
                .zIndex(100) // Menu drawer always on top
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
                        // If recropping from results, dismiss results since image changed
                        if recropFromResults {
                            identificationResults = nil
                            recropFromResults = false
                        }
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
                        // If recropping from results, dismiss results since image was deleted
                        if recropFromResults {
                            identificationResults = nil
                            recropFromResults = false
                        }
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
        // Assign zIndex when overlays open so the most recent one is on top
        .onChange(of: showSettings) { newValue in
            if newValue {
                settingsZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onChange(of: showAbout) { newValue in
            if newValue {
                aboutZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onChange(of: showFAQ) { newValue in
            if newValue {
                faqZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onChange(of: showExpandedHistory) { newValue in
            if newValue {
                expandedHistoryZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onChange(of: identificationResults) { newValue in
            if newValue != nil {
                resultsZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onChange(of: selectedResult) { newValue in
            if newValue != nil {
                speciesDetailZIndex = nextZIndex
                nextZIndex += 1
            }
        }
        .onAppear {
            checkForSharedImage()
        }
        .onChange(of: scenePhase) { newPhase in
            if newPhase == .active {
                checkForSharedImage()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .sharedImageReceived)) { _ in
            checkForSharedImage()
        }
    }

    private func checkForSharedImage() {
        guard let (image, location) = SharedImageHandler.shared.loadPendingSharedImage() else {
            return
        }

        // Clear the pending image so we don't process it again
        SharedImageHandler.shared.clearPendingSharedImage()

        // Set the image for cropping (same flow as camera/gallery)
        lastInputMethodIsCamera = false
        imageToCrop = IdentifiableImage(image: image, location: location)
    }

    private func openCamera() {
        lastInputMethodIsCamera = true
        // Request location permission before opening camera so the dialog
        // appears before the camera UI, not over it
        locationHelper.requestPermissionThenExecute {
            showCamera = true
        }
    }

    private func identifySpecies() {
        isIdentifying = true
        isViewingHistoricalResults = false

        identificationTask = Task {
            do {
                let images = croppedImages.map { $0.image }
                // Use the most recent image that has coordinates
                let location = croppedImages.last(where: { $0.location != nil })?.location

                let result = try await SpeciesAPIService.shared.identifySpecies(images: images, location: location)

                await MainActor.run {
                    isIdentifying = false
                    identificationResults = result.predictions
                    currentUploadId = result.uploadId
                    currentUploadSecret = result.uploadSecret
                    currentIdentificationTimestamp = result.timestamp

                    // Save to history
                    historyStorage.saveIdentification(
                        results: result.predictions,
                        images: images,
                        uploadId: result.uploadId,
                        uploadSecret: result.uploadSecret
                    )
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

    private func loadHistoryResults(_ item: IdentificationHistory) {
        // Parse the stored JSON results
        let predictions = historyStorage.parseResultsFromJson(item.allResults)

        guard !predictions.isEmpty else { return }

        // Load images from history
        let historyImages = item.imagePaths.compactMap { path -> CroppedImageData? in
            guard let image = historyStorage.loadImage(at: path) else { return nil }
            return CroppedImageData(image: image, originalImage: image, location: nil)
        }

        // Mark as viewing historical results
        isViewingHistoricalResults = true

        // Set the display state
        croppedImages = historyImages
        identificationResults = predictions
        currentUploadId = item.uploadId
        currentUploadSecret = item.uploadSecret
        currentIdentificationTimestamp = item.timestamp
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
        // Camera button centered, gallery button positioned to its left
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
        .overlay(alignment: .leading) {
            Button(action: onGalleryTap) {
                ZStack {
                    Circle()
                        .fill(Color.surfacePrimary)
                        .frame(width: DesignSystem.ButtonSize.medium, height: DesignSystem.ButtonSize.medium)

                    Circle()
                        .stroke(Color.borderAccent, lineWidth: 2)
                        .frame(width: DesignSystem.ButtonSize.medium, height: DesignSystem.ButtonSize.medium)

                    SVGWebView(svgName: "ic_gallery", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                        .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                }
                .frame(width: DesignSystem.ButtonSize.medium, height: DesignSystem.ButtonSize.medium)
            }
            .offset(x: -(DesignSystem.ButtonSize.medium + DesignSystem.Spacing.standard), y: DesignSystem.Spacing.xxxLarge / 2)
        }
        .padding(.vertical, DesignSystem.Spacing.standard)
    }
}

#Preview {
    MainScreenView()
}

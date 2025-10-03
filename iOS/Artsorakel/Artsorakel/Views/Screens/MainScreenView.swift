import SwiftUI
import CoreLocation

// Wrapper to make UIImage identifiable for sheet presentation
struct IdentifiableImage: Identifiable {
    let id = UUID()
    let image: UIImage
    let location: CLLocation?
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
    @State private var croppedImage: UIImage?
    @State private var imageLocation: CLLocation?

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

                        if let image = croppedImage {
                            ImageManagementContent(
                                image: image,
                                onReset: {
                                    croppedImage = nil
                                    imageLocation = nil
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
                            showCamera = true
                        },
                        onGalleryTap: {
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

            MenuDrawerView(isOpen: $isMenuOpen, showSettings: $showSettings, showAbout: $showAbout, showFAQ: $showFAQ)
                .zIndex(2)
        }
        .fullScreenCover(item: $imageToCrop) { identifiableImage in
            ImageCropperView(
                isPresented: Binding(
                    get: { imageToCrop != nil },
                    set: { if !$0 { imageToCrop = nil } }
                ),
                image: identifiableImage.image,
                location: identifiableImage.location,
                onCropComplete: { croppedImg, loc in
                    croppedImage = croppedImg
                    imageLocation = loc
                    imageToCrop = nil
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
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: DesignSystem.IconSize.medium))
                    .foregroundColor(Color.textAccent)
                    .frame(width: DesignSystem.ButtonSize.standard, height: 60)
            }
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
            if resourceExists(avatarName) {
                SVGWebView(svgName: avatarName, width: geometry.size.width, height: geometry.size.height)
                    .frame(width: geometry.size.width, height: geometry.size.height)
            } else {
                Image(systemName: "photo")
                    .font(.system(size: DesignSystem.Spacing.huge))
                    .foregroundColor(Color.gray.opacity(DesignSystem.Opacity.overlay))
                    .frame(width: geometry.size.width, height: geometry.size.height)
            }
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

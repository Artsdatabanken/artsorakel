import SwiftUI

struct MainScreenView: View {
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @State private var showCamera = false
    @State private var showGallery = false
    @State private var isMenuOpen = false
    @State private var showSettings = false

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                HeaderView(isMenuOpen: $isMenuOpen)

                Divider()
                    .frame(height: 1)
                    .background(Color.borderDefault)

                VStack(spacing: 0) {
                    Text(localizationManager.localize("main_title", comment: "Main screen tagline"))
                        .font(.custom("Chivo", size: 22).weight(.bold))
                        .foregroundColor(Color.textPrimary)
                        .padding(.horizontal, 100)
                        .padding(.vertical, 16)
                        .multilineTextAlignment(.center)

                    ZStack {
                        Color.backgroundSubtle
                            .ignoresSafeArea()

                        AvatarView()
                            .padding(32)
                    }

                    CameraButtonsView(
                        onCameraTap: { showCamera = true },
                        onGalleryTap: { showGallery = true }
                    )
                    .padding(.bottom, 16)
                }
                .background(Color.backgroundSubtle)
            }
            .ignoresSafeArea(edges: .bottom)

            MenuDrawerView(isOpen: $isMenuOpen, showSettings: $showSettings)

            if showSettings {
                SettingsView(isPresented: $showSettings, showMenuDrawer: $isMenuOpen)
                    .transition(.move(edge: .trailing))
                    .zIndex(1)
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
                SVGWebView(svgName: colorScheme == .dark ? "ic_logo_color_dark" : "ic_logo_color_light", width: 20, height: 60)
                    .frame(width: 20, height: 60)
                    .padding(.leading, 16)
            }

            SVGWebView(svgName: "chevron_separator", width: 48, height: 64, tintColor: .borderDefault)
                .frame(width: 48, height: 64)

            Text("Artsorakel")
                .font(.custom("Chivo", size: 18).weight(.bold))
                .foregroundColor(Color.textPrimary)
                .padding(.leading, 0)

            Spacer()

            Button(action: {
                withAnimation {
                    isMenuOpen.toggle()
                }
            }) {
                Image(systemName: "line.3.horizontal")
                    .font(.system(size: 20))
                    .foregroundColor(Color.textAccent)
                    .frame(width: 48, height: 60)
            }
            .padding(.trailing, 16)
        }
        .frame(height: 64)
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
                    .font(.system(size: 80))
                    .foregroundColor(Color.gray.opacity(0.3))
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
                        .frame(width: 56, height: 56)
                        .shadow(color: Color.black.opacity(0.3), radius: 8, x: 0, y: 4)

                    SVGWebView(svgName: "ic_gallery", width: 24, height: 24, tintColor: .surfacePrimary)
                        .frame(width: 24, height: 24)
                }
                .frame(width: 56, height: 56)
            }
            .padding(.trailing, 16)
            .padding(.top, 36)

            Button(action: onCameraTap) {
                ZStack {
                    Circle()
                        .fill(Color.surfaceAccent)
                        .frame(width: 97, height: 97)
                        .shadow(color: Color.black.opacity(0.3), radius: 10, x: 0, y: 6)

                    SVGWebView(svgName: "ic_camera", width: 47, height: 47, tintColor: .surfacePrimary)
                        .frame(width: 47, height: 47)
                }
                .frame(width: 97, height: 97)
            }

            Spacer()
        }
        .padding(.vertical, 16)
    }
}

extension Color {
    static let backgroundDefault = Color("Color_backgroundDefault")
    static let backgroundSubtle = Color("Color_backgroundSubtle")
    static let surfacePrimary = Color("Color_surfacePrimary")
    static let surfaceAccent = Color("Color_surfaceAccent")
    static let textPrimary = Color("Color_textPrimary")
    static let textAccent = Color("Color_textAccent")
    static let borderDefault = Color("Color_borderDefault")
    static let surfaceBrand1a = Color("Color_surfaceBrand1a")
}

#Preview {
    MainScreenView()
}

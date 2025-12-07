import SwiftUI
import AVFoundation
import CoreLocation

struct SettingsView: View {
    @Binding var isPresented: Bool
    @Binding var showMenuDrawer: Bool
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @StateObject private var historyStorage = HistoryStorage.shared
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"
    @AppStorage("saveHistory") private var saveHistory: Bool = true
    @AppStorage("useLocation") private var useLocation: Bool = true
    @State private var showClearHistoryConfirmation: Bool = false

    // Permission states
    @State private var cameraPermission: PermissionStatus = .notDetermined
    @State private var locationPermission: PermissionStatus = .notDetermined

    var body: some View {
        ZStack {
            // Background
            Color.backgroundDefault
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack(spacing: 0) {
                    // Back button
                    Button(action: {
                        withAnimation {
                            isPresented = false
                        }
                    }) {
                            SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                       
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.leading, DesignSystem.Spacing.small)

                    // Title
                    Text(localizationManager.localize("settings", comment: "Settings"))
                        .font(DesignSystem.Typography.titleRegular())
                        .foregroundColor(Color.textPrimary)
                        .frame(maxWidth: .infinity)

                    // Menu button
                    Button(action: {
                        withAnimation {
                            showMenuDrawer = true
                        }
                    }) {
                            SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                        
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.trailing, DesignSystem.Spacing.small)
                }
                .frame(height: DesignSystem.ComponentSize.headerHeight)
                .background(Color.backgroundDefault)

                // Scrollable content
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Appearance Section
                        Text(localizationManager.localize("appearance", comment: "Appearance"))
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, DesignSystem.Spacing.medium)

                        VStack(spacing: DesignSystem.Spacing.small) {
                            ThemeOptionView(
                                label: localizationManager.localize("system_default", comment: "System default"),
                                isSelected: selectedTheme == "system"
                            ) {
                                selectedTheme = "system"
                            }

                            ThemeOptionView(
                                label: localizationManager.localize("theme_light", comment: "Light"),
                                isSelected: selectedTheme == "light"
                            ) {
                                selectedTheme = "light"
                            }

                            ThemeOptionView(
                                label: localizationManager.localize("theme_dark", comment: "Dark"),
                                isSelected: selectedTheme == "dark"
                            ) {
                                selectedTheme = "dark"
                            }
                        }
                        .padding(.bottom, DesignSystem.Spacing.xLarge)

                        // Divider
                        SectionDivider()

                        // Language Section
                        Text(localizationManager.localize("language_language", comment: "Language"))
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, DesignSystem.Spacing.medium)

                        VStack(spacing: DesignSystem.Spacing.small) {
                            LanguageOptionView(
                                label: localizationManager.localize("system_default", comment: "System default"),
                                isSelected: localizationManager.currentLanguage == "system"
                            ) {
                                localizationManager.setLanguage("system")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_norwegian_bokmaal", comment: "Norsk (Bokmål)"),
                                isSelected: localizationManager.currentLanguage == "nb"
                            ) {
                                localizationManager.setLanguage("nb")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_norwegian_nynorsk", comment: "Norwegian (Nynorsk)"),
                                isSelected: localizationManager.currentLanguage == "nn"
                            ) {
                                localizationManager.setLanguage("nn")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_english", comment: "English"),
                                isSelected: localizationManager.currentLanguage == "en"
                            ) {
                                localizationManager.setLanguage("en")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_dutch", comment: "Dutch"),
                                isSelected: localizationManager.currentLanguage == "nl"
                            ) {
                                localizationManager.setLanguage("nl")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_spanish", comment: "Spanish"),
                                isSelected: localizationManager.currentLanguage == "es"
                            ) {
                                localizationManager.setLanguage("es")
                            }

                            LanguageOptionView(
                                label: localizationManager.localize("language_swedish", comment: "Swedish"),
                                isSelected: localizationManager.currentLanguage == "sv"
                            ) {
                                localizationManager.setLanguage("sv")
                            }
                        }
                        .padding(.bottom, DesignSystem.Spacing.xLarge)

                        // Divider
                        SectionDivider()

                        // Location Section
                        Text(localizationManager.localize("location_settings_title", comment: "Location"))
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, DesignSystem.Spacing.medium)

                        SettingsToggleView(
                            title: localizationManager.localize("use_location_for_id", comment: "Use location for identification"),
                            description: localizationManager.localize("use_location_for_id_desc", comment: "Sharing the approximate location of pictures can improve results by selecting the best model. If switched off, the location will not be shared."),
                            isOn: $useLocation
                        )
                        .padding(.bottom, DesignSystem.Spacing.xLarge)

                        // Divider
                        SectionDivider()

                        // History Section
                        Text(localizationManager.localize("identification_history", comment: "History"))
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, DesignSystem.Spacing.medium)

                        SettingsToggleView(
                            title: localizationManager.localize("save_history", comment: "Save history"),
                            description: localizationManager.localize("save_history_desc", comment: "Keep identification results in device history"),
                            isOn: $saveHistory
                        )

                        Button(action: {
                            showClearHistoryConfirmation = true
                        }) {
                            HStack(spacing: 8) {
                                SVGWebView(svgName: "ic_delete", width: 18, height: 18, tintColor: Color.alertDangerBorderPrimary)
                                    .frame(width: 18, height: 18)

                                Text(localizationManager.localize("clear_history", comment: "Clear history"))
                                    .font(DesignSystem.Typography.body())
                                    .foregroundColor(Color.alertDangerTextPrimary)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 10)
                            .background(Color.surfacePrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color.alertDangerBorderPrimary, lineWidth: 2)
                            )
                            .cornerRadius(20)
                        }
                        .padding(.top, DesignSystem.Spacing.medium)
                        .padding(.bottom, DesignSystem.Spacing.xLarge)

                        // Divider
                        SectionDivider()

                        // Permissions Section
                        Text(localizationManager.localize("permissions_title", comment: "Permissions"))
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, DesignSystem.Spacing.medium)

                        PermissionRowView(
                            title: localizationManager.localize("permission_camera", comment: "Camera"),
                            status: permissionStatusText(cameraPermission),
                            buttonText: localizationManager.localize("permission_manage", comment: "Manage"),
                            action: openAppSettings
                        )

                        PermissionRowView(
                            title: localizationManager.localize("permission_location", comment: "Location"),
                            status: permissionStatusText(locationPermission),
                            buttonText: localizationManager.localize("permission_manage", comment: "Manage"),
                            action: openAppSettings
                        )
                    }
                    .padding(DesignSystem.Spacing.large)
                    .padding(.bottom, DesignSystem.Spacing.huge)
                }
                .background(Color.backgroundSubtle)
            }
        }
        .onAppear {
            checkPermissions()
        }
        .alert(localizationManager.localize("clear_history", comment: "Clear history"), isPresented: $showClearHistoryConfirmation) {
            Button(localizationManager.localize("cancel", comment: "Cancel"), role: .cancel) { }
            Button(localizationManager.localize("clear", comment: "Clear"), role: .destructive) {
                historyStorage.deleteAllHistory()
            }
        } message: {
            Text(localizationManager.localize("clear_history_confirmation_message", comment: "Are you sure you want to clear your history?"))
        }
    }

    private func checkPermissions() {
        // Check camera permission
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            cameraPermission = .granted
        case .denied, .restricted:
            cameraPermission = .denied
        case .notDetermined:
            cameraPermission = .notDetermined
        @unknown default:
            cameraPermission = .notDetermined
        }

        // Check location permission
        let locationStatus = CLLocationManager().authorizationStatus
        switch locationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            locationPermission = .granted
        case .denied, .restricted:
            locationPermission = .denied
        case .notDetermined:
            locationPermission = .notDetermined
        @unknown default:
            locationPermission = .notDetermined
        }
    }

    private func permissionStatusText(_ status: PermissionStatus) -> String {
        switch status {
        case .granted:
            return localizationManager.localize("permission_granted", comment: "Granted")
        case .denied, .notDetermined:
            return localizationManager.localize("permission_not_granted", comment: "Not granted")
        }
    }

    private func openAppSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(url)
        }
    }
}

enum PermissionStatus {
    case granted
    case denied
    case notDetermined
}

struct PermissionRowView: View {
    let title: String
    let status: String
    let buttonText: String
    let action: () -> Void

    var body: some View {
        HStack(alignment: .center, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary)

                Text(status)
                    .font(DesignSystem.Typography.caption())
                    .foregroundColor(Color.textSecondary)
            }

            Spacer()

            Button(action: action) {
                Text(buttonText)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textAccent)
            }
        }
        .padding(.vertical, DesignSystem.Spacing.small)
    }
}

struct ThemeOptionView: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(Color.textAccent, lineWidth: 2)
                        .frame(width: DesignSystem.ComponentSize.radioButtonSize, height: DesignSystem.ComponentSize.radioButtonSize)

                    if isSelected {
                        Circle()
                            .fill(Color.textAccent)
                            .frame(width: DesignSystem.ComponentSize.radioButtonDot, height: DesignSystem.ComponentSize.radioButtonDot)
                    }
                }

                Text(label)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textAccent)

                Spacer()
            }
            .padding(.vertical, DesignSystem.Spacing.small)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct LanguageOptionView: View {
    let label: String
    let isSelected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                ZStack {
                    Circle()
                        .stroke(Color.textAccent, lineWidth: 2)
                        .frame(width: DesignSystem.ComponentSize.radioButtonSize, height: DesignSystem.ComponentSize.radioButtonSize)

                    if isSelected {
                        Circle()
                            .fill(Color.textAccent)
                            .frame(width: DesignSystem.ComponentSize.radioButtonDot, height: DesignSystem.ComponentSize.radioButtonDot)
                    }
                }

                Text(label)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textAccent)

                Spacer()
            }
            .padding(.vertical, DesignSystem.Spacing.small)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct SettingsToggleView: View {
    let title: String
    let description: String
    @Binding var isOn: Bool
    var isDisabled: Bool = false

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .toggleStyle(MaterialToggleStyle())
                .disabled(isDisabled)
                .opacity(isDisabled ? DesignSystem.Opacity.disabled : 1.0)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary)

                Text(description)
                    .font(DesignSystem.Typography.caption())
                    .foregroundColor(Color.textPrimary.opacity(DesignSystem.Opacity.subtle))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct MaterialToggleStyle: ToggleStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(configuration.isOn ?
                      Color.surfaceAccent.opacity(0.38) :
                      Color.textPrimary.opacity(0.12))
                .frame(width: 52, height: 32)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.surfaceAccent, lineWidth: configuration.isOn ? 0 : 2)
                )
                .overlay(
                    ZStack {
                        Circle()
                            .fill(Color.surfaceAccent)
                            .frame(width: configuration.isOn ? 24 : 16, height: configuration.isOn ? 24 : 16)
                            .shadow(color: Color.black.opacity(0.2), radius: 2, x: 0, y: 1)

                        // Checkmark when on
                        if configuration.isOn {
                            Image(systemName: "checkmark")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color.surfacePrimary)
                        }
                    }
                    .offset(x: configuration.isOn ? 10 : -12)
                )
                .onTapGesture {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        configuration.isOn.toggle()
                    }
                }
        }
    }
}

struct SectionDivider: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.surfaceBrand1a)
            .frame(width: DesignSystem.ComponentSize.dividerWidth, height: DesignSystem.ComponentSize.dividerThickness)
            .padding(.bottom, DesignSystem.Spacing.xxLarge)
    }
}

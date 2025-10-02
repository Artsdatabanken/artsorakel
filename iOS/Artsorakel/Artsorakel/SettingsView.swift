import SwiftUI

struct SettingsView: View {
    @Binding var isPresented: Bool
    @Binding var showMenuDrawer: Bool
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"
    @AppStorage("saveHistory") private var saveHistory: Bool = true
    @AppStorage("useLocation") private var useLocation: Bool = true

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
                        if resourceExists("ic_arrow_back") {
                            SVGWebView(svgName: "ic_arrow_back", width: 20, height: 20, tintColor: .textAccent)
                                .frame(width: 20, height: 20)
                        } else {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 20))
                                .foregroundColor(Color.textAccent)
                        }
                    }
                    .frame(width: 48, height: 48)
                    .padding(.leading, 8)

                    // Title
                    Text(localizationManager.localize("settings", comment: "Settings"))
                        .font(.custom("Chivo", size: 18))
                        .foregroundColor(Color.textPrimary)
                        .frame(maxWidth: .infinity)

                    // Menu button
                    Button(action: {
                        withAnimation {
                            showMenuDrawer = true
                        }
                    }) {
                        if resourceExists("ic_menu") {
                            SVGWebView(svgName: "ic_menu", width: 24, height: 24, tintColor: .textAccent)
                                .frame(width: 24, height: 24)
                        } else {
                            Image(systemName: "line.3.horizontal")
                                .font(.system(size: 24))
                                .foregroundColor(Color.textAccent)
                        }
                    }
                    .frame(width: 48, height: 48)
                    .padding(.trailing, 8)
                }
                .frame(height: 64)
                .background(Color.backgroundDefault)

                // Scrollable content
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        // Appearance Section
                        Text(localizationManager.localize("appearance", comment: "Appearance"))
                            .font(.custom("Chivo-Bold", size: 16))
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, 12)

                        VStack(spacing: 8) {
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
                        .padding(.bottom, 24)

                        // Divider
                        SectionDivider()

                        // Language Section
                        Text(localizationManager.localize("language_language", comment: "Language"))
                            .font(.custom("Chivo-Bold", size: 16))
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, 12)

                        VStack(spacing: 8) {
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
                        .padding(.bottom, 24)

                        // Divider
                        SectionDivider()

                        // Location Section
                        Text(localizationManager.localize("location_settings_title", comment: "Location"))
                            .font(.custom("Chivo-Bold", size: 16))
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, 12)

                        SettingsToggleView(
                            title: localizationManager.localize("use_location_for_id", comment: "Use location for identification"),
                            description: localizationManager.localize("use_location_for_id_desc", comment: "Sharing the approximate location of pictures can improve results by selecting the best model. If switched off, the location will not be shared."),
                            isOn: $useLocation,
                            isDisabled: true
                        )
                        .padding(.bottom, 24)

                        // Divider
                        SectionDivider()

                        // History Section
                        Text(localizationManager.localize("identification_history", comment: "History"))
                            .font(.custom("Chivo-Bold", size: 16))
                            .foregroundColor(Color.textPrimary)
                            .padding(.bottom, 12)

                        SettingsToggleView(
                            title: localizationManager.localize("save_history", comment: "Save history"),
                            description: localizationManager.localize("save_history_desc", comment: "Keep identification results in device history"),
                            isOn: $saveHistory,
                            isDisabled: true
                        )

                        Button(action: {
                            // Disabled for now
                        }) {
                            HStack(spacing: 8) {
                                if resourceExists("ic_delete") {
                                    SVGWebView(svgName: "ic_delete", width: 20, height: 20, tintColor: Color(red: 0.8, green: 0.2, blue: 0.2))
                                        .frame(width: 20, height: 20)
                                }

                                Text(localizationManager.localize("clear_history", comment: "Clear history"))
                                    .font(.custom("Chivo", size: 14))
                                    .foregroundColor(Color(red: 0.8, green: 0.2, blue: 0.2))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.surfacePrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color(red: 0.8, green: 0.2, blue: 0.2), lineWidth: 2)
                            )
                            .cornerRadius(24)
                        }
                        .disabled(true)
                        .opacity(0.5)
                        .padding(.top, 12)
                    }
                    .padding(20)
                    .padding(.bottom, 80)
                }
                .background(Color.backgroundSubtle)
            }
        }
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
                        .frame(width: 20, height: 20)

                    if isSelected {
                        Circle()
                            .fill(Color.textAccent)
                            .frame(width: 10, height: 10)
                    }
                }

                Text(label)
                    .font(.custom("Chivo", size: 14))
                    .foregroundColor(Color.textAccent)

                Spacer()
            }
            .padding(.vertical, 8)
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
                        .frame(width: 20, height: 20)

                    if isSelected {
                        Circle()
                            .fill(Color.textAccent)
                            .frame(width: 10, height: 10)
                    }
                }

                Text(label)
                    .font(.custom("Chivo", size: 14))
                    .foregroundColor(Color.textAccent)

                Spacer()
            }
            .padding(.vertical, 8)
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
                .disabled(isDisabled)
                .opacity(isDisabled ? 0.5 : 1.0)

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.custom("Chivo", size: 14))
                    .foregroundColor(Color.textPrimary)

                Text(description)
                    .font(.custom("Chivo", size: 12))
                    .foregroundColor(Color.textPrimary.opacity(0.7))
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}

struct SectionDivider: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.surfaceBrand1a)
            .frame(width: 60, height: 4)
            .padding(.bottom, 32)
    }
}

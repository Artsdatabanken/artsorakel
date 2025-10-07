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
                            SVGWebView(svgName: "ic_arrow_back", width: DesignSystem.IconSize.medium, height: DesignSystem.IconSize.medium, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.medium, height: DesignSystem.IconSize.medium)
                       
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.leading, DesignSystem.Spacing.small)

                    // Title
                    Text(localizationManager.localize("settings", comment: "Settings"))
                        .font(DesignSystem.Typography.title())
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
                            isOn: $useLocation,
                            isDisabled: true
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
                            isOn: $saveHistory,
                            isDisabled: true
                        )

                        Button(action: {
                            // Disabled for now
                        }) {
                            HStack(spacing: 8) {
                                    SVGWebView(svgName: "ic_delete", width: 20, height: 20, tintColor: Color(red: 0.8, green: 0.2, blue: 0.2))
                                        .frame(width: DesignSystem.ComponentSize.radioButtonSize, height: DesignSystem.ComponentSize.radioButtonSize)

                                Text(localizationManager.localize("clear_history", comment: "Clear history"))
                                    .font(DesignSystem.Typography.body())
                                    .foregroundColor(Color(red: 0.8, green: 0.2, blue: 0.2))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: DesignSystem.ButtonSize.standard)
                            .background(Color.surfacePrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color(red: 0.8, green: 0.2, blue: 0.2), lineWidth: 2)
                            )
                            .cornerRadius(DesignSystem.CornerRadius.large)
                        }
                        .disabled(true)
                        .opacity(DesignSystem.Opacity.disabled)
                        .padding(.top, DesignSystem.Spacing.medium)
                    }
                    .padding(DesignSystem.Spacing.large)
                    .padding(.bottom, DesignSystem.Spacing.huge)
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

struct SectionDivider: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.surfaceBrand1a)
            .frame(width: DesignSystem.ComponentSize.dividerWidth, height: DesignSystem.ComponentSize.dividerThickness)
            .padding(.bottom, DesignSystem.Spacing.xxLarge)
    }
}

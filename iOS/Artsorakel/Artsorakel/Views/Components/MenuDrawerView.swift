import SwiftUI

struct MenuDrawerView: View {
    @Binding var isOpen: Bool
    @Binding var showSettings: Bool
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"

    @State private var showThemeDialog = false
    @State private var showLanguageDialog = false

    func applyTheme(_ theme: String) {
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            if isOpen {
                Color.black.opacity(0.3)
                    .ignoresSafeArea()
                    .onTapGesture {
                        withAnimation {
                            isOpen = false
                        }
                    }

                VStack(spacing: 0) {
                    Spacer()

                    VStack(spacing: 0) {
                        MenuItemView(
                            iconName: "ic_theme",
                            title: localizationManager.localize("theme_theme", comment: "Theme")
                        ) {
                            showThemeDialog = true
                        }

                        MenuItemView(
                            iconName: "ic_language",
                            title: localizationManager.localize("language_language", comment: "Language")
                        ) {
                            showLanguageDialog = true
                        }

                        DividerView()

                        MenuItemView(
                            iconName: "ic_settings",
                            title: localizationManager.localize("settings", comment: "Settings")
                        ) {
                            withAnimation {
                                isOpen = false
                                showSettings = true
                            }
                        }

                        MenuItemView(
                            iconName: "ic_alert_info",
                            title: localizationManager.localize("about", comment: "About")
                        ) {
                        }

                        MenuItemView(
                            iconName: "ic_help",
                            title: localizationManager.localize("faq", comment: "FAQ")
                        ) {
                        }

                        Spacer()
                            .frame(height: 16)

                        HStack {
                            Spacer()

                            Button(action: {
                                withAnimation {
                                    isOpen = false
                                }
                            }) {
                                ZStack {
                                    Circle()
                                        .fill(Color.surfacePrimary)
                                        .frame(width: 40, height: 40)
                                        .overlay(
                                            Circle()
                                                .stroke(Color.textAccent, lineWidth: 1.5)
                                        )
                                        .shadow(color: Color.black.opacity(0.3), radius: 8, x: 0, y: 4)

                                    Image(systemName: "xmark")
                                        .font(.system(size: 16))
                                        .foregroundColor(Color.textAccent)
                                }
                            }
                            .padding(8)
                        }
                    }
                    .padding(.bottom, 16)
                }
                .frame(width: 280)
                .background(Color.backgroundSubtle)
                .transition(.move(edge: .trailing))
            }
        }
        .alert(localizationManager.localize("theme_theme", comment: "Theme"), isPresented: $showThemeDialog) {
            Button(localizationManager.localize("system_default", comment: "System default")) {
                selectedTheme = "system"
                applyTheme("system")
            }
            Button(localizationManager.localize("theme_light", comment: "Light")) {
                selectedTheme = "light"
                applyTheme("light")
            }
            Button(localizationManager.localize("theme_dark", comment: "Dark")) {
                selectedTheme = "dark"
                applyTheme("dark")
            }
            Button(localizationManager.localize("cancel", comment: "Cancel"), role: .cancel) { }
        }
        .alert(localizationManager.localize("language_language", comment: "Language"), isPresented: $showLanguageDialog) {
            Button(localizationManager.localize("system_default", comment: "System default")) {
                localizationManager.setLanguage("system")
            }
            Button(localizationManager.localize("language_norwegian_bokmaal", comment: "Norwegian Bokmål")) {
                localizationManager.setLanguage("nb")
            }
            Button(localizationManager.localize("language_norwegian_nynorsk", comment: "Norwegian Nynorsk")) {
                localizationManager.setLanguage("nn")
            }
            Button(localizationManager.localize("language_english", comment: "English")) {
                localizationManager.setLanguage("en")
            }
            Button(localizationManager.localize("language_spanish", comment: "Spanish")) {
                localizationManager.setLanguage("es")
            }
            Button(localizationManager.localize("language_dutch", comment: "Dutch")) {
                localizationManager.setLanguage("nl")
            }
            Button(localizationManager.localize("language_swedish", comment: "Swedish")) {
                localizationManager.setLanguage("sv")
            }
            Button(localizationManager.localize("cancel", comment: "Cancel"), role: .cancel) { }
        }
    }
}

struct MenuItemView: View {
    let iconName: String
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                if resourceExists(iconName) {
                    SVGWebView(svgName: iconName, width: 24, height: 24, tintColor: .textAccent)
                        .frame(width: 24, height: 24)
                } else {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 24))
                        .foregroundColor(Color.textAccent)
                        .frame(width: 24, height: 24)
                }

                Text(title)
                    .font(.custom("Chivo", size: 14))
                    .foregroundColor(Color.textPrimary)

                Spacer()
            }
            .padding(.horizontal, 16)
            .frame(height: 48)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct DividerView: View {
    var body: some View {
        RoundedRectangle(cornerRadius: 2)
            .fill(Color.surfaceBrand1a)
            .frame(width: 60, height: 4)
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}
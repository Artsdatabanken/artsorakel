import SwiftUI

struct MenuDrawerView: View {
    @Binding var isOpen: Bool
    @Environment(\.colorScheme) var colorScheme
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"
    @AppStorage("selectedLanguage") private var selectedLanguage: String = "system"

    @State private var showThemeDialog = false
    @State private var showLanguageDialog = false

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
                            title: NSLocalizedString("theme_theme", comment: "Theme")
                        ) {
                            showThemeDialog = true
                        }

                        MenuItemView(
                            iconName: "ic_language",
                            title: NSLocalizedString("language_language", comment: "Language")
                        ) {
                            showLanguageDialog = true
                        }

                        DividerView()

                        MenuItemView(
                            iconName: "ic_settings",
                            title: NSLocalizedString("settings", comment: "Settings")
                        ) {
                        }

                        MenuItemView(
                            iconName: "ic_alert_info",
                            title: NSLocalizedString("about", comment: "About")
                        ) {
                        }

                        MenuItemView(
                            iconName: "ic_help",
                            title: NSLocalizedString("faq", comment: "FAQ")
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
        .sheet(isPresented: $showThemeDialog) {
            ThemeSelectionView(selectedTheme: $selectedTheme, isPresented: $showThemeDialog)
                .presentationDetents([.height(280)])
        }
        .sheet(isPresented: $showLanguageDialog) {
            LanguageSelectionView(selectedLanguage: $selectedLanguage, isPresented: $showLanguageDialog)
                .presentationDetents([.height(450)])
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

struct ThemeSelectionView: View {
    @Binding var selectedTheme: String
    @Binding var isPresented: Bool

    let themes = [
        ("system", NSLocalizedString("system_default", comment: "System default")),
        ("light", NSLocalizedString("theme_light", comment: "Light")),
        ("dark", NSLocalizedString("theme_dark", comment: "Dark"))
    ]

    var body: some View {
        VStack(spacing: 0) {
            Text(NSLocalizedString("theme_theme", comment: "Theme"))
                .font(.custom("Chivo", size: 18).weight(.bold))
                .foregroundColor(Color.textPrimary)
                .padding(.top, 20)
                .padding(.bottom, 16)

            ForEach(themes, id: \.0) { theme in
                Button(action: {
                    selectedTheme = theme.0
                    applyTheme(theme.0)
                    isPresented = false
                }) {
                    HStack {
                        Text(theme.1)
                            .font(.custom("Chivo", size: 16))
                            .foregroundColor(Color.textPrimary)

                        Spacer()

                        if selectedTheme == theme.0 {
                            Image(systemName: "checkmark")
                                .foregroundColor(Color.textAccent)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .contentShape(Rectangle())
                }
                .buttonStyle(PlainButtonStyle())
            }

            Spacer()
        }
        .background(Color.backgroundSubtle)
    }

    func applyTheme(_ theme: String) {
    }
}

struct LanguageSelectionView: View {
    @Binding var selectedLanguage: String
    @Binding var isPresented: Bool

    let languages = [
        ("system", NSLocalizedString("system_default", comment: "System default")),
        ("nb", NSLocalizedString("language_norwegian_bokmaal", comment: "Norwegian Bokmål")),
        ("nn", NSLocalizedString("language_norwegian_nynorsk", comment: "Norwegian Nynorsk")),
        ("en", NSLocalizedString("language_english", comment: "English")),
        ("es", NSLocalizedString("language_spanish", comment: "Spanish")),
        ("nl", NSLocalizedString("language_dutch", comment: "Dutch")),
        ("sv", NSLocalizedString("language_swedish", comment: "Swedish"))
    ]

    var body: some View {
        VStack(spacing: 0) {
            Text(NSLocalizedString("language_language", comment: "Language"))
                .font(.custom("Chivo", size: 18).weight(.bold))
                .foregroundColor(Color.textPrimary)
                .padding(.top, 20)
                .padding(.bottom, 16)

            ScrollView {
                ForEach(languages, id: \.0) { language in
                    Button(action: {
                        selectedLanguage = language.0
                        isPresented = false
                    }) {
                        HStack {
                            Text(language.1)
                                .font(.custom("Chivo", size: 16))
                                .foregroundColor(Color.textPrimary)

                            Spacer()

                            if selectedLanguage == language.0 {
                                Image(systemName: "checkmark")
                                    .foregroundColor(Color.textAccent)
                            }
                        }
                        .padding(.horizontal, 24)
                        .padding(.vertical, 12)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }

            Spacer()
        }
        .background(Color.backgroundSubtle)
    }
}
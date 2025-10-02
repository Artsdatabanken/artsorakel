//
//  ArtsorakelApp.swift
//  Artsorakel
//
//  Created by Wouter Koch on 04/09/2025.
//

import SwiftUI

@main
struct ArtsorakelApp: App {
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"
    @StateObject private var localizationManager = LocalizationManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(colorScheme(for: selectedTheme))
                .environmentObject(localizationManager)
        }
    }

    private func colorScheme(for theme: String) -> ColorScheme? {
        switch theme {
        case "light":
            return .light
        case "dark":
            return .dark
        default:
            return nil
        }
    }
}

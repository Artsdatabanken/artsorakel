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

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(colorScheme(for: selectedTheme))
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

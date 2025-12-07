//
//  ArtsorakelApp.swift
//  Artsorakel
//
//  Created by Wouter Koch on 04/09/2025.
//

import SwiftUI

/// Notification name for when a shared image is received
extension Notification.Name {
    static let sharedImageReceived = Notification.Name("sharedImageReceived")
}

@main
struct ArtsorakelApp: App {
    @AppStorage("selectedTheme") private var selectedTheme: String = "system"
    @StateObject private var localizationManager = LocalizationManager()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(colorScheme(for: selectedTheme))
                .environmentObject(localizationManager)
                .onOpenURL { url in
                    handleIncomingURL(url)
                }
                .onAppear {
                    // Check for pending shared images when app launches
                    checkForPendingSharedImage()
                }
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

    private func handleIncomingURL(_ url: URL) {
        // Handle artsorakel://shared-image URL from Share Extension
        if url.scheme == "artsorakel" && url.host == "shared-image" {
            checkForPendingSharedImage()
        }
    }

    private func checkForPendingSharedImage() {
        if SharedImageHandler.shared.hasPendingSharedImage() {
            // Post notification that a shared image is available
            NotificationCenter.default.post(name: .sharedImageReceived, object: nil)
        }
    }
}

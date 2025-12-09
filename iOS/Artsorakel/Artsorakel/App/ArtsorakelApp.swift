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
    @StateObject private var appConfig = AppConfig.shared

    var body: some Scene {
        WindowGroup {
            if let error = appConfig.configurationError {
                ConfigurationErrorView(error: error)
                    .preferredColorScheme(colorScheme(for: selectedTheme))
            } else {
                ContentView()
                    .preferredColorScheme(colorScheme(for: selectedTheme))
                    .environmentObject(localizationManager)
                    .onOpenURL { url in
                        handleIncomingURL(url)
                    }
                    .onAppear {
                        // Check for pending shared images when app launches
                        checkForPendingSharedImage()
                        // Reset RSS session on app start
                        RssFeedService.shared.resetSessionOnAppStart()
                    }
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

/// Error view displayed when app configuration fails to load
struct ConfigurationErrorView: View {
    let error: AppConfigError

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 64))
                .foregroundColor(.orange)

            Text("Configuration Error")
                .font(.title)
                .fontWeight(.bold)

            Text(error.localizedDescription)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()

            Text("Error code: \(String(describing: error).components(separatedBy: ".").last ?? "unknown")")
                .font(.caption)
                .foregroundColor(.secondary)
                .padding(.bottom, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

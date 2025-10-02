import Foundation
import SwiftUI

class LocalizationManager: ObservableObject {
    @Published var currentLanguage: String {
        didSet {
            UserDefaults.standard.set(currentLanguage, forKey: "selectedLanguage")
        }
    }

    private var bundle: Bundle?

    init() {
        let savedLanguage = UserDefaults.standard.string(forKey: "selectedLanguage") ?? "system"
        self.currentLanguage = savedLanguage
        updateBundle()
    }

    func setLanguage(_ language: String) {
        currentLanguage = language
        updateBundle()
    }

    private func updateBundle() {
        if currentLanguage == "system" {
            bundle = nil
            return
        }

        if let path = Bundle.main.path(forResource: currentLanguage, ofType: "lproj"),
           let langBundle = Bundle(path: path) {
            bundle = langBundle
        } else {
            bundle = nil
        }
    }

    func localize(_ key: String, comment: String = "") -> String {
        if let bundle = bundle {
            return NSLocalizedString(key, bundle: bundle, comment: comment)
        }
        return NSLocalizedString(key, comment: comment)
    }
}

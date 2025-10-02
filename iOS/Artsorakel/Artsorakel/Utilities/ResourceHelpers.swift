import Foundation

// Helper function to check if resource exists
func resourceExists(_ name: String) -> Bool {
    return Bundle.main.url(forResource: name, withExtension: "svg") != nil ||
           Bundle.main.url(forResource: "Resources/Images/\(name)", withExtension: "svg") != nil ||
           Bundle.main.url(forResource: "Resources/Vectors/\(name)", withExtension: "svg") != nil
}

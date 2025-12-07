import Foundation

struct AppConfig {
    struct API {
        static let baseURL: String = {
            // Try multiple paths to find the config file
            let possiblePaths = [
                Bundle.main.path(forResource: "app_config", ofType: "json", inDirectory: "Config"),
                Bundle.main.path(forResource: "app_config", ofType: "json"),
                Bundle.main.url(forResource: "app_config", withExtension: "json", subdirectory: "Config")?.path,
                Bundle.main.url(forResource: "app_config", withExtension: "json")?.path
            ]

            guard let path = possiblePaths.compactMap({ $0 }).first,
                  let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let api = json["api"] as? [String: Any],
                  let baseUrl = api["baseUrl"] as? String else {
                fatalError("Failed to load baseUrl from app_config.json. Make sure to run sync_resources.py first.")
            }
            return baseUrl
        }()

        static let bearerToken: String = {
            // Try multiple paths to find the secrets file
            let possiblePaths = [
                Bundle.main.path(forResource: "secrets", ofType: "json", inDirectory: "Config"),
                Bundle.main.path(forResource: "secrets", ofType: "json"),
                Bundle.main.url(forResource: "secrets", withExtension: "json", subdirectory: "Config")?.path,
                Bundle.main.url(forResource: "secrets", withExtension: "json")?.path
            ]

            guard let path = possiblePaths.compactMap({ $0 }).first,
                  let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let api = json["api"] as? [String: Any] else {
                fatalError("Failed to load bearerToken from secrets.json. Make sure to run sync_resources.py first and that secrets.json exists.")
            }

            // Use test token if baseURL contains "test", otherwise use production token
            let tokenKey = baseURL.contains(".test.") ? "bearerTokenTest" : "bearerToken"
            guard let bearerTokens = api[tokenKey] as? [String: String],
                  let token = bearerTokens["ios"] else {
                fatalError("Failed to load \(tokenKey) from secrets.json.")
            }
            return token
        }()

        static let timeout: TimeInterval = {
            let possiblePaths = [
                Bundle.main.path(forResource: "app_config", ofType: "json", inDirectory: "Config"),
                Bundle.main.path(forResource: "app_config", ofType: "json"),
                Bundle.main.url(forResource: "app_config", withExtension: "json", subdirectory: "Config")?.path,
                Bundle.main.url(forResource: "app_config", withExtension: "json")?.path
            ]

            guard let path = possiblePaths.compactMap({ $0 }).first,
                  let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let api = json["api"] as? [String: Any],
                  let timeout = api["timeout"] as? Int else {
                return 60 // Default timeout
            }
            return TimeInterval(timeout)
        }()
    }
}

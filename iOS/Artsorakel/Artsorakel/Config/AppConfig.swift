import Foundation

/// Errors that can occur during app configuration loading
enum AppConfigError: LocalizedError {
    case configFileNotFound
    case configFileInvalid
    case missingBaseURL
    case secretsFileNotFound
    case secretsFileInvalid
    case missingBearerToken

    var errorDescription: String? {
        switch self {
        case .configFileNotFound:
            return "Configuration file not found. Please reinstall the app."
        case .configFileInvalid:
            return "Configuration file is invalid. Please reinstall the app."
        case .missingBaseURL:
            return "API configuration is incomplete. Please reinstall the app."
        case .secretsFileNotFound:
            return "Authentication configuration not found. Please reinstall the app."
        case .secretsFileInvalid:
            return "Authentication configuration is invalid. Please reinstall the app."
        case .missingBearerToken:
            return "Authentication token not found. Please reinstall the app."
        }
    }
}

/// App configuration manager with graceful error handling
class AppConfig: ObservableObject {
    static let shared = AppConfig()

    /// Configuration error if loading failed
    @Published private(set) var configurationError: AppConfigError?

    /// Whether the configuration loaded successfully
    var isConfigured: Bool { configurationError == nil }

    // API Configuration
    private(set) var baseURL: String = ""
    private(set) var bearerToken: String = ""
    private(set) var timeout: TimeInterval = 60

    // RSS Feed Configuration
    private(set) var rssFeedURL: String?

    private init() {
        loadConfiguration()
    }

    private func loadConfiguration() {
        // Load app config
        do {
            let (url, timeoutValue, rssFeed) = try loadAppConfig()
            self.baseURL = url
            self.timeout = timeoutValue
            self.rssFeedURL = rssFeed

            // Load secrets (depends on baseURL for token selection)
            self.bearerToken = try loadSecrets(baseURL: url)
        } catch let error as AppConfigError {
            self.configurationError = error
        } catch {
            self.configurationError = .configFileInvalid
        }
    }

    private func loadAppConfig() throws -> (baseURL: String, timeout: TimeInterval, rssFeedURL: String?) {
        let possiblePaths = [
            Bundle.main.path(forResource: "app_config", ofType: "json", inDirectory: "Config"),
            Bundle.main.path(forResource: "app_config", ofType: "json"),
            Bundle.main.url(forResource: "app_config", withExtension: "json", subdirectory: "Config")?.path,
            Bundle.main.url(forResource: "app_config", withExtension: "json")?.path
        ]

        guard let path = possiblePaths.compactMap({ $0 }).first else {
            throw AppConfigError.configFileNotFound
        }

        guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let api = json["api"] as? [String: Any] else {
            throw AppConfigError.configFileInvalid
        }

        // Select endpoint based on build configuration
        #if DEBUG
        let urlKey = "baseUrlDebug"
        let rssFeedKey = "urlDebug"
        #else
        let urlKey = "baseUrlRelease"
        let rssFeedKey = "urlRelease"
        #endif

        guard let baseUrl = api[urlKey] as? String else {
            throw AppConfigError.missingBaseURL
        }

        let timeout = (api["timeout"] as? Int).map { TimeInterval($0) } ?? 60

        // Load RSS feed URL (optional)
        let rssFeed = json["rssFeed"] as? [String: Any]
        let rssFeedURL = rssFeed?[rssFeedKey] as? String

        return (baseUrl, timeout, rssFeedURL)
    }

    private func loadSecrets(baseURL: String) throws -> String {
        let possiblePaths = [
            Bundle.main.path(forResource: "secrets", ofType: "json", inDirectory: "Config"),
            Bundle.main.path(forResource: "secrets", ofType: "json"),
            Bundle.main.url(forResource: "secrets", withExtension: "json", subdirectory: "Config")?.path,
            Bundle.main.url(forResource: "secrets", withExtension: "json")?.path
        ]

        guard let path = possiblePaths.compactMap({ $0 }).first else {
            throw AppConfigError.secretsFileNotFound
        }

        guard let data = try? Data(contentsOf: URL(fileURLWithPath: path)),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let api = json["api"] as? [String: Any] else {
            throw AppConfigError.secretsFileInvalid
        }

        // Use test token if baseURL contains "test", otherwise use production token
        let tokenKey = baseURL.contains(".test.") ? "bearerTokenTest" : "bearerToken"
        guard let bearerTokens = api[tokenKey] as? [String: String],
              let token = bearerTokens["ios"] else {
            throw AppConfigError.missingBearerToken
        }

        return token
    }
}

// MARK: - Legacy Static Access (for backwards compatibility during migration)
extension AppConfig {
    struct API {
        static var baseURL: String { AppConfig.shared.baseURL }
        static var bearerToken: String { AppConfig.shared.bearerToken }
        static var timeout: TimeInterval { AppConfig.shared.timeout }
    }
}

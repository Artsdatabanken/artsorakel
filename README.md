<img src="shared/images/artsorakel_owl_optimized.svg" width="80" height="80" align="left" alt="Artsorakel app icon">

# Artsorakel

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform - Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](Android/)
[![Platform - iOS](https://img.shields.io/badge/Platform-iOS-000000?logo=apple&logoColor=white)](iOS/)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-3.0-4baaaa.svg)](CODE_OF_CONDUCT.md)

Artsorakel is a species identification app that uses artificial intelligence to identify wild species from photos. Developed by NBIC, the [Norwegian Biodiversity Information Centren](https://artsdatabanken.no) (Artsdatabanken).

The app is available for both Android and iOS.

## Features

- Identify species from photos using AI
- Works offline for viewing history
- Supports multiple languages
- No account required
- Free to use

## Prerequisites

Before building, ensure you have the following installed:

- **Python 3** - for the sync script
- **Java** - required by vd-tool for SVG to Android VectorDrawable conversion
- **vd-tool** - `npm install -g vd-tool`
- **librsvg** - `brew install librsvg` (macOS)
- **ImageMagick** - `brew install imagemagick` (macOS)
- **jq** - `brew install jq` (macOS)

For Android development:
- Android Studio
- Android SDK

For iOS development:
- Xcode
- macOS

## Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Artsdatabanken/artsorakel.git
   cd artsorakel
   ```

2. **Configure API credentials**

   Copy the secrets template and add your bearer tokens:
   ```bash
   cp shared/config/secrets.json.template shared/config/secrets.json
   ```

   Edit `shared/config/secrets.json` and replace the placeholder values with your actual bearer tokens:
   ```json
   {
     "api": {
       "bearerToken": {
         "android": "YOUR_ANDROID_BEARER_TOKEN_HERE",
         "ios": "YOUR_IOS_BEARER_TOKEN_HERE"
       }
     }
   }
   ```

3. **Run the sync script**

   The sync script downloads fonts, generates platform-specific resources, and syncs shared assets:
   ```bash
   python3 sync_resources.py
   ```

   This will:
   - Download fonts from Fontsource
   - Generate localized strings for both platforms
   - Convert SVG icons to Android VectorDrawables
   - Generate iOS color assets and app icons
   - Sync design system themes and colors

## Building

### Android

1. Open the `Android` folder in Android Studio
2. Sync Gradle
3. Run on device/emulator

### iOS

1. Open `iOS/Artsorakel/Artsorakel.xcodeproj` in Xcode
2. Select your development team for signing
3. Run on device/simulator

## Project Structure

```
artsorakel/
├── Android/                 # Android app (Kotlin)
├── iOS/                     # iOS app (Swift/SwiftUI)
├── shared/                  # Shared resources
│   ├── config/              # App configuration and secrets
│   ├── content/             # HTML content (about, FAQ, etc.)
│   ├── designsystem/        # Design tokens and themes
│   ├── fonts/               # Downloaded fonts (auto-generated)
│   ├── images/              # Logo and image assets
│   ├── vectors/             # SVG icons
│   └── strings.csv          # Localization strings
├── sync_resources.py        # Resource sync script
├── LICENSE                  # MIT License
├── README.md                # This file
├── CONTRIBUTING.md          # Contribution guidelines
└── CODE_OF_CONDUCT.md       # Community code of conduct
```

## How It Works

The app sends photos to [NBIC's AI middleware](https://github.com/artsdatabanken/ai), which augments and returns species predictions based on a model trained and hosted by [Naturalis Biodiversity Center](https://www.naturalis.nl), based on images contributed by citizen scientists on [Artsobservasjoner.no](https://artsobservasjoner.no) and similar platforms. The model can only identify wild species that have been reported with images - it does not recognize humans, domestic animals, or garden plants. Based on the approximate location of the provided picture (if shared by the user), or the country of their IP address, the Norwegian, Swedish or European modal is selected.

Images and user information are not stored, nor made available to NBIC or third parties.

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting issues or pull requests. All participants are expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Support

Questions and feedback can be sent to [support@artsobservasjoner.no](mailto:support@artsobservasjoner.no).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

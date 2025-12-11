# Artsorakel

Artsorakel is a species identification app that uses artificial intelligence to identify wild species from photos. Developed by [Artsdatabanken](https://artsdatabanken.no) (Norwegian Biodiversity Information Centre) in collaboration with [Naturalis Biodiversity Center](https://www.naturalis.nl/).

The app is available for both Android and iOS.

## Features

- Identify species from photos using AI
- Works offline for viewing history
- Supports multiple languages (Norwegian Bokmål, Nynorsk, English, Dutch, Spanish, Swedish)
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
   - Download Chivo fonts from Fontsource (if missing or older than a week)
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
└── LICENSE                  # MIT License
```

## How It Works

The app sends photos to Artsdatabanken's AI service, which returns species predictions based on a model trained with images from [Artsobservasjoner.no](https://artsobservasjoner.no). The model can only identify wild species that have been reported with images - it does not recognize humans, domestic animals, or garden plants.

Images and user information are not stored or made available to Artsdatabanken or third parties.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.

## Support

Questions and feedback can be sent to [support@artsobservasjoner.no](mailto:support@artsobservasjoner.no).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Artsdatabanken](https://artsdatabanken.no) - Norwegian Biodiversity Information Centre
- [Naturalis Biodiversity Center](https://www.naturalis.nl/) - AI model development
- [Fontsource](https://fontsource.org/) - Chivo font distribution

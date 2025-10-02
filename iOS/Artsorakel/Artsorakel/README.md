# iOS Project Structure

This project follows a clean architecture pattern with clear separation of concerns.

## Directory Structure

```
Artsorakel/
├── App/                          # App entry point
│   └── ArtsorakelApp.swift       # Main app definition (@main)
│
├── Views/
│   ├── Screens/                  # Full-screen views
│   │   ├── MainScreenView.swift  # Main landing screen
│   │   ├── SettingsView.swift    # Settings overlay
│   │   └── ContentView.swift     # Root content view
│   ├── Components/               # Reusable UI components
│   │   ├── SVGWebView.swift      # SVG rendering components
│   │   └── MenuDrawerView.swift  # Slide-out menu
│   └── Extensions/               # SwiftUI extensions
│       └── ColorExtensions.swift # Design system colors
│
├── Managers/                     # Business logic managers
│   └── LocalizationManager.swift # i18n management
│
├── Utilities/                    # Helper functions
│   └── ResourceHelpers.swift     # Resource loading helpers
│
└── Resources/                    # Generated resources (DO NOT EDIT)
    ├── Content/                  # HTML & JSON content (synced)
    ├── Fonts/                    # TTF fonts (synced)
    ├── Images/                   # SVG images (synced)
    ├── Vectors/                  # SVG icons (synced)
    ├── *.lproj/                  # Localization strings (synced)
    └── Assets.xcassets/          # Asset catalog (colors synced)
```

## Generated vs Manual Files

### 🤖 Auto-Generated (by sync_resources.py)
**DO NOT EDIT** - Changes will be overwritten

- `Resources/Content/*.html`, `*.json`
- `Resources/Fonts/*.ttf`
- `Resources/Images/*.svg`
- `Resources/Vectors/*.svg`
- `*.lproj/Localizable.strings`
- `Assets.xcassets/Color_*.colorset/`
- `Assets.xcassets/AppIcon.appiconset/`

### ✏️ Manual Files
Safe to edit

- All `.swift` files
- `Info.plist` (values updated by script, but manual edits preserved)

## Design System Integration

Colors are automatically generated from `shared/design-system/tokens.json`:
- Accessed via: `Color.backgroundDefault`, `Color.textPrimary`, etc.
- Defined in: `Views/Extensions/ColorExtensions.swift`
- Synced to: `Assets.xcassets/Color_*.colorset/`

Strings are synced from `shared/strings.csv`:
- Accessed via: `localizationManager.localize("key", comment: "...")`
- Managed by: `Managers/LocalizationManager.swift`
- Synced to: `*.lproj/Localizable.strings`

## Adding New Features

1. **New Screen**: Add to `Views/Screens/`
2. **Reusable Component**: Add to `Views/Components/`
3. **Business Logic**: Add manager to `Managers/`
4. **Helper Function**: Add to `Utilities/`
5. **SwiftUI Extension**: Add to `Views/Extensions/`

## Running the Sync Script

```bash
python3 sync_resources.py
```

This will:
- Clean up build artifacts
- Sync all resources from `shared/` directory
- Update version info in `Info.plist`
- Generate color assets
- Generate localization strings

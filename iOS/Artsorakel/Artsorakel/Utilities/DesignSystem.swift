import SwiftUI

/// Centralized design system containing all spacing, sizing, typography, and visual constants
/// Used throughout the app to ensure consistency
struct DesignSystem {

    // MARK: - Spacing
    struct Spacing {
        static let xxSmall: CGFloat = 2
        static let xSmall: CGFloat = 4
        static let small: CGFloat = 8
        static let medium: CGFloat = 12
        static let standard: CGFloat = 16
        static let large: CGFloat = 20
        static let xLarge: CGFloat = 24
        static let xxLarge: CGFloat = 32
        static let xxxLarge: CGFloat = 36
        static let huge: CGFloat = 80
        static let extraHuge: CGFloat = 100
    }

    // MARK: - Icon Sizes
    struct IconSize {
        static let small: CGFloat = 16
        static let medium: CGFloat = 20
        static let standard: CGFloat = 24
        static let large: CGFloat = 47
    }

    // MARK: - Button Sizes
    struct ButtonSize {
        static let standard: CGFloat = 48
        static let medium: CGFloat = 56
        static let large: CGFloat = 97
    }

    // MARK: - Component Sizes
    struct ComponentSize {
        static let headerHeight: CGFloat = 64
        static let menuWidth: CGFloat = 280
        static let dividerHeight: CGFloat = 1
        static let dividerWidth: CGFloat = 60
        static let dividerThickness: CGFloat = 4
        static let closeButtonSize: CGFloat = 40
        static let radioButtonSize: CGFloat = 20
        static let radioButtonDot: CGFloat = 10
    }

    // MARK: - Corner Radius
    struct CornerRadius {
        static let small: CGFloat = 2
        static let medium: CGFloat = 8
        static let large: CGFloat = 24
    }

    // MARK: - Typography
    struct Typography {
        // Font names
        static let primaryFont = "Chivo"
        static let primaryFontBold = "Chivo-Bold"

        // Font sizes
        struct FontSize {
            static let caption: CGFloat = 12
            static let body: CGFloat = 14
            static let subheadline: CGFloat = 16
            static let title: CGFloat = 18
            static let titleLarge: CGFloat = 22
        }

        // Font helpers
        static func body() -> Font {
            .custom(primaryFont, size: FontSize.body)
        }

        static func bodyBold() -> Font {
            .custom(primaryFontBold, size: FontSize.body)
        }

        static func subheadline() -> Font {
            .custom(primaryFont, size: FontSize.subheadline)
        }

        static func subheadlineBold() -> Font {
            .custom(primaryFontBold, size: FontSize.subheadline)
        }

        static func title() -> Font {
            .custom(primaryFont, size: FontSize.title).weight(.bold)
        }

        static func titleRegular() -> Font {
            .custom(primaryFont, size: FontSize.title)
        }

        static func titleLarge() -> Font {
            .custom(primaryFont, size: FontSize.titleLarge).weight(.bold)
        }

        static func caption() -> Font {
            .custom(primaryFont, size: FontSize.caption)
        }
    }

    // MARK: - Shadows
    struct Shadow {
        static let small = ShadowStyle(
            color: Color.black.opacity(0.3),
            radius: 8,
            x: 0,
            y: 4
        )

        static let medium = ShadowStyle(
            color: Color.black.opacity(0.3),
            radius: 10,
            x: 0,
            y: 6
        )
    }

    struct ShadowStyle {
        let color: Color
        let radius: CGFloat
        let x: CGFloat
        let y: CGFloat
    }

    // MARK: - Opacity
    struct Opacity {
        static let disabled: Double = 0.5
        static let overlay: Double = 0.3
        static let subtle: Double = 0.7
    }
}

// MARK: - View Extensions for easy usage
extension View {
    func applyShadow(_ shadow: DesignSystem.ShadowStyle) -> some View {
        self.shadow(color: shadow.color, radius: shadow.radius, x: shadow.x, y: shadow.y)
    }
}

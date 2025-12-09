//
//  WarningBoxView.swift
//  Artsorakel
//

import SwiftUI

struct WarningBoxView: View {
    let warning: WarningItem
    let speciesName: String?
    let currentLanguage: String

    private var borderColor: Color {
        switch warning.category {
        case .danger:
            return .alertDangerBorderPrimary
        case .warning:
            return .alertWarningBorderPrimary
        case .info:
            return .alertInfoBorderPrimary
        }
    }

    private var surfaceColor: Color {
        switch warning.category {
        case .danger:
            return .alertDangerSurfaceSubtle
        case .warning:
            return .alertWarningSurfaceSubtle
        case .info:
            return .alertInfoSurfaceSubtle
        }
    }

    private var textColor: Color {
        switch warning.category {
        case .danger:
            return .alertDangerTextPrimary
        case .warning:
            return .alertWarningTextPrimary
        case .info:
            return .alertInfoTextPrimary
        }
    }

    private var iconName: String {
        switch warning.category {
        case .danger:
            return "ic_alert_info"
        case .warning:
            return "ic_alert_warning"
        case .info:
            return "ic_alert_info"
        }
    }

    private var titleText: String? {
        guard let title = warning.getTitle(for: currentLanguage) else { return nil }
        if let name = speciesName {
            return "\(title) (\(name))"
        }
        return title
    }

    private var messageText: String {
        let message = warning.getMessage(for: currentLanguage) ?? ""
        // If no title but species name provided, prepend it to message
        if warning.title == nil, let name = speciesName {
            return "\(name): \(message)"
        }
        return message
    }

    var body: some View {
        let linkURL = warning.getLink(for: currentLanguage)
        let isClickable = linkURL != nil && !linkURL!.isEmpty

        HStack(spacing: 0) {
            // Left colored border (6dp)
            Rectangle()
                .fill(borderColor)
                .frame(width: 6)

            HStack(alignment: .top, spacing: DesignSystem.Spacing.medium) {
                // Icon
                SVGWebView(
                    svgName: iconName,
                    width: DesignSystem.IconSize.standard,
                    height: DesignSystem.IconSize.standard,
                    tintColor: textColor
                )
                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)

                // Title and Message
                VStack(alignment: .leading, spacing: DesignSystem.Spacing.xSmall) {
                    if let title = titleText {
                        Text(title)
                            .font(DesignSystem.Typography.subheadlineBold())
                            .foregroundColor(textColor)
                    }

                    HStack(spacing: 4) {
                        Text(messageText)
                            .font(DesignSystem.Typography.body())
                            .foregroundColor(textColor)

                        if isClickable {
                            SVGWebView(
                                svgName: "ic_external_link",
                                width: 14,
                                height: 14,
                                tintColor: textColor
                            )
                            .frame(width: 14, height: 14)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, DesignSystem.Spacing.standard)
            .padding(.vertical, DesignSystem.Spacing.standard)
        }
        .background(surfaceColor)
        .overlay(
            Rectangle()
                .stroke(borderColor, lineWidth: 1)
        )
        .contentShape(Rectangle())
        .onTapGesture {
            if let urlString = linkURL, let url = URL(string: urlString) {
                UIApplication.shared.open(url)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }
}

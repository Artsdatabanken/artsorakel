//
//  MessageBoxView.swift
//  Artsorakel
//

import SwiftUI

struct MessageBoxView: View {
    let feedItem: RssFeedItem
    let onDismiss: () -> Void
    @EnvironmentObject var localizationManager: LocalizationManager

    private var borderColor: Color {
        switch feedItem.category {
        case .danger:
            return .alertDangerBorderPrimary
        case .warning:
            return .alertWarningBorderPrimary
        case .info:
            return .alertInfoBorderPrimary
        }
    }

    private var surfaceColor: Color {
        switch feedItem.category {
        case .danger:
            return .alertDangerSurfaceSubtle
        case .warning:
            return .alertWarningSurfaceSubtle
        case .info:
            return .alertInfoSurfaceSubtle
        }
    }

    private var textColor: Color {
        switch feedItem.category {
        case .danger:
            return .alertDangerTextPrimary
        case .warning:
            return .alertWarningTextPrimary
        case .info:
            return .alertInfoTextPrimary
        }
    }

    private var iconName: String {
        switch feedItem.category {
        case .danger:
            return "ic_alert_info"
        case .warning:
            return "ic_alert_warning"
        case .info:
            return "ic_alert_info"
        }
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
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

                    // Title and Description
                    VStack(alignment: .leading, spacing: DesignSystem.Spacing.small) {
                        Text(feedItem.title)
                            .font(DesignSystem.Typography.title())
                            .foregroundColor(textColor)

                        descriptionView
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.leading, 10)
                .padding(.trailing, feedItem.isPermanent ? DesignSystem.Spacing.standard : 48) // Extra space for dismiss button
                .padding(.vertical, DesignSystem.Spacing.standard)
            }
            .background(surfaceColor)
            .overlay(
                Rectangle()
                    .stroke(borderColor, lineWidth: 1)
            )

            // Dismiss button (only show if not permanent)
            if !feedItem.isPermanent {
                Button(action: onDismiss) {
                    ZStack {
                        Circle()
                            .fill(Color.surfaceSubtle)
                            .frame(width: 32, height: 32)

                        SVGWebView(
                            svgName: "ic_close",
                            width: 16,
                            height: 16,
                            tintColor: .surfaceAccent
                        )
                        .frame(width: 16, height: 16)
                    }
                }
                .padding(DesignSystem.Spacing.small)
            }
        }
        .fixedSize(horizontal: false, vertical: true)
    }

    @ViewBuilder
    private var descriptionView: some View {
        if let link = feedItem.link, !link.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                Text(feedItem.description)
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(textColor)

                Button(action: {
                    if let url = URL(string: link) {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 4) {
                        Text(localizationManager.localize("read_more", comment: "Read more"))
                            .font(DesignSystem.Typography.body())
                            .foregroundColor(.textAccent)

                        SVGWebView(
                            svgName: "ic_external_link",
                            width: 14,
                            height: 14,
                            tintColor: .textAccent
                        )
                        .frame(width: 14, height: 14)
                    }
                }
                .padding(.top, 4)
            }
        } else {
            Text(feedItem.description)
                .font(DesignSystem.Typography.body())
                .foregroundColor(textColor)
        }
    }
}

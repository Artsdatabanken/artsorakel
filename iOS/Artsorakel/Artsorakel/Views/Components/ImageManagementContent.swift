import SwiftUI

struct ImageManagementContent: View {
    let image: UIImage
    let onReset: () -> Void
    @EnvironmentObject var localizationManager: LocalizationManager

    var body: some View {
        VStack(spacing: DesignSystem.Spacing.standard) {
            // Image card
            VStack(spacing: 0) {
                // Close button
                HStack {
                    Spacer()
                    Button(action: onReset) {
                        ZStack {
                            Circle()
                                .fill(Color.surfaceSubtle)
                                .frame(width: 32, height: 32)

                            if resourceExists("ic_close") {
                                SVGWebView(svgName: "ic_close", width: 16, height: 16, tintColor: .surfaceAccent)
                                    .frame(width: 16, height: 16)
                            } else {
                                Image(systemName: "xmark")
                                    .font(.system(size: 16))
                                    .foregroundColor(Color.surfaceAccent)
                            }
                        }
                    }
                    .padding(DesignSystem.Spacing.small)
                }

                // Hint text
                Text(localizationManager.localize("add_pictures_hint", comment: "Add more pictures or identify"))
                    .font(DesignSystem.Typography.body())
                    .foregroundColor(Color.textPrimary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, DesignSystem.Spacing.xxxLarge)
                    .padding(.top, DesignSystem.Spacing.medium)

                // Image thumbnails (just showing the one image for now)
                HStack(spacing: DesignSystem.Spacing.small) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 80, height: 80)
                        .cornerRadius(DesignSystem.CornerRadius.small)
                        .clipped()
                }
                .padding(.top, DesignSystem.Spacing.medium)

                // Identify button
                Button(action: {
                    // TODO: Trigger identification
                }) {
                    Text(localizationManager.localize("identify", comment: "Identify"))
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textAccent)
                        .padding(.horizontal, DesignSystem.Spacing.large)
                        .padding(.vertical, DesignSystem.Spacing.standard)
                }
                .background(Color.surfacePrimary)
                .overlay(
                    RoundedRectangle(cornerRadius: 28)
                        .stroke(Color.borderAccent, lineWidth: 2)
                )
                .cornerRadius(28)
                .padding(.top, DesignSystem.Spacing.standard)
                .padding(.bottom, DesignSystem.Spacing.standard)
            }
            .background(Color.surfacePrimary)
            .cornerRadius(DesignSystem.CornerRadius.medium)
        }
    }
}

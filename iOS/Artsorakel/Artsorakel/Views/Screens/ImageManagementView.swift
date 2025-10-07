import SwiftUI
import CoreLocation

struct ImageManagementView: View {
    @Binding var isPresented: Bool
    @Binding var showMenuDrawer: Bool
    let image: UIImage
    let location: CLLocation?
    @Environment(\.colorScheme) var colorScheme
    @EnvironmentObject var localizationManager: LocalizationManager

    var body: some View {
        ZStack {
            // Background
            Color.backgroundDefault
                .ignoresSafeArea()

            VStack(spacing: 0) {
                // Header
                HStack(spacing: 0) {
                    // Empty space for symmetry
                    Spacer()
                        .frame(width: DesignSystem.ButtonSize.standard)
                        .padding(.leading, DesignSystem.Spacing.small)

                    // Title
                    Text(localizationManager.localize("image_management", comment: "Image Management"))
                        .font(DesignSystem.Typography.title())
                        .foregroundColor(Color.textPrimary)
                        .frame(maxWidth: .infinity)

                    // Menu button
                    Button(action: {
                        withAnimation {
                            showMenuDrawer = true
                        }
                    }) {
                            SVGWebView(svgName: "ic_menu", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                       
                    }
                    .frame(width: DesignSystem.ButtonSize.standard, height: DesignSystem.ButtonSize.standard)
                    .padding(.trailing, DesignSystem.Spacing.small)
                }
                .frame(height: DesignSystem.ComponentSize.headerHeight)
                .background(Color.backgroundDefault)

                // Image display
                ScrollView {
                    VStack(spacing: DesignSystem.Spacing.large) {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(maxWidth: .infinity)
                            .frame(height: 300)
                            .background(Color.surfacePrimary)
                            .cornerRadius(DesignSystem.CornerRadius.medium)
                            .padding(DesignSystem.Spacing.standard)

                        // Reset button
                        Button(action: {
                            withAnimation {
                                isPresented = false
                            }
                        }) {
                            HStack(spacing: DesignSystem.Spacing.small) {
                                    SVGWebView(svgName: "ic_close", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                        .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)
                               

                                Text(localizationManager.localize("reset", comment: "Reset"))
                                    .font(DesignSystem.Typography.body())
                                    .foregroundColor(Color.textAccent)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: DesignSystem.ButtonSize.standard)
                            .background(Color.surfacePrimary)
                            .overlay(
                                RoundedRectangle(cornerRadius: DesignSystem.CornerRadius.large)
                                    .stroke(Color.textAccent, lineWidth: 2)
                            )
                            .cornerRadius(DesignSystem.CornerRadius.large)
                        }
                        .padding(.horizontal, DesignSystem.Spacing.standard)
                    }
                    .padding(.top, DesignSystem.Spacing.standard)
                }
                .background(Color.backgroundSubtle)
            }
        }
    }
}

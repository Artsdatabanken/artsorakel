import SwiftUI

struct LoadingView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let onAbort: () -> Void
    @State private var showTimeoutMessage = false
    @State private var timer: DispatchWorkItem?

    var body: some View {
        VStack(spacing: DesignSystem.Spacing.large) {
            Spacer()

            ProgressView()
                .scaleEffect(1.5)
                .progressViewStyle(CircularProgressViewStyle(tint: Color.textPrimary))

            if showTimeoutMessage {
                VStack(alignment: .leading, spacing: DesignSystem.Spacing.standard) {
                    Text(localizationManager.localize("timeout_message", comment: "This is taking longer than expected..."))
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textPrimary)

                    Button(action: onAbort) {
                        HStack(spacing: DesignSystem.Spacing.small) {
                            Text(localizationManager.localize("abort_button", comment: "Abort"))
                                .font(DesignSystem.Typography.body())
                                .foregroundColor(Color.textAccent)

                            SVGWebView(svgName: "ic_close", width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard, tintColor: .textAccent)
                                .frame(width: DesignSystem.IconSize.standard, height: DesignSystem.IconSize.standard)

                        }
                        .frame(height: 42)
                        .padding(.horizontal, 20)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .background(Color.surfacePrimary)
                    .overlay(
                        RoundedRectangle(cornerRadius: 21)
                            .stroke(Color.borderAccent, lineWidth: 2)
                    )
                    .cornerRadius(21)
                    .fixedSize()
                }
                .padding(.horizontal, DesignSystem.Spacing.large)
                .padding(.top, DesignSystem.Spacing.large)
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.backgroundSubtle)
        .onAppear {
            // Show timeout message after 50ms (for testing)
            let workItem = DispatchWorkItem {
                withAnimation {
                    showTimeoutMessage = true
                }
            }
            timer = workItem
            DispatchQueue.main.asyncAfter(deadline: .now() + 5.00, execute: workItem)
        }
        .onDisappear {
            timer?.cancel()
        }
    }
}

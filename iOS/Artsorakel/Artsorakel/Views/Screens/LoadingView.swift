import SwiftUI

struct LoadingView: View {
    @EnvironmentObject var localizationManager: LocalizationManager
    let onAbort: () -> Void
    @State private var showTimeoutMessage = false

    var body: some View {
        VStack(spacing: DesignSystem.Spacing.large) {
            Spacer()

            ProgressView()
                .scaleEffect(1.5)
                .progressViewStyle(CircularProgressViewStyle(tint: Color.textPrimary))

            if showTimeoutMessage {
                VStack(spacing: DesignSystem.Spacing.standard) {
                    Text(localizationManager.localize("timeout_message", comment: "This is taking longer than expected..."))
                        .font(DesignSystem.Typography.body())
                        .foregroundColor(Color.textPrimary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, DesignSystem.Spacing.large)
                        .padding(.top, DesignSystem.Spacing.large)

                    Button(action: onAbort) {
                        HStack {
                            if resourceExists("ic_close") {
                                SVGWebView(svgName: "ic_close", width: 20, height: 20, tintColor: .textAccent)
                            } else {
                                Image(systemName: "xmark")
                                    .foregroundColor(Color.textAccent)
                            }
                            Text(localizationManager.localize("abort_button", comment: "Abort"))
                                .font(DesignSystem.Typography.body())
                                .foregroundColor(Color.textAccent)
                        }
                        .padding(.horizontal, DesignSystem.Spacing.large)
                        .padding(.vertical, DesignSystem.Spacing.standard)
                        .background(Color.surfaceSubtle)
                        .cornerRadius(28)
                    }
                }
            }

            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.backgroundSubtle)
        .onAppear {
            // Show timeout message after 15 seconds
            DispatchQueue.main.asyncAfter(deadline: .now() + 15) {
                withAnimation {
                    showTimeoutMessage = true
                }
            }
        }
    }
}

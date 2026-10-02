import SwiftUI

struct HomeView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image("mascot_family")
                .resizable()
                .scaledToFit()
                .frame(maxWidth: 260)
            Text("Gift Manager")
                .font(.largeTitle.bold())
            Text("Des idées. Moins de doublons. Plus de magie.")
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(20)
    }
}

#Preview {
    HomeView()
}

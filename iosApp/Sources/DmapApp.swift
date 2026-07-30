import SwiftUI
import DmapShared

/// P0b exit criterion: the iOS app launches to a settings screen backed by the
/// same shared core as Android, and a one-line change in `commonMain` reaches
/// this screen.
@main
struct DmapApp: App {
    var body: some Scene {
        WindowGroup {
            BackendSettingsView(viewModel: BackendSettingsViewModel())
        }
    }
}

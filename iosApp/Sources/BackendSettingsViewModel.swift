import Foundation
import Observation
import DmapShared

/// A NATIVE view model that calls shared use cases.
///
/// This is interop rule 1 from the plan: shared `StateFlow`s are deliberately
/// not exported into SwiftUI. Presentation stays native, the exported Swift API
/// stays small and idiomatic, and no presentation concern leaks into
/// `commonMain`. The Android twin is `BackendSettingsViewModel.kt` — same shape,
/// same shared types.
@Observable
final class BackendSettingsViewModel {

    private let store: BackendConfigStore

    var config: BackendConfig
    var problems: [String] { config.validate() as? [String] ?? [] }

    init(store: BackendConfigStore = UserDefaultsBackendConfigStore()) {
        self.store = store
        // Shared defaults, so both platforms start from the same place.
        self.config = BackendConfig()
        Task { await load() }
    }

    @MainActor
    func load() async {
        do {
            config = try await store.load()
        } catch {
            // A half-configured backend is a normal development state, not an
            // error worth surfacing loudly.
            print("dmap: could not load backend config: \(error)")
        }
    }

    @MainActor
    func save() async {
        do {
            try await store.save(config: config)
        } catch {
            print("dmap: could not save backend config: \(error)")
        }
    }

    func update(_ transform: (BackendConfig) -> BackendConfig) {
        config = transform(config)
    }
}

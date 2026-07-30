import SwiftUI
import DmapShared

/// Twin of `BackendSettingsScreen.kt`. Same fields, same shared `BackendConfig`,
/// native presentation — which is the whole architecture in miniature.
struct BackendSettingsView: View {

    @State var viewModel: BackendSettingsViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("dmap3 · P0b scaffold. The map arrives in P1.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                Section("Backends") {
                    urlField("Tiles (:8080)", value: viewModel.config.tiles) { config, new in
                        config.doCopy(tiles: new)
                    }
                    urlField("Places (:8081)", value: viewModel.config.places) { config, new in
                        config.doCopy(places: new)
                    }
                    urlField("Routing (:8082)", value: viewModel.config.routing) { config, new in
                        config.doCopy(routing: new)
                    }
                    urlField("Imagery (:8083)", value: viewModel.config.imagery) { config, new in
                        config.doCopy(imagery: new)
                    }
                    urlField("Transit (:8084)", value: viewModel.config.transit) { config, new in
                        config.doCopy(transit: new)
                    }
                    urlField("Hazards (:8085)", value: viewModel.config.hazards) { config, new in
                        config.doCopy(hazards: new)
                    }
                    urlField("Identity (:8086)", value: viewModel.config.identity) { config, new in
                        config.doCopy(identity: new)
                    }
                }

                if !viewModel.problems.isEmpty {
                    Section {
                        ForEach(viewModel.problems, id: \.self) { problem in
                            Text("• \(problem)")
                                .font(.footnote)
                                .foregroundStyle(.red)
                        }
                    }
                }

                Section {
                    Button("Save") {
                        Task { await viewModel.save() }
                    }
                }
            }
            .navigationTitle("Backend settings")
        }
    }

    /// NOTE: `doCopy` is how Kotlin data-class `copy` surfaces in Swift, and it
    /// requires every parameter. That is ugly enough that in P1 it is worth
    /// adding explicit `withTiles(_:)`-style helpers in `commonMain` instead —
    /// a reminder that the exported surface is a design artifact and should be
    /// reviewed like an API.
    private func urlField(
        _ label: String,
        value: String,
        transform: @escaping (BackendConfig, String) -> BackendConfig
    ) -> some View {
        TextField(
            label,
            text: Binding(
                get: { value },
                set: { new in viewModel.update { transform($0, new) } }
            ),
            prompt: Text("not configured")
        )
        .textInputAutocapitalization(.never)
        .autocorrectionDisabled()
        .keyboardType(.URL)
    }
}

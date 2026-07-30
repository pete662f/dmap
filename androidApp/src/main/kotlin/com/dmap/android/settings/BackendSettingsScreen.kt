package com.dmap.android.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dmap.config.BackendConfig

/**
 * Runtime backend settings. This is the screen that replaces dmap2's
 * compile-time BuildConfig chain, and it is the reason a physical device no
 * longer needs a rebuild to point at a different host.
 *
 * Its iOS twin is `BackendSettingsView.swift` — same fields, same shared
 * [BackendConfig], native presentation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackendSettingsScreen(
    config: BackendConfig,
    problems: List<String>,
    onChange: (BackendConfig) -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Backend settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "dmap3 · P0b scaffold. The map arrives in P1.",
                style = MaterialTheme.typography.bodySmall,
            )

            UrlField("Tiles (:8080)", config.tiles) { onChange(config.copy(tiles = it)) }
            UrlField("Places (:8081)", config.places) { onChange(config.copy(places = it)) }
            UrlField("Routing (:8082)", config.routing) { onChange(config.copy(routing = it)) }
            UrlField("Imagery (:8083)", config.imagery) { onChange(config.copy(imagery = it)) }
            UrlField("Transit (:8084)", config.transit) { onChange(config.copy(transit = it)) }
            UrlField("Hazards (:8085)", config.hazards) { onChange(config.copy(hazards = it)) }
            UrlField("Identity (:8086)", config.identity) { onChange(config.copy(identity = it)) }

            if (problems.isNotEmpty()) {
                Text(
                    problems.joinToString("\n") { "• $it" },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@Composable
private fun UrlField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        placeholder = { Text("not configured") },
        modifier = Modifier.fillMaxWidth(),
    )
}

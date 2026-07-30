package com.dmap.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dmap.android.settings.BackendSettingsScreen
import com.dmap.android.theme.DmapTheme
import com.dmap.config.BackendConfig
import com.dmap.config.BackendConfigStore
import kotlinx.coroutines.launch

/**
 * P0b exit criterion: the app launches to a settings screen, backed by the
 * shared module. The map arrives in P1.
 *
 * Note the shape — a NATIVE ViewModel calling into shared code. Shared
 * StateFlows are deliberately not exported into the UI layer; presentation
 * stays platform-native so the Swift API stays idiomatic.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = (application as DmapApplication).backendConfigStore

        setContent {
            DmapTheme {
                val viewModel: BackendSettingsViewModel = viewModel(
                    factory = BackendSettingsViewModel.factory(store),
                )
                BackendSettingsScreen(
                    config = viewModel.config,
                    problems = viewModel.problems,
                    onChange = viewModel::update,
                    onSave = { lifecycleScope.launch { viewModel.save() } },
                )
            }
        }
    }
}

class BackendSettingsViewModel(private val store: BackendConfigStore) : ViewModel() {

    var config by mutableStateOf(BackendConfig())
        private set

    val problems: List<String> get() = config.validate()

    init {
        // Load on construction. In P0b this is the whole app.
        kotlinx.coroutines.MainScope().launch { config = store.load() }
    }

    fun update(next: BackendConfig) {
        config = next
    }

    suspend fun save() = store.save(config)

    companion object {
        fun factory(store: BackendConfigStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BackendSettingsViewModel(store) as T
        }
    }
}

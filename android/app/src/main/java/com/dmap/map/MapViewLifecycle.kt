package com.dmap.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.maplibre.android.maps.MapView

@Composable
internal fun rememberMapViewWithLifecycle(
    lifecycleOwner: LifecycleOwner,
): MapView {
    val context = LocalContext.current
    val mapView = remember(lifecycleOwner) { MapView(context) }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        mapView.onCreate(null)

        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                mapView.onStart()
            }

            override fun onResume(owner: LifecycleOwner) {
                mapView.onResume()
            }

            override fun onPause(owner: LifecycleOwner) {
                mapView.onPause()
            }

            override fun onStop(owner: LifecycleOwner) {
                mapView.onStop()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                if (!destroyed) {
                    destroyed = true
                    mapView.onDestroy()
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                destroyed = true
                mapView.onDestroy()
            }
        }
    }

    return mapView
}

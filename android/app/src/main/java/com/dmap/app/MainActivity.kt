package com.dmap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.dmap.map.MapScreen
import com.dmap.ui.theme.DmapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DmapApplication

        setContent {
            DmapTheme {
                Surface {
                    MapScreen(app.appContainer)
                }
            }
        }
    }
}

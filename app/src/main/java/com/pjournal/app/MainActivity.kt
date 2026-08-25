package com.pjournal.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.pjournal.app.data.PreferencesManager
import com.pjournal.app.ui.navigation.NavGraph
import com.pjournal.app.ui.theme.PJournalTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)

        // Trigger sync on app start
        lifecycleScope.launch {
            try {
                (application as PJournalApp).syncManager.triggerSyncIfReady()
            } catch (_: Exception) {}
        }

        setContent {
            val darkTheme by prefs.darkTheme.collectAsState(initial = false)
            val einkMode by prefs.einkMode.collectAsState(initial = false)
            val forceLandscape by prefs.forceLandscape.collectAsState(initial = false)
            val scope = rememberCoroutineScope()

            LaunchedEffect(forceLandscape) {
                requestedOrientation = if (forceLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            PJournalTheme(
                darkTheme = darkTheme,
                einkMode = einkMode
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(
                        darkTheme = darkTheme,
                        einkMode = einkMode,
                        onToggleTheme = {
                            scope.launch {
                                prefs.setDarkTheme(!darkTheme)
                            }
                        },
                        prefs = prefs
                    )
                }
            }
        }
    }
}

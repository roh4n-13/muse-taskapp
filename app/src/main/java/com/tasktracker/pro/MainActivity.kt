package com.tasktracker.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasktracker.pro.data.ThemeMode
import com.tasktracker.pro.ui.MainScreen
import com.tasktracker.pro.ui.theme.TaskTrackerProTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by appContainer().settings.theme
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            TaskTrackerProTheme(themeMode = themeMode) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val container = appContainer()
        if (container.syncEngine.isEnabled) {
            // Refresh-on-resume: pull sheet changes as soon as the app is visible again.
            container.appScope.launch { container.syncEngine.syncNow() }
            container.syncPoller.start(container.appScope)
        }
    }

    override fun onPause() {
        appContainer().syncPoller.stop()
        super.onPause()
    }
}

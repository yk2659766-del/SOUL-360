package com.soul360.design

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.soul360.design.data.AppSettings
import com.soul360.design.data.PhotoProject
import com.soul360.design.data.ProjectStore
import com.soul360.design.ui.EditorScreen
import com.soul360.design.ui.HomeScreen
import com.soul360.design.ui.SettingsScreen
import com.soul360.design.ui.Soul360Theme
import com.soul360.design.ui.StudioScreen
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data class Editor(val project: PhotoProject) : Screen
    data class Studio(val room: File?) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProjectStore(applicationContext)
        val settings = AppSettings(applicationContext)
        setContent {
            Soul360Theme { App(store, settings) }
        }
    }
}

@Composable
private fun App(store: ProjectStore, settings: AppSettings) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val goHome = { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            store = store,
            onOpenProject = { screen = Screen.Editor(it) },
            onOpenStudio = { screen = Screen.Studio(it) },
            onOpenSettings = { screen = Screen.Settings },
        )
        is Screen.Editor -> EditorScreen(store, settings, s.project, onClose = goHome)
        is Screen.Studio -> {
            BackHandler(onBack = goHome)
            StudioScreen(store, s.room, onClose = goHome)
        }
        Screen.Settings -> {
            BackHandler(onBack = goHome)
            SettingsScreen(settings, onClose = goHome)
        }
    }
}

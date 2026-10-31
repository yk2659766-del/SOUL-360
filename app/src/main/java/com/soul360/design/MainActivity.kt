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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import com.soul360.design.ui.SplashScreen
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

private fun Screen.depth(): Int = when (this) {
    Screen.Home -> 0
    else -> 1
}

private fun Screen.key(): String = when (this) {
    Screen.Home -> "home"
    is Screen.Editor -> "editor-" + project.id
    is Screen.Studio -> "studio"
    Screen.Settings -> "settings"
}

@Composable
private fun App(store: ProjectStore, settings: AppSettings) {
    var showSplash by rememberSaveable { mutableStateOf(true) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val goHome = { screen = Screen.Home }

    AnimatedContent(
        targetState = showSplash,
        transitionSpec = {
            (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 1.04f))
                .togetherWith(fadeOut(tween(450)) + scaleOut(tween(450), targetScale = 0.96f))
        },
        label = "splash",
    ) { splash ->
        if (splash) {
            SplashScreen(onFinished = { showSplash = false })
        } else {
            AnimatedContent(
                targetState = screen,
                contentKey = { it.key() },
                transitionSpec = {
                    val forward = targetState.depth() >= initialState.depth()
                    val enter = slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { w -> if (forward) w / 4 else -w / 4 } +
                        fadeIn(tween(320, delayMillis = 60))
                    val exit = slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { w -> if (forward) -w / 6 else w / 4 } +
                        fadeOut(tween(220))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                },
                label = "screens",
            ) { s ->
                when (s) {
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
        }
    }
}

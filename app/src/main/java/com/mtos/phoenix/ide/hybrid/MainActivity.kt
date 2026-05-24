package com.mtos.phoenix.ide.hybrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mtos.phoenix.ide.hybrid.data.AppDatabase
import com.mtos.phoenix.ide.hybrid.data.WorkspaceRepository
import com.mtos.phoenix.ide.hybrid.ui.HomeScreen
import com.mtos.phoenix.ide.hybrid.ui.TemplateScreen
import com.mtos.phoenix.ide.hybrid.ui.WorkspaceScreen
import com.mtos.phoenix.ide.hybrid.viewmodel.IdeViewModel

enum class AppScreen {
    Home,
    Templates,
    Workspace
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = WorkspaceRepository(applicationContext, database.workspaceDao())
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return IdeViewModel(repository) as T
            }
        }
        val viewModel: IdeViewModel by viewModels { factory }

        setContent {
            var currentScreen by remember { mutableStateOf(AppScreen.Home) }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2196F3),
                    onPrimary = Color.White,
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    error = Color(0xFFCF6679)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            AppScreen.Home -> HomeScreen(
                                viewModel = viewModel,
                                onOpenTemplates = { currentScreen = AppScreen.Templates },
                                onOpenProject = { currentScreen = AppScreen.Workspace }
                            )
                            AppScreen.Templates -> TemplateScreen(
                                viewModel = viewModel,
                                onBack = { currentScreen = AppScreen.Home }
                            )
                            AppScreen.Workspace -> WorkspaceScreen(
                                viewModel = viewModel,
                                onBackToHome = { currentScreen = AppScreen.Home }
                            )
                        }
                    }
                }
            }
        }
    }
}

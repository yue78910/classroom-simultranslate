package com.classroom.simultranslate

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.classroom.simultranslate.ui.ModelDownloadScreen
import com.classroom.simultranslate.ui.HomeScreen
import com.classroom.simultranslate.ui.SettingsScreen
import com.classroom.simultranslate.ui.theme.SimulTranslateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimulTranslateTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var hasMicPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                viewModel.getApplication<android.app.Application>().applicationContext,
                Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) viewModel.startSession()
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                ui = ui,
                onStart = {
                    if (hasMicPermission) {
                        viewModel.startSession()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPause = viewModel::pauseSession,
                onResume = viewModel::resumeSession,
                onEnd = viewModel::endSession,
                onClear = viewModel::clearSubtitles,
                onOpenSettings = { navController.navigate("settings") },
                onOpenModels = { navController.navigate("models") },
            )
        }
        composable("settings") {
            SettingsScreen(
                ui = ui,
                onBack = { navController.popBackStack() },
                onDirectionChange = viewModel::setDirection,
                onModeChange = viewModel::setMode,
                onApiKeyChange = viewModel::setApiKey,
                onFontScaleChange = viewModel::setFontScale,
                onMirrorChange = viewModel::setUseMirror,
                onOpenModels = { navController.navigate("models") },
            )
        }
        composable("models") {
            ModelDownloadScreen(
                manifest = viewModel.manifest,
                installedPacks = viewModel.installedPacks.collectAsStateWithLifecycle().value,
                downloadStates = viewModel.downloadStates.collectAsStateWithLifecycle().value,
                onBack = { navController.popBackStack() },
                onDownload = viewModel::downloadPack,
                onDelete = viewModel::deletePack,
            )
        }
    }
}

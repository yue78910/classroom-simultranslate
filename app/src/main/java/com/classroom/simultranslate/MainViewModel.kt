package com.classroom.simultranslate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classroom.simultranslate.audio.AudioCapture
import com.classroom.simultranslate.data.EngineMode
import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SessionConfig
import com.classroom.simultranslate.data.SettingsRepository
import com.classroom.simultranslate.data.SubtitleSnapshot
import com.classroom.simultranslate.data.TranslationDirection
import com.classroom.simultranslate.data.TranslationSessionState
import com.classroom.simultranslate.engine.EngineCoordinator
import com.classroom.simultranslate.engine.OfflineEngine
import com.classroom.simultranslate.engine.OnlineRealtimeEngine
import com.classroom.simultranslate.offline.DownloadManager
import com.classroom.simultranslate.offline.ModelManager
import com.classroom.simultranslate.offline.ModelManifest
import com.classroom.simultranslate.offline.ModelManifestLoader
import com.classroom.simultranslate.offline.OfflineModelPack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DownloadUiState(
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val detail: String = "",
)

data class HomeUiState(
    val snapshot: SubtitleSnapshot = SubtitleSnapshot(),
    val status: EngineStatus = EngineStatus.Idle,
    val rms: Float = 0f,
    val direction: TranslationDirection = TranslationDirection.EN_TO_ZH,
    val mode: EngineMode = EngineMode.AUTO,
    val hasApiKey: Boolean = false,
    val isRunning: Boolean = false,
    val sessionState: TranslationSessionState = TranslationSessionState.IDLE,
    val fontScale: Float = 1.4f,
    val useMirror: Boolean = true,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = SettingsRepository(application)
    private val modelManager = ModelManager(application)
    private val downloadManager = DownloadManager(
        modelManager,
        useMirror = { settings.useMirror },
    )
    private val audioCapture = AudioCapture()
    private val onlineEngine = OnlineRealtimeEngine()
    private val offlineEngine = OfflineEngine(modelManager, viewModelScope, application.assets)
    private val coordinator = EngineCoordinator(
        audioCapture = audioCapture,
        onlineEngine = onlineEngine,
        offlineEngineProvider = { offlineEngine },
        onUiState = ::onCoordinatorState,
        onBanner = { message ->
            viewModelScope.launch { _banner.emit(message) }
        },
    )

    private val _ui = MutableStateFlow(
        HomeUiState(
            direction = settings.direction,
            mode = settings.mode,
            hasApiKey = settings.apiKey.isNotBlank(),
            fontScale = settings.subtitleFontScale,
            useMirror = settings.useMirror,
        ),
    )
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private val _banner = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val banner: SharedFlow<String> = _banner.asSharedFlow()

    val manifest: ModelManifest = ModelManifestLoader.load(application)
    val installedPacks: StateFlow<Set<String>> = modelManager.installedPacks

    private val _downloadStates = MutableStateFlow<Map<String, DownloadUiState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadUiState>> = _downloadStates.asStateFlow()

    fun startSession() {
        _ui.update {
            it.copy(
                isRunning = true,
                sessionState = TranslationSessionState.RUNNING,
            )
        }
        coordinator.start(settings.sessionConfig())
    }

    fun pauseSession() {
        if (_ui.value.sessionState != TranslationSessionState.RUNNING) return
        coordinator.pause()
        _ui.update {
            it.copy(
                isRunning = false,
                sessionState = TranslationSessionState.PAUSED,
            )
        }
    }

    fun resumeSession() {
        if (_ui.value.sessionState != TranslationSessionState.PAUSED) return
        coordinator.resume()
        _ui.update {
            it.copy(
                isRunning = true,
                sessionState = TranslationSessionState.RUNNING,
            )
        }
    }

    fun endSession() {
        _ui.update {
            it.copy(
                isRunning = false,
                sessionState = TranslationSessionState.IDLE,
            )
        }
        coordinator.stop()
    }

    fun clearSubtitles() {
        coordinator.clearSubtitles()
    }

    fun setDirection(direction: TranslationDirection) {
        settings.direction = direction
        _ui.update { it.copy(direction = direction) }
    }

    fun setMode(mode: EngineMode) {
        settings.mode = mode
        _ui.update { it.copy(mode = mode) }
    }

    fun setApiKey(apiKey: String) {
        settings.apiKey = apiKey
        _ui.update { it.copy(hasApiKey = apiKey.isNotBlank()) }
    }

    fun setFontScale(scale: Float) {
        settings.subtitleFontScale = scale
        _ui.update { it.copy(fontScale = scale) }
    }

    fun setUseMirror(enabled: Boolean) {
        settings.useMirror = enabled
        _ui.update { it.copy(useMirror = enabled) }
    }

    fun downloadPack(pack: OfflineModelPack) {
        if (_downloadStates.value[pack.id]?.downloading == true) return
        _downloadStates.update {
            it + (pack.id to DownloadUiState(downloading = true, progress = 0f, detail = "准备下载"))
        }
        viewModelScope.launch {
            val result = downloadManager.downloadPack(pack) { progress, detail ->
                _downloadStates.update {
                    it + (pack.id to DownloadUiState(downloading = true, progress = progress, detail = detail))
                }
            }
            result.onSuccess {
                _downloadStates.update { it + (pack.id to DownloadUiState(downloading = false, progress = 1f, detail = "已安装")) }
            }.onFailure { error ->
                _downloadStates.update {
                    it + (pack.id to DownloadUiState(downloading = false, progress = 0f, detail = error.message ?: "下载失败"))
                }
                _banner.tryEmit(error.message ?: "下载失败")
            }
        }
    }

    fun deletePack(pack: OfflineModelPack) {
        modelManager.deletePack(pack.id)
        _downloadStates.update { it - pack.id }
    }

    private fun onCoordinatorState(snapshot: SubtitleSnapshot, status: EngineStatus, rms: Float) {
        val nextState = when {
            status is EngineStatus.Error ->
                TranslationSessionState.IDLE
            status == EngineStatus.Idle &&
                _ui.value.sessionState == TranslationSessionState.IDLE ->
                TranslationSessionState.IDLE
            _ui.value.sessionState == TranslationSessionState.PAUSED ->
                TranslationSessionState.PAUSED
            else -> _ui.value.sessionState
        }
        _ui.update {
            it.copy(
                snapshot = snapshot,
                status = status,
                rms = rms,
                isRunning = nextState == TranslationSessionState.RUNNING,
                sessionState = nextState,
            )
        }
    }

    override fun onCleared() {
        coordinator.release()
        onlineEngine.release()
        super.onCleared()
    }
}

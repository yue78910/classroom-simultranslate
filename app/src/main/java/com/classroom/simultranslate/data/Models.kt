package com.classroom.simultranslate.data

enum class TranslationDirection(
    val label: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val openAiTargetLanguage: String,
) {
    EN_TO_ZH("英 → 中", "English", "简体中文", "zh"),
    ZH_TO_EN("中 → 英", "简体中文", "English", "en"),
}

enum class EngineMode(val label: String) {
    AUTO("自动（在线优先，断网回退离线）"),
    ONLINE("仅在线"),
    OFFLINE("仅离线"),
}

enum class TranslationSessionState {
    IDLE,
    RUNNING,
    PAUSED,
}

data class SessionConfig(
    val direction: TranslationDirection = TranslationDirection.EN_TO_ZH,
    val mode: EngineMode = EngineMode.AUTO,
    val apiKey: String = "",
)

data class SubtitlePair(
    val source: String,
    val target: String,
)

data class SubtitleSnapshot(
    val sourcePartial: String = "",
    val targetPartial: String = "",
    val history: List<SubtitlePair> = emptyList(),
)

sealed interface EngineStatus {
    data object Idle : EngineStatus
    data object Starting : EngineStatus
    data object Online : EngineStatus
    data object Offline : EngineStatus
    data class Error(val message: String) : EngineStatus
}

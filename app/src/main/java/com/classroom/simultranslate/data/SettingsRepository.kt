package com.classroom.simultranslate.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var direction: TranslationDirection
        get() = runCatching {
            TranslationDirection.valueOf(prefs.getString(KEY_DIRECTION, TranslationDirection.EN_TO_ZH.name).orEmpty())
        }.getOrDefault(TranslationDirection.EN_TO_ZH)
        set(value) = prefs.edit().putString(KEY_DIRECTION, value.name).apply()

    var mode: EngineMode
        get() = runCatching {
            EngineMode.valueOf(prefs.getString(KEY_MODE, EngineMode.AUTO.name).orEmpty())
        }.getOrDefault(EngineMode.AUTO)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    var subtitleFontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.4f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()

    var useMirror: Boolean
        get() = prefs.getBoolean(KEY_USE_MIRROR, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_MIRROR, value).apply()

    fun sessionConfig() = SessionConfig(
        direction = direction,
        mode = mode,
        apiKey = apiKey,
    )

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_DIRECTION = "direction"
        const val KEY_MODE = "mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_USE_MIRROR = "use_mirror"
    }
}

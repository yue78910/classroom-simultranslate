package com.classroom.simultranslate.offline

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelFile(
    val name: String,
    val url: String,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
data class OfflineModelPack(
    val id: String,
    val label: String,
    val kind: String,
    val type: String,
    val url: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val extractNames: List<String> = emptyList(),
    val files: List<ModelFile> = emptyList(),
)

@Serializable
data class ModelManifest(
    val version: Int,
    val packs: List<OfflineModelPack>,
)

object ModelManifestLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): ModelManifest {
        val raw = context.assets.open("offline_models.json").bufferedReader().use { it.readText() }
        return json.decodeFromString<ModelManifest>(raw)
    }
}


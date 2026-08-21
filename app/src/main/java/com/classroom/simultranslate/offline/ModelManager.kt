package com.classroom.simultranslate.offline

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelManager(private val context: Context) {
    private val root: File by lazy {
        context.getExternalFilesDir("offline_models") ?: File(context.filesDir, "offline_models")
    }

    private val _installedPacks = MutableStateFlow<Set<String>>(readInstalled())
    val installedPacks: StateFlow<Set<String>> = _installedPacks.asStateFlow()

    fun isInstalled(packId: String): Boolean = markerFile(packId).exists()

    fun packDir(packId: String): File = File(root, packId)

    fun markInstalled(packId: String, version: String) {
        val dir = packDir(packId)
        dir.mkdirs()
        markerFile(packId).writeText(version)
        _installedPacks.value = readInstalled()
    }

    fun deletePack(packId: String) {
        val dir = packDir(packId)
        if (dir.exists()) dir.deleteRecursively()
        markerFile(packId).delete()
        _installedPacks.value = readInstalled()
    }

    fun asrFiles(): AsrFiles? {
        val dir = packDir(PACK_ASR)
        if (!isInstalled(PACK_ASR)) return null
        return AsrFiles(
            encoder = File(dir, "encoder-epoch-99-avg-1.int8.onnx"),
            decoder = File(dir, "decoder-epoch-99-avg-1.onnx"),
            joiner = File(dir, "joiner-epoch-99-avg-1.onnx"),
            tokens = File(dir, "tokens.txt"),
        )
    }

    fun mtFiles(): MtFiles? {
        val dir = packDir(PACK_MT)
        if (!isInstalled(PACK_MT)) return null
        return MtFiles(
            encoder = File(dir, "encoder_model_int8.onnx"),
            decoder = File(dir, "decoder_model_int8.onnx"),
            tokenizer = File(dir, "sentencepiece.bpe.model"),
        )
    }

    private fun markerFile(packId: String): File = File(root, "$packId.installed")

    private fun readInstalled(): Set<String> {
        if (!root.exists()) return emptySet()
        return root.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".installed") }
            ?.map { it.name.removeSuffix(".installed") }
            ?.toSet()
            .orEmpty()
    }

    companion object {
        const val PACK_ASR = "asr-zh-en"
        const val PACK_MT = "nllb-600m-zh-en"
    }
}

data class AsrFiles(
    val encoder: File,
    val decoder: File,
    val joiner: File,
    val tokens: File,
)

data class MtFiles(
    val encoder: File,
    val decoder: File,
    val tokenizer: File,
)

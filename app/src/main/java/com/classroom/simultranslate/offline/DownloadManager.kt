package com.classroom.simultranslate.offline

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

class DownloadManager(
    private val modelManager: ModelManager,
    private val useMirror: () -> Boolean = { true },
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun downloadPack(
        pack: OfflineModelPack,
        onProgress: (progress: Float, detail: String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            when (pack.type) {
                "tar.bz2" -> downloadAndExtractTar(pack, onProgress)
                "files" -> downloadFiles(pack, onProgress)
                else -> error("未知模型包类型：${pack.type}")
            }
            modelManager.markInstalled(pack.id, "v${pack.sizeBytes ?: 1}")
        }
    }

    private fun downloadAndExtractTar(
        pack: OfflineModelPack,
        onProgress: (Float, String) -> Unit,
    ) {
        val url = pack.url ?: error("模型包缺少下载地址")
        val temp = File(modelManager.packDir(pack.id).parentFile, "${pack.id}.tar.bz2")
        temp.parentFile?.mkdirs()
        download(url, temp, pack.sizeBytes ?: 0L, pack.sha256) { progress, detail ->
            onProgress(progress * 0.85f, detail)
        }

        val dest = modelManager.packDir(pack.id)
        dest.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(temp))))
            .use { tar ->
                var entry = tar.nextTarEntry
                val wanted = pack.extractNames.toSet()
                var done = 0
                while (entry != null) {
                    val name = entry.name.substringAfterLast('/')
                    if (name in wanted) {
                        FileOutputStream(File(dest, name)).use { out ->
                            tar.copyTo(BufferedOutputStream(out))
                        }
                        done++
                    }
                    entry = tar.nextTarEntry
                }
                if (done < wanted.size) error("压缩包内缺少模型文件：找到 $done/${wanted.size}")
            }
        temp.delete()
        onProgress(1f, "解压完成")
    }

    private fun downloadFiles(
        pack: OfflineModelPack,
        onProgress: (Float, String) -> Unit,
    ) {
        val dest = modelManager.packDir(pack.id)
        dest.mkdirs()
        var totalProgress = 0f
        pack.files.forEachIndexed { index, file ->
            val target = File(dest, file.name)
            onProgress(totalProgress, "下载 ${file.name}")
            download(
                url = file.url,
                target = target,
                knownSize = file.sizeBytes ?: 0L,
                expectedSha = file.sha256,
            ) { fileProgress, detail ->
                val overall = (index + fileProgress) / pack.files.size
                onProgress(overall, "$detail · ${file.name}")
            }
            totalProgress = (index + 1f) / pack.files.size
        }
        onProgress(1f, "模型下载完成")
    }

    private fun download(
        url: String,
        target: File,
        knownSize: Long,
        expectedSha: String?,
        onProgress: (Float, String) -> Unit,
    ) {
        if (target.exists() && target.length() > 0 && sha256(target) == expectedSha) {
            onProgress(1f, "文件已存在")
            return
        }

        val urls = if (useMirror()) {
            listOf(DownloadMirror.mirror(url), url).distinct()
        } else {
            listOf(url)
        }
        var lastError: Exception? = null
        for (candidate in urls) {
            try {
                downloadFrom(candidate, target, knownSize, expectedSha, onProgress)
                return
            } catch (e: Exception) {
                lastError = e
                target.delete()
            }
        }
        throw lastError ?: error("下载失败")
    }

    private fun downloadFrom(
        url: String,
        target: File,
        knownSize: Long,
        expectedSha: String?,
        onProgress: (Float, String) -> Unit,
    ) {
        val resumeFrom = if (target.exists() && knownSize > 0) target.length() else 0L
        val request = Request.Builder()
            .url(url)
            .apply {
                if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
            }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 206) {
                error("下载失败：HTTP ${response.code}")
            }
            val total = if (knownSize > 0) knownSize else response.body?.contentLength() ?: 0L
            val body = response.body ?: error("响应为空")
            val output = FileOutputStream(target, resumeFrom > 0)
            val input = body.byteStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = resumeFrom
            var reported = -1
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                downloaded += read
                if (total > 0) {
                    val progress = (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
                    if ((progress * 100).toInt() != reported) {
                        reported = (progress * 100).toInt()
                        onProgress(progress, "已下载 ${formatBytes(downloaded)} / ${formatBytes(total)}")
                    }
                }
            }
            output.flush()
            output.close()
        }

        if (!expectedSha.isNullOrBlank() && sha256(target) != expectedSha) {
            target.delete()
            error("校验失败：文件哈希不一致")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(bytes.toDouble() / (1L shl 30))
        bytes >= 1L shl 20 -> "%.1f MB".format(bytes.toDouble() / (1L shl 20))
        bytes >= 1L shl 10 -> "%.1f KB".format(bytes.toDouble() / (1L shl 10))
        else -> "$bytes B"
    }
}

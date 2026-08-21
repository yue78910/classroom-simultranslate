package com.classroom.simultranslate.offline

object DownloadMirror {
    private const val HUGGING_FACE = "https://huggingface.co/"
    private const val HF_MIRROR = "https://hf-mirror.com/"
    private const val GITHUB = "https://github.com/"
    private const val GITHUB_PROXY = "https://ghfast.top/"

    fun mirror(url: String): String = when {
        url.startsWith(HUGGING_FACE, ignoreCase = true) ->
            HF_MIRROR + url.removePrefix(HUGGING_FACE)
        url.startsWith(GITHUB, ignoreCase = true) ->
            GITHUB_PROXY + url
        else -> url
    }
}

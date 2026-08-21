package com.classroom.simultranslate.util

object LanguageDetector {
    private val cjkRegex = Regex("[\\u4E00-\\u9FFF\\u3400-\\u4DBF\\uF900-\\uFAFF]")

    fun isMostlyChinese(text: String): Boolean {
        if (text.isBlank()) return false
        val letters = text.count { it.isLetterOrDigit() }
        if (letters == 0) return false
        val chinese = text.count { cjkRegex.matches(it.toString()) }
        return chinese.toDouble() / letters >= 0.6
    }
}


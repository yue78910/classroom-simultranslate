package com.classroom.simultranslate.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDetectorTest {
    @Test
    fun `detects Chinese text`() {
        assertTrue(LanguageDetector.isMostlyChinese("信号与系统课程开始"))
    }

    @Test
    fun `detects English text`() {
        assertFalse(LanguageDetector.isMostlyChinese("This is a communication engineering lecture"))
    }
}


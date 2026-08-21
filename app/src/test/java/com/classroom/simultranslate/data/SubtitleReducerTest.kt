package com.classroom.simultranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleReducerTest {
    @Test
    fun `deltas accumulate into partial text`() {
        val reducer = SubtitleReducer()
        reducer.onSourceDelta("Hello ")
        reducer.onSourceDelta("world")
        reducer.onTargetDelta("你好")

        val snapshot = reducer.snapshot()
        assertEquals("Hello world", snapshot.sourcePartial)
        assertEquals("你好", snapshot.targetPartial)
    }

    @Test
    fun `commit moves partials to history and clears buffers`() {
        val reducer = SubtitleReducer()
        reducer.onSourceDelta("Good morning")
        reducer.onTargetDelta("早上好")
        reducer.commitCurrent()

        val snapshot = reducer.snapshot()
        assertEquals(1, snapshot.history.size)
        assertEquals("Good morning", snapshot.history.first().source)
        assertEquals("早上好", snapshot.history.first().target)
        assertTrue(snapshot.sourcePartial.isEmpty())
        assertTrue(snapshot.targetPartial.isEmpty())
    }

    @Test
    fun `blank commit is ignored`() {
        val reducer = SubtitleReducer()
        reducer.commitCurrent()
        assertTrue(reducer.snapshot().history.isEmpty())
    }

    @Test
    fun `history is capped`() {
        val reducer = SubtitleReducer()
        repeat(SubtitleReducer.MAX_HISTORY + 3) { index ->
            reducer.onSourceDelta("s$index")
            reducer.onTargetDelta("t$index")
            reducer.commitCurrent()
        }
        assertEquals(SubtitleReducer.MAX_HISTORY, reducer.snapshot().history.size)
        assertEquals("s3", reducer.snapshot().history.first().source)
    }
}


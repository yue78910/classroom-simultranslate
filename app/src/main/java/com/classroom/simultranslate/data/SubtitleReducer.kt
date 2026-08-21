package com.classroom.simultranslate.data

/**
 * Merges streaming source/target transcript deltas into a stable subtitle state.
 * Delays are handled by the coordinator; this class only owns text state.
 */
class SubtitleReducer {
    private val completed = ArrayDeque<SubtitlePair>()
    private var sourceBuffer = ""
    private var targetBuffer = ""

    fun onSourceDelta(delta: String) {
        if (delta.isNotBlank()) sourceBuffer += delta
    }

    fun onSourceFinal(text: String) {
        sourceBuffer = text.ifBlank { sourceBuffer }
    }

    fun onTargetDelta(delta: String) {
        if (delta.isNotBlank()) targetBuffer += delta
    }

    fun onTargetFinal(text: String) {
        targetBuffer = text.ifBlank { targetBuffer }
    }

    fun commitCurrent() {
        val source = sourceBuffer.trim()
        val target = targetBuffer.trim()
        if (source.isEmpty() && target.isEmpty()) return
        completed.addLast(SubtitlePair(source, target))
        if (completed.size > MAX_HISTORY) completed.removeFirst()
        sourceBuffer = ""
        targetBuffer = ""
    }

    fun snapshot(): SubtitleSnapshot = SubtitleSnapshot(
        sourcePartial = sourceBuffer,
        targetPartial = targetBuffer,
        history = completed.toList(),
    )

    fun clear() {
        completed.clear()
        sourceBuffer = ""
        targetBuffer = ""
    }

    companion object {
        const val MAX_HISTORY = 6
    }
}


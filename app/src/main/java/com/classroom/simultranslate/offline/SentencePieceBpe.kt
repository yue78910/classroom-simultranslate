package com.classroom.simultranslate.offline

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

/**
 * Minimal pure-Kotlin SentencePiece BPE model reader/encoder used by the
 * offline NLLB translation pipeline. It parses the protobuf model file with a
 * small hand-written wire-format reader, so the app does not need a native
 * SentencePiece library on Android.
 */
class SentencePieceBpe private constructor(
    private val pieces: List<PieceInfo>,
    private val ids: Map<String, Int>,
    private val scores: Map<String, Float>,
) {
    data class PieceInfo(
        val piece: String,
        val type: Int,
        val score: Float,
    )

    fun idOf(piece: String): Int? = ids[piece]

    fun encode(text: String, languageToken: String? = null, addEos: Boolean = true): IntArray {
        val tokens = mutableListOf<Int>()
        languageToken?.let {
            tokens += ids[it] ?: ids["<unk>"] ?: 0
        }

        val normalized = normalize(text)
        val marked = "▁" + normalized.replace(" ", "▁")
        val symbols = mutableListOf<String>()
        marked.forEach { ch ->
            val char = ch.toString()
            if (ids.containsKey(char)) {
                symbols += char
            } else {
                symbols += byteFallback(char)
            }
        }
        merge(symbols)

        symbols.forEach { symbol ->
            tokens += ids[symbol] ?: ids["<unk>"] ?: 0
        }
        if (addEos) {
            ids["</s>"]?.let { tokens += it }
        }
        return tokens.toIntArray()
    }

    fun decode(tokenIds: IntArray): String {
        val out = StringBuilder()
        val pendingBytes = ByteArrayOutputStream()
        fun flushBytes() {
            if (pendingBytes.size() > 0) {
                out.append(String(pendingBytes.toByteArray(), Charsets.UTF_8))
                pendingBytes.reset()
            }
        }
        tokenIds.forEach { id ->
            val info = pieces.getOrNull(id) ?: return@forEach
            val piece = info.piece
            when {
                piece.isEmpty() || piece == "<unk>" -> return@forEach
                info.type == TYPE_CONTROL || info.type == TYPE_UNUSED || info.type == TYPE_USER_DEFINED ->
                    return@forEach
                piece.startsWith("<0x") && piece.endsWith(">") -> {
                    val hex = piece.removePrefix("<0x").removeSuffix(">")
                    val byte = hex.toInt(16).toByte()
                    pendingBytes.write(byte.toInt())
                }
                else -> {
                    flushBytes()
                    out.append(piece.replace(META_SPACE, " "))
                }
            }
        }
        flushBytes()
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun merge(symbols: MutableList<String>) {
        while (symbols.size > 1) {
            var bestScore = Float.NEGATIVE_INFINITY
            var bestIndex = -1
            for (i in 0 until symbols.size - 1) {
                val combined = symbols[i] + symbols[i + 1]
                val score = scores[combined] ?: continue
                if (score > bestScore) {
                    bestScore = score
                    bestIndex = i
                }
            }
            if (bestIndex < 0) break
            symbols[bestIndex] = symbols[bestIndex] + symbols[bestIndex + 1]
            symbols.removeAt(bestIndex + 1)
        }
    }

    private fun byteFallback(char: String): List<String> {
        val bytes = char.toByteArray(Charsets.UTF_8)
        return bytes.map { byte ->
            val upper = "<0x%02X>".format(byte.toInt() and 0xFF)
            val lower = upper.lowercase()
            when {
                ids.containsKey(upper) -> upper
                ids.containsKey(lower) -> lower
                else -> "<unk>"
            }
        }
    }

    companion object {
        private const val META_SPACE = "▁"
        private const val TYPE_NORMAL = 1
        private const val TYPE_UNKNOWN = 2
        private const val TYPE_CONTROL = 3
        private const val TYPE_USER_DEFINED = 4
        private const val TYPE_UNUSED = 5
        private const val TYPE_BYTE = 6

        fun load(file: File): SentencePieceBpe {
            val infos = parseModel(file.readBytes())
            val ids = mutableMapOf<String, Int>()
            infos.forEachIndexed { index, info ->
                if (info.piece.isNotEmpty()) ids[info.piece] = index
            }
            val scores = infos.associate { it.piece to it.score }
            return SentencePieceBpe(infos, ids, scores)
        }

        private fun normalize(text: String): String {
            val nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC)
            return nfkc.replace(Regex("\\s+"), " ").trim()
        }

        private fun parseModel(bytes: ByteArray): List<PieceInfo> {
            val result = mutableListOf<PieceInfo>()
            var pos = 0
            while (pos < bytes.size) {
                val (tag, next) = readVarint(bytes, pos)
                pos = next
                val field = (tag ushr 3).toInt()
                val wire = (tag and 7).toInt()
                if (field == 1 && wire == 2) {
                    val (length, lenNext) = readVarint(bytes, pos)
                    pos = lenNext
                    val end = pos + length.toInt()
                    var piece = ""
                    var score = 0f
                    var type = TYPE_NORMAL
                    while (pos < end) {
                        val (innerTag, innerNext) = readVarint(bytes, pos)
                        pos = innerNext
                        val innerField = (innerTag ushr 3).toInt()
                        val innerWire = (innerTag and 7).toInt()
                        when (innerField) {
                            1 -> {
                                require(innerWire == 2) { "unexpected piece field wire" }
                                val (len, valueNext) = readVarint(bytes, pos)
                                pos = valueNext
                                piece = String(bytes, pos, len.toInt(), Charsets.UTF_8)
                                pos += len.toInt()
                            }
                            2 -> {
                                require(innerWire == 5) { "unexpected score field wire" }
                                score = ByteBuffer.wrap(bytes, pos, 4)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .float
                                pos += 4
                            }
                            3 -> {
                                require(innerWire == 0) { "unexpected type field wire" }
                                val (value, valueNext) = readVarint(bytes, pos)
                                type = value.toInt()
                                pos = valueNext
                            }
                            else -> pos = skipField(bytes, pos, innerWire)
                        }
                    }
                    result += PieceInfo(piece, type, score)
                } else {
                    pos = skipField(bytes, pos, wire)
                }
            }
            return result
        }

        private fun skipField(bytes: ByteArray, start: Int, wire: Int): Int {
            var pos = start
            return when (wire) {
                0 -> {
                    val (_, next) = readVarint(bytes, pos)
                    next.toInt()
                }
                1 -> {
                    check(pos + 8 <= bytes.size) { "truncated fixed64" }
                    pos + 8
                }
                2 -> {
                    val (len, next) = readVarint(bytes, pos)
                    val lenInt = len.toInt()
                    check(next + lenInt <= bytes.size) { "truncated bytes" }
                    next + lenInt
                }
                5 -> {
                    check(pos + 4 <= bytes.size) { "truncated fixed32" }
                    pos + 4
                }
                else -> error("unsupported wire type $wire")
            }
        }

        private fun readVarint(bytes: ByteArray, start: Int): Pair<Long, Int> {
            var result = 0L
            var shift = 0
            var pos = start
            while (pos < bytes.size && shift < 64) {
                val byte = bytes[pos].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                pos++
                if (byte and 0x80 == 0) return result to pos
                shift += 7
            }
            error("invalid varint")
        }
    }
}

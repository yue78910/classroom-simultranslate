package com.classroom.simultranslate.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

fun interface AudioChunkListener {
    fun onAudioChunk(samples: FloatArray, rms: Float)
}

class AudioCapture(
    private val chunkSamples: Int = SAMPLE_RATE / 10,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null
    var listener: AudioChunkListener? = null

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (recordJob?.isActive == true) return true
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(chunkSamples * 2 * 2)

        val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }

        audioRecord = record
        recordJob = scope.launch {
            record.startRecording()
            val samples = ShortArray(chunkSamples)
            val floats = FloatArray(chunkSamples)
            while (isActive && record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue
                var sum = 0f
                for (i in 0 until read) {
                    floats[i] = samples[i] / 32768f
                    sum += floats[i] * floats[i]
                }
                val rms = sqrt(sum / read)
                listener?.onAudioChunk(floats.copyOf(read), rms)
            }
        }
        return true
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
    }

    fun release() {
        stop()
        scope.cancel()
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}

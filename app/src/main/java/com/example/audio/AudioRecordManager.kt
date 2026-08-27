package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.debug.LiveDebugLogger
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages real-time microphone capture on Android.
 * Captures 16kHz, 16-bit PCM mono audio using VOICE_COMMUNICATION source with
 * AcousticEchoCanceler and NoiseSuppressor for feedback-free conversation.
 */
class AudioRecordManager(private val context: Context) {

    private val tag = "AudioRecordManager"

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val CHUNK_SIZE_BYTES = 2048 // 1024 samples @ 16kHz ~= 64ms per chunk
    }

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    /**
     * Debug toggle to enable or disable hardware AcousticEchoCanceler and NoiseSuppressor.
     */
    var isAecNsEnabled: Boolean = true
        set(value) {
            field = value
            try {
                echoCanceler?.enabled = value
                noiseSuppressor?.enabled = value
                LiveDebugLogger.log("Hardware AEC & Noise Suppressor ${if (value) "ENABLED" else "DISABLED"}", LiveDebugLogger.LogLevel.INFO)
            } catch (e: Exception) {
                Log.w(tag, "Failed to toggle AEC/NS dynamically: ${e.localizedMessage}")
            }
        }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: (ByteArray, Float) -> Unit,
        onError: (String) -> Unit
    ) {
        LiveDebugLogger.log("[MIC] startRecording() called", LiveDebugLogger.LogLevel.INFO)

        if (isRecording) {
            Log.d(tag, "AudioRecord already recording")
            LiveDebugLogger.log("[MIC] Aborted: already recording", LiveDebugLogger.LogLevel.WARN)
            return
        }

        if (!hasPermission()) {
            val msg = "Microphone permission (RECORD_AUDIO) not granted."
            LiveDebugLogger.log("[MIC] Aborted: missing RECORD_AUDIO permission ($msg)", LiveDebugLogger.LogLevel.ERROR)
            onError(msg)
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            val msg = "AudioRecord hardware configuration not supported (minBufferSize: $minBufferSize)."
            LiveDebugLogger.log("[MIC] Aborted: $msg", LiveDebugLogger.LogLevel.ERROR)
            onError(msg)
            return
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                val msg = "Failed to initialize microphone hardware (state: ${audioRecord?.state})."
                LiveDebugLogger.log("[MIC] Aborted: $msg", LiveDebugLogger.LogLevel.ERROR)
                onError(msg)
                audioRecord?.release()
                audioRecord = null
                return
            }

            val sessionId = audioRecord?.audioSessionId ?: 0
            LiveDebugLogger.log("[MIC] AudioRecord initialized successfully, session ID: $sessionId", LiveDebugLogger.LogLevel.INFO)

            // Explicitly enable hardware / platform Acoustic Echo Canceler and Noise Suppressor if enabled
            if (sessionId != 0 && isAecNsEnabled) {
                if (AcousticEchoCanceler.isAvailable()) {
                    try {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        LiveDebugLogger.log("AcousticEchoCanceler enabled on audio session $sessionId", LiveDebugLogger.LogLevel.INFO)
                        Log.i(tag, "AcousticEchoCanceler enabled: ${echoCanceler?.enabled}")
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to create/enable AcousticEchoCanceler", e)
                        LiveDebugLogger.log("AEC warning: ${e.localizedMessage}", LiveDebugLogger.LogLevel.WARN)
                    }
                } else {
                    LiveDebugLogger.log("Hardware AcousticEchoCanceler is not supported on this device", LiveDebugLogger.LogLevel.WARN)
                }

                if (NoiseSuppressor.isAvailable()) {
                    try {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        LiveDebugLogger.log("NoiseSuppressor enabled on audio session $sessionId", LiveDebugLogger.LogLevel.INFO)
                        Log.i(tag, "NoiseSuppressor enabled: ${noiseSuppressor?.enabled}")
                    } catch (e: Exception) {
                        Log.w(tag, "Failed to create/enable NoiseSuppressor", e)
                        LiveDebugLogger.log("NoiseSuppressor warning: ${e.localizedMessage}", LiveDebugLogger.LogLevel.WARN)
                    }
                } else {
                    LiveDebugLogger.log("Hardware NoiseSuppressor is not supported on this device", LiveDebugLogger.LogLevel.WARN)
                }
            } else if (!isAecNsEnabled) {
                LiveDebugLogger.log("Hardware AEC & Noise Suppressor bypassed by debug setting", LiveDebugLogger.LogLevel.INFO)
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(tag, "AudioRecord started: VOICE_COMMUNICATION, 16kHz 16-bit Mono (AEC/NS: $isAecNsEnabled)")
            LiveDebugLogger.log("Microphone recording started (VOICE_COMMUNICATION, 16kHz Mono, AEC/NS: $isAecNsEnabled)", LiveDebugLogger.LogLevel.INFO)

            recordingJob = scope.launch(Dispatchers.IO) {
                val audioBuffer = ByteArray(CHUNK_SIZE_BYTES)
                var chunkCount = 0

                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1

                    if (readBytes > 0) {
                        // Calculate RMS Amplitude for volume/audio visualization
                        val rms = calculateRms(audioBuffer, readBytes)
                        chunkCount++
                        if (chunkCount % 20 == 0) {
                            val rmsFormatted = String.format(java.util.Locale.US, "%.2f", rms)
                            LiveDebugLogger.log("[MIC LEVEL] RMS: $rmsFormatted", LiveDebugLogger.LogLevel.DATA)
                        }
                        val chunkCopy = audioBuffer.copyOf(readBytes)
                        onAudioChunk(chunkCopy, rms)
                    } else if (readBytes < 0) {
                        Log.e(tag, "Error reading from AudioRecord: $readBytes")
                        LiveDebugLogger.log("AudioRecord read error: $readBytes", LiveDebugLogger.LogLevel.WARN)
                        delay(20)
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(tag, "SecurityException starting AudioRecord", se)
            val msg = "Permission denied: ${se.localizedMessage}"
            LiveDebugLogger.log("Mic error: $msg", LiveDebugLogger.LogLevel.ERROR)
            onError(msg)
            stopRecording()
        } catch (e: Exception) {
            Log.e(tag, "Exception starting AudioRecord", e)
            val msg = "Mic error: ${e.localizedMessage}"
            LiveDebugLogger.log("Mic error: $msg", LiveDebugLogger.LogLevel.ERROR)
            onError(msg)
            stopRecording()
        }
    }

    private fun calculateRms(buffer: ByteArray, length: Int): Float {
        if (length < 2) return 0.0f
        var sumSquares = 0.0
        val sampleCount = length / 2
        for (i in 0 until length - 1 step 2) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortSample = sample.toShort().toFloat()
            sumSquares += (shortSample * shortSample)
        }
        val rms = sqrt(sumSquares / sampleCount).toFloat()
        // Normalize 0 to 1 range (max short is 32767)
        return (rms / 32767.0f).coerceIn(0.0f, 1.0f)
    }

    fun stopRecording() {
        if (isRecording) {
            LiveDebugLogger.log("Microphone recording stopped", LiveDebugLogger.LogLevel.INFO)
        }
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            echoCanceler?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AcousticEchoCanceler", e)
        } finally {
            echoCanceler = null
        }

        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error releasing NoiseSuppressor", e)
        } finally {
            noiseSuppressor = null
        }

        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
            Log.i(tag, "AudioRecord stopped and released")
        }
    }
}

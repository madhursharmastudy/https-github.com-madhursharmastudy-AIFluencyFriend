package com.example.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Manages real-time microphone capture on Android.
 * Captures 16kHz, 16-bit PCM mono audio required by the Gemini Live API.
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
    private var recordingJob: Job? = null
    private var isRecording = false

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
        if (isRecording) {
            Log.d(tag, "AudioRecord already recording")
            return
        }

        if (!hasPermission()) {
            onError("Microphone permission (RECORD_AUDIO) not granted.")
            return
        }

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError("AudioRecord hardware configuration not supported.")
            return
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Failed to initialize microphone hardware.")
                audioRecord?.release()
                audioRecord = null
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            Log.i(tag, "AudioRecord started: 16kHz 16-bit Mono")

            recordingJob = scope.launch(Dispatchers.IO) {
                val audioBuffer = ByteArray(CHUNK_SIZE_BYTES)

                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: -1

                    if (readBytes > 0) {
                        // Calculate RMS Amplitude for volume/audio visualization
                        val rms = calculateRms(audioBuffer, readBytes)
                        val chunkCopy = audioBuffer.copyOf(readBytes)
                        onAudioChunk(chunkCopy, rms)
                    } else if (readBytes < 0) {
                        Log.e(tag, "Error reading from AudioRecord: $readBytes")
                        delay(20)
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(tag, "SecurityException starting AudioRecord", se)
            onError("Permission denied: ${se.localizedMessage}")
            stopRecording()
        } catch (e: Exception) {
            Log.e(tag, "Exception starting AudioRecord", e)
            onError("Mic error: ${e.localizedMessage}")
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
        isRecording = false
        recordingJob?.cancel()
        recordingJob = null

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

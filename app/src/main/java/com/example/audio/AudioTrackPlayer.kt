package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages low-latency real-time PCM audio playback using AudioTrack.
 * Default output is 24kHz 16-bit PCM mono (the format streamed by Gemini Multimodal Live API).
 */
class AudioTrackPlayer(
    private val sampleRate: Int = 24000
) {
    private val tag = "AudioTrackPlayer"

    private var audioTrack: AudioTrack? = null
    private val audioQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val isPlaying = AtomicBoolean(false)
    private val isRunning = AtomicBoolean(false)

    var onPlaybackStarted: (() -> Unit)? = null
    var onPlaybackCompleted: (() -> Unit)? = null

    fun initialize(scope: CoroutineScope) {
        if (isRunning.get()) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = (minBufferSize * 4).coerceAtLeast(8192)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            isRunning.set(true)

            playbackJob = scope.launch(Dispatchers.IO) {
                while (isActive && isRunning.get()) {
                    val chunk = audioQueue.poll()
                    if (chunk != null && chunk.isNotEmpty()) {
                        if (!isPlaying.get()) {
                            isPlaying.set(true)
                            withContext(Dispatchers.Main) {
                                onPlaybackStarted?.invoke()
                            }
                        }

                        var offset = 0
                        while (offset < chunk.size && isRunning.get()) {
                            val written = audioTrack?.write(
                                chunk,
                                offset,
                                chunk.size - offset,
                                AudioTrack.WRITE_BLOCKING
                            ) ?: -1

                            if (written > 0) {
                                offset += written
                            } else {
                                break
                            }
                        }
                    } else {
                        if (isPlaying.get()) {
                            // Queue has drained
                            delay(100) // allow short buffer drain
                            if (audioQueue.isEmpty() && isPlaying.get()) {
                                isPlaying.set(false)
                                withContext(Dispatchers.Main) {
                                    onPlaybackCompleted?.invoke()
                                }
                            }
                        }
                        delay(10)
                    }
                }
            }

            Log.i(tag, "AudioTrackPlayer initialized @ ${sampleRate}Hz")
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize AudioTrackPlayer", e)
        }
    }

    fun enqueueAudio(audioBytes: ByteArray) {
        if (!isRunning.get() || audioBytes.isEmpty()) return
        audioQueue.offer(audioBytes)
    }

    fun stopAndFlush() {
        audioQueue.clear()
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.pause()
                    it.flush()
                    it.play()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error flushing AudioTrack", e)
        }
        if (isPlaying.getAndSet(false)) {
            onPlaybackCompleted?.invoke()
        }
    }

    fun release() {
        isRunning.set(false)
        isPlaying.set(false)
        audioQueue.clear()
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
            Log.i(tag, "AudioTrackPlayer released")
        }
    }
}

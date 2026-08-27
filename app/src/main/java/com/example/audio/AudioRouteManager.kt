package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.debug.LiveDebugLogger

/**
 * Manages audio routing for bidirectional voice communication.
 * Configures AudioManager.MODE_IN_COMMUNICATION and forces loudspeaker output
 * so real-time AI voice playback is routed to the main device speaker rather than the earpiece.
 */
class AudioRouteManager(private val context: Context) {
    private val tag = "AudioRouteManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var isCallModeActive = false

    /**
     * Sets AudioManager to MODE_IN_COMMUNICATION and routes output to the built-in speaker.
     */
    fun activateSpeakerphoneCommunication() {
        val am = audioManager ?: run {
            Log.w(tag, "AudioManager system service is not available")
            return
        }

        try {
            // 1. Set mode to MODE_IN_COMMUNICATION for two-way VoIP / interactive live audio
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // 2. Explicitly route audio output to the built-in loudspeaker
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val availableDevices = am.availableCommunicationDevices
                val speakerDevice = availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speakerDevice != null) {
                    val success = am.setCommunicationDevice(speakerDevice)
                    Log.i(tag, "setCommunicationDevice(TYPE_BUILTIN_SPEAKER) returned: $success")
                    LiveDebugLogger.log("Audio routed to built-in loudspeaker (API 31+ setCommunicationDevice: $success)", LiveDebugLogger.LogLevel.INFO)
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = true
                    Log.i(tag, "Built-in speaker device not found in communication devices list, fallback to isSpeakerphoneOn=true")
                    LiveDebugLogger.log("Loudspeaker forced via isSpeakerphoneOn=true (fallback)", LiveDebugLogger.LogLevel.INFO)
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                Log.i(tag, "Speakerphone set to true via isSpeakerphoneOn")
                LiveDebugLogger.log("Audio routed to speakerphone (MODE_IN_COMMUNICATION)", LiveDebugLogger.LogLevel.INFO)
            }

            isCallModeActive = true
            Log.i(tag, "AudioRouteManager activated: mode=${am.mode}, speakerphoneOn=${@Suppress("DEPRECATION") am.isSpeakerphoneOn}")
        } catch (e: Exception) {
            Log.e(tag, "Failed to configure AudioManager for speakerphone communication", e)
            LiveDebugLogger.log("Audio routing error: ${e.localizedMessage}", LiveDebugLogger.LogLevel.WARN)
        }
    }

    /**
     * Resets AudioManager back to MODE_NORMAL and releases speakerphone / communication devices.
     */
    fun resetToNormal() {
        val am = audioManager ?: return
        if (!isCallModeActive) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
                Log.i(tag, "Communication device cleared")
            }

            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = false
            am.mode = AudioManager.MODE_NORMAL
            isCallModeActive = false

            Log.i(tag, "AudioRouteManager reset: mode=${am.mode}, speakerphone=false")
            LiveDebugLogger.log("Audio route reset to MODE_NORMAL (speakerphone off)", LiveDebugLogger.LogLevel.INFO)
        } catch (e: Exception) {
            Log.e(tag, "Error resetting AudioManager mode to normal", e)
            LiveDebugLogger.log("Audio route reset error: ${e.localizedMessage}", LiveDebugLogger.LogLevel.WARN)
        }
    }
}

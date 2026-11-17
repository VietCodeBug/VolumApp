package com.example.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.data.OverlayPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.roundToInt

data class VolumeSnapshot(
    val currentVolume: Int,
    val minVolume: Int,
    val maxVolume: Int
) {
    val isMuted: Boolean
        get() = currentVolume <= minVolume

    val percentage: Int
        get() {
            val span = max(1, maxVolume - minVolume)
            return (((currentVolume - minVolume).toFloat() / span.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }
}

class DeviceVolumeController(
    context: Context,
    private val preferences: OverlayPreferences = OverlayPreferences(context)
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _volumeState = MutableStateFlow(readCurrentSnapshot())
    val volumeState: StateFlow<VolumeSnapshot> = _volumeState.asStateFlow()

    private var receiverRegistered = false
    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVolumeState()
        }
    }

    fun readCurrentSnapshot(): VolumeSnapshot {
        val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(0)
        } else {
            0
        }
        val maxVol = runCatching {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(15).coerceAtLeast(minVol + 1)

        val currentVol = runCatching {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }.getOrDefault(minVol).coerceIn(minVol, maxVol)

        if (currentVol > minVol) {
            preferences.saveLastNonZeroVolume(currentVol)
        }

        return VolumeSnapshot(
            currentVolume = currentVol,
            minVolume = minVol,
            maxVolume = maxVol
        )
    }

    fun refreshVolumeState(): VolumeSnapshot {
        val snapshot = readCurrentSnapshot()
        _volumeState.value = snapshot
        return snapshot
    }

    /**
     * Adjusts media volume by [delta] steps (e.g. +1 or -1), strictly clamped between [minVolume, maxVolume].
     */
    fun adjustVolumeByStep(delta: Int): VolumeSnapshot {
        val current = readCurrentSnapshot()
        val target = (current.currentVolume + delta).coerceIn(current.minVolume, current.maxVolume)
        return setStreamVolumeInternal(target, current.minVolume, current.maxVolume)
    }

    /**
     * Sets media volume to an exact [targetVolume], strictly clamped between [minVolume, maxVolume].
     */
    fun setExactVolume(targetVolume: Int): VolumeSnapshot {
        val current = readCurrentSnapshot()
        val clampedTarget = targetVolume.coerceIn(current.minVolume, current.maxVolume)
        return setStreamVolumeInternal(clampedTarget, current.minVolume, current.maxVolume)
    }

    /**
     * Toggles quick mute/unmute for media volume, restoring the previous non-zero volume when unmuted.
     */
    fun toggleMute(): VolumeSnapshot {
        val current = readCurrentSnapshot()
        return if (current.currentVolume > current.minVolume) {
            preferences.saveLastNonZeroVolume(current.currentVolume)
            setStreamVolumeInternal(current.minVolume, current.minVolume, current.maxVolume)
        } else {
            val savedNonZero = preferences.readConfig().lastNonZeroVolume
            val fallbackRestore = max(current.minVolume + 1, (current.maxVolume * 0.35f).roundToInt())
            val restoreTarget = if (savedNonZero > current.minVolume) {
                savedNonZero.coerceIn(current.minVolume + 1, current.maxVolume)
            } else {
                fallbackRestore.coerceIn(current.minVolume + 1, current.maxVolume)
            }
            setStreamVolumeInternal(restoreTarget, current.minVolume, current.maxVolume)
        }
    }

    private fun setStreamVolumeInternal(target: Int, minVol: Int, maxVol: Int): VolumeSnapshot {
        val safeTarget = target.coerceIn(minVol, maxVol)
        val result = runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, safeTarget, 0)
        }
        result.onFailure { error ->
            Log.e(TAG, "setStreamVolume failed for target=$safeTarget", error)
        }
        val observed = refreshVolumeState()
        if (result.isSuccess && observed.currentVolume != safeTarget) {
            // A vendor audio service may reject or delay a background/overlay volume request
            // without throwing. A follow-up read is done by the overlay after the gesture.
            Log.w(
                TAG,
                "Volume request not yet reflected: target=$safeTarget, observed=${observed.currentVolume}"
            )
        }
        // Persist the observed value (readCurrentSnapshot does this), not a rejected target.
        return observed
    }

    fun startListeningVolumeChanges() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(volumeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                appContext.registerReceiver(volumeReceiver, filter)
            }
            receiverRegistered = true
        }
    }

    fun stopListeningVolumeChanges() {
        if (!receiverRegistered) return
        runCatching {
            appContext.unregisterReceiver(volumeReceiver)
        }
        receiverRegistered = false
    }

    companion object {
        private const val TAG = "FloatingVolumeAudio"
        const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
    }
}

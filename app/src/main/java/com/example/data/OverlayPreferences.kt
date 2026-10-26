package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

enum class OverlayDisplayMode(val storageKey: String) {
    COMPACT_BAR("COMPACT_BAR"),
    COLLAPSED_BUBBLE("COLLAPSED_BUBBLE");

    companion object {
        fun fromKey(key: String?): OverlayDisplayMode {
            return entries.firstOrNull { it.storageKey == key } ?: COMPACT_BAR
        }
    }
}

data class OverlayConfig(
    val userEnabled: Boolean = false,
    val displayMode: OverlayDisplayMode = OverlayDisplayMode.COMPACT_BAR,
    val positionX: Int = DEFAULT_POSITION_UNSET,
    val positionY: Int = DEFAULT_POSITION_UNSET,
    val snapToEdge: Boolean = true,
    val sizePercent: Int = DEFAULT_SIZE_PERCENT,
    val opacityPercent: Int = DEFAULT_OPACITY_PERCENT,
    val lastNonZeroVolume: Int = -1
) {
    val scaleFactor: Float
        get() = (sizePercent.coerceIn(MIN_SIZE_PERCENT, MAX_SIZE_PERCENT)) / 100f

    val alphaValue: Float
        get() = (opacityPercent.coerceIn(MIN_OPACITY_PERCENT, MAX_OPACITY_PERCENT)) / 100f

    companion object {
        const val DEFAULT_POSITION_UNSET = -1
        const val MIN_SIZE_PERCENT = 75
        const val MAX_SIZE_PERCENT = 140
        const val DEFAULT_SIZE_PERCENT = 100

        const val MIN_OPACITY_PERCENT = 35
        const val MAX_OPACITY_PERCENT = 100
        const val DEFAULT_OPACITY_PERCENT = 90
    }
}

class OverlayPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _configFlow = MutableStateFlow(readConfig())
    val configFlow: StateFlow<OverlayConfig> = _configFlow.asStateFlow()

    fun readConfig(): OverlayConfig {
        return OverlayConfig(
            userEnabled = prefs.getBoolean(KEY_USER_ENABLED, false),
            displayMode = OverlayDisplayMode.fromKey(
                prefs.getString(KEY_DISPLAY_MODE, OverlayDisplayMode.COMPACT_BAR.storageKey)
            ),
            positionX = prefs.getInt(KEY_POSITION_X, OverlayConfig.DEFAULT_POSITION_UNSET),
            positionY = prefs.getInt(KEY_POSITION_Y, OverlayConfig.DEFAULT_POSITION_UNSET),
            snapToEdge = prefs.getBoolean(KEY_SNAP_TO_EDGE, true),
            sizePercent = prefs.getInt(KEY_SIZE_PERCENT, OverlayConfig.DEFAULT_SIZE_PERCENT)
                .coerceIn(OverlayConfig.MIN_SIZE_PERCENT, OverlayConfig.MAX_SIZE_PERCENT),
            opacityPercent = prefs.getInt(KEY_OPACITY_PERCENT, OverlayConfig.DEFAULT_OPACITY_PERCENT)
                .coerceIn(OverlayConfig.MIN_OPACITY_PERCENT, OverlayConfig.MAX_OPACITY_PERCENT),
            lastNonZeroVolume = prefs.getInt(KEY_LAST_NON_ZERO_VOLUME, -1)
        )
    }

    fun isUserEnabled(): Boolean = prefs.getBoolean(KEY_USER_ENABLED, false)

    fun setUserEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_USER_ENABLED, enabled).apply()
        refreshFlow()
    }

    fun setDisplayMode(mode: OverlayDisplayMode) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode.storageKey).apply()
        refreshFlow()
    }

    fun setSnapToEdge(snap: Boolean) {
        prefs.edit().putBoolean(KEY_SNAP_TO_EDGE, snap).apply()
        refreshFlow()
    }

    fun setSizePercent(percent: Int) {
        val clamped = percent.coerceIn(OverlayConfig.MIN_SIZE_PERCENT, OverlayConfig.MAX_SIZE_PERCENT)
        prefs.edit().putInt(KEY_SIZE_PERCENT, clamped).apply()
        refreshFlow()
    }

    fun setOpacityPercent(percent: Int) {
        val clamped = percent.coerceIn(OverlayConfig.MIN_OPACITY_PERCENT, OverlayConfig.MAX_OPACITY_PERCENT)
        prefs.edit().putInt(KEY_OPACITY_PERCENT, clamped).apply()
        refreshFlow()
    }

    fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_POSITION_X, x)
            .putInt(KEY_POSITION_Y, y)
            .apply()
        refreshFlow()
    }

    fun resetPosition() {
        prefs.edit()
            .putInt(KEY_POSITION_X, OverlayConfig.DEFAULT_POSITION_UNSET)
            .putInt(KEY_POSITION_Y, OverlayConfig.DEFAULT_POSITION_UNSET)
            .apply()
        refreshFlow()
    }

    fun saveLastNonZeroVolume(volume: Int) {
        if (volume > 0) {
            prefs.edit().putInt(KEY_LAST_NON_ZERO_VOLUME, volume).apply()
            refreshFlow()
        }
    }

    fun refreshFlow() {
        _configFlow.value = readConfig()
    }

    companion object {
        const val PREFS_NAME = "floating_volume_prefs"
        private const val KEY_USER_ENABLED = "user_enabled"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private const val KEY_SNAP_TO_EDGE = "snap_to_edge"
        private const val KEY_SIZE_PERCENT = "size_percent"
        private const val KEY_OPACITY_PERCENT = "opacity_percent"
        private const val KEY_LAST_NON_ZERO_VOLUME = "last_non_zero_volume"

        /**
         * Clamps (x, y) so the overlay view stays strictly inside the visible screen bounds
         * [margin .. screenWidth - viewWidth - margin] and [margin .. screenHeight - viewHeight - margin].
         */
        fun clampPositionToBounds(
            x: Int,
            y: Int,
            viewWidth: Int,
            viewHeight: Int,
            screenWidth: Int,
            screenHeight: Int,
            edgeMarginPx: Int = 8,
            snapToEdge: Boolean = false
        ): Pair<Int, Int> {
            val safeScreenW = max(1, screenWidth)
            val safeScreenH = max(1, screenHeight)
            val safeViewW = viewWidth.coerceIn(1, safeScreenW)
            val safeViewH = viewHeight.coerceIn(1, safeScreenH)

            val minX = min(edgeMarginPx, max(0, safeScreenW - safeViewW))
            val maxX = max(minX, safeScreenW - safeViewW - edgeMarginPx)

            val minY = min(edgeMarginPx, max(0, safeScreenH - safeViewH))
            val maxY = max(minY, safeScreenH - safeViewH - edgeMarginPx)

            val resolvedX = if (x == OverlayConfig.DEFAULT_POSITION_UNSET) {
                maxX
            } else if (snapToEdge) {
                val centerX = x + safeViewW / 2
                if (centerX < safeScreenW / 2) minX else maxX
            } else {
                x.coerceIn(minX, maxX)
            }

            val resolvedY = if (y == OverlayConfig.DEFAULT_POSITION_UNSET) {
                ((safeScreenH - safeViewH) * 0.42f).toInt().coerceIn(minY, maxY)
            } else {
                y.coerceIn(minY, maxY)
            }

            return resolvedX to resolvedY
        }
    }
}

package com.example.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DeviceVolumeController
import com.example.audio.VolumeSnapshot
import com.example.data.OverlayConfig
import com.example.data.OverlayDisplayMode
import com.example.data.OverlayPreferences
import com.example.service.VolumeOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SystemPermissionState(
    val canDrawOverlays: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val isBatteryUnrestricted: Boolean = false
)

data class MainUiState(
    val isServiceActuallyRunning: Boolean = false,
    val isOverlayAttached: Boolean = false,
    val permissions: SystemPermissionState = SystemPermissionState(),
    val config: OverlayConfig = OverlayConfig(),
    val volume: VolumeSnapshot = VolumeSnapshot(currentVolume = 5, minVolume = 0, maxVolume = 15)
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Context = application.applicationContext
    val preferences = OverlayPreferences(appContext)
    val volumeController = DeviceVolumeController(appContext, preferences)

    private val _permissionState = MutableStateFlow(readSystemPermissions(appContext))

    val uiState: StateFlow<MainUiState> = combine(
        VolumeOverlayService.isServiceRunningFlow,
        VolumeOverlayService.isOverlayAttachedFlow,
        _permissionState,
        preferences.configFlow,
        volumeController.volumeState
    ) { isRunning, isAttached, perms, config, vol ->
        MainUiState(
            isServiceActuallyRunning = isRunning && isAttached,
            isOverlayAttached = isAttached,
            permissions = perms,
            config = config,
            volume = vol
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(
            isServiceActuallyRunning = VolumeOverlayService.isServiceRunningFlow.value &&
                VolumeOverlayService.isOverlayAttachedFlow.value,
            isOverlayAttached = VolumeOverlayService.isOverlayAttachedFlow.value,
            permissions = _permissionState.value,
            config = preferences.readConfig(),
            volume = volumeController.readCurrentSnapshot()
        )
    )

    init {
        volumeController.startListeningVolumeChanges()
        refreshSystemState()
    }

    fun refreshSystemState() {
        val perms = readSystemPermissions(appContext)
        _permissionState.value = perms
        preferences.refreshFlow()
        volumeController.refreshVolumeState()

        // If overlay permission was revoked while the service was running, stop safely
        if (!perms.canDrawOverlays && VolumeOverlayService.isServiceRunningFlow.value) {
            VolumeOverlayService.stopService(appContext)
        }
    }

    fun startFloatingOverlay(): Boolean {
        val perms = readSystemPermissions(appContext)
        _permissionState.value = perms
        if (!perms.canDrawOverlays) {
            return false
        }
        preferences.setUserEnabled(true)
        VolumeOverlayService.startService(appContext)
        return true
    }

    fun stopFloatingOverlay() {
        VolumeOverlayService.stopService(appContext)
        preferences.refreshFlow()
    }

    fun setDisplayMode(mode: OverlayDisplayMode) {
        preferences.setDisplayMode(mode)
        VolumeOverlayService.notifyConfigUpdated(appContext)
    }

    fun setSnapToEdge(enabled: Boolean) {
        preferences.setSnapToEdge(enabled)
        VolumeOverlayService.notifyConfigUpdated(appContext)
    }

    fun setSizePercent(percent: Int) {
        preferences.setSizePercent(percent)
        VolumeOverlayService.notifyConfigUpdated(appContext)
    }

    fun setOpacityPercent(percent: Int) {
        preferences.setOpacityPercent(percent)
        VolumeOverlayService.notifyConfigUpdated(appContext)
    }

    fun resetOverlayPosition() {
        VolumeOverlayService.resetPosition(appContext)
        preferences.refreshFlow()
    }

    fun expandOverlayPanelNow() {
        VolumeOverlayService.expandPanelNow(appContext)
    }

    fun adjustDeviceVolumeByStep(delta: Int) {
        volumeController.adjustVolumeByStep(delta)
    }

    fun setExactDeviceVolume(target: Int) {
        volumeController.setExactVolume(target)
    }

    fun toggleDeviceMute() {
        volumeController.toggleMute()
    }

    override fun onCleared() {
        volumeController.stopListeningVolumeChanges()
        super.onCleared()
    }

    companion object {
        fun readSystemPermissions(context: Context): SystemPermissionState {
            val canOverlay = VolumeOverlayService.canDrawOverlayWindow(context)
            val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                runCatching {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                }.getOrDefault(true)
            }

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val batteryUnrestricted = runCatching {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
            }.getOrDefault(false)

            return SystemPermissionState(
                canDrawOverlays = canOverlay,
                hasNotificationPermission = hasNotif,
                isBatteryUnrestricted = batteryUnrestricted
            )
        }
    }
}

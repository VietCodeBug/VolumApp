package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.DeviceVolumeController
import com.example.data.OverlayPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Independent started Foreground Service that owns the floating volume overlay via [WindowManager].
 * Continues running when the user swipes the app away from Recent Apps (`android:stopWithTask="false"`).
 */
class VolumeOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var preferences: OverlayPreferences
    private lateinit var volumeController: DeviceVolumeController
    private lateinit var windowManager: WindowManager
    private var overlayViewHolder: OverlayViewHolder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = OverlayPreferences(this)
        volumeController = DeviceVolumeController(this, preferences)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        volumeController.startListeningVolumeChanges()

        serviceScope.launch {
            volumeController.volumeState.collect { snapshot ->
                overlayViewHolder?.updateVolumeUi(snapshot)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        // 1. Explicit Stop action (from MainActivity or Notification "Tắt" button)
        if (action == ACTION_STOP) {
            stopOverlayAndService(clearUserEnabledFlag = true)
            return START_NOT_STICKY
        }

        // 2. When intent is null (system recreated service via START_STICKY), check if user actually wanted it enabled
        if (action == null && !preferences.isUserEnabled()) {
            stopOverlayAndService(clearUserEnabledFlag = false)
            return START_NOT_STICKY
        }

        // 3. Always verify overlay permission before creating or maintaining the overlay window
        if (! canDrawOverlayWindow(this)) {
            preferences.setUserEnabled(false)
            stopOverlayAndService(clearUserEnabledFlag = true)
            return START_NOT_STICKY
        }

        // 4. Mark user enabled when starting or recovering, and immediately promote to Foreground
        preferences.setUserEnabled(true)
        promoteToForeground()

        // 5. Ensure single overlay instance (never create duplicate overlays when opened or started multiple times)
        val holder = overlayViewHolder ?: OverlayViewHolder(
            serviceContext = this,
            windowManager = windowManager,
            volumeController = volumeController,
            preferences = preferences
        ).also { overlayViewHolder = it }

        if (!holder.isAttached) {
            val attached = holder.attachToWindow()
            if (!attached) {
                preferences.setUserEnabled(false)
                stopOverlayAndService(clearUserEnabledFlag = true)
                return START_NOT_STICKY
            }
        }

        when (action) {
            ACTION_UPDATE_CONFIG -> {
                holder.applyConfig(preferences.readConfig())
            }

            ACTION_RESET_POSITION -> {
                preferences.resetPosition()
                holder.clampAndUpdateWindowPosition(snapIfConfigured = true)
            }

            ACTION_EXPAND_PANEL -> {
                holder.expandFullPanel()
            }

            else -> {
                holder.applyConfig(preferences.readConfig())
                holder.updateVolumeUi(volumeController.refreshVolumeState())
            }
        }

        _isServiceRunning.value = true
        _isOverlayAttached.value = holder.isAttached
        return START_STICKY
    }

    /**
     * CRITICAL REQUIREMENT:
     * When the user swipes the app away from Recent Apps, do NOT stop the service or remove the overlay!
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Intentionally preserve the running foreground service and floating overlay window.
        overlayViewHolder?.clampAndUpdateWindowPosition(
            snapIfConfigured = preferences.readConfig().snapToEdge && overlayViewHolder?.isPanelExpanded == false
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Keep the floating volume control inside the visible screen bounds on rotation or display size change
        overlayViewHolder?.let { holder ->
            if (holder.isAttached) {
                holder.clampAndUpdateWindowPosition(
                    snapIfConfigured = !holder.isPanelExpanded && preferences.readConfig().snapToEdge
                )
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        volumeController.stopListeningVolumeChanges()
        overlayViewHolder?.detachFromWindow()
        overlayViewHolder = null
        _isOverlayAttached.value = false
        _isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopOverlayAndService(clearUserEnabledFlag: Boolean) {
        if (clearUserEnabledFlag) {
            preferences.setUserEnabled(false)
        }
        overlayViewHolder?.detachFromWindow()
        overlayViewHolder = null
        _isOverlayAttached.value = false
        _isServiceRunning.value = false
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    private fun promoteToForeground() {
        val notification = buildForegroundNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopServiceIntent = Intent(this, VolumeOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP_SERVICE,
            stopServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_volume_speaker)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_content_active))
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                R.drawable.ic_collapse_panel,
                getString(R.string.notif_action_stop),
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "floating_volume_overlay_channel"
        const val NOTIFICATION_ID = 10042

        private const val REQUEST_CODE_OPEN_APP = 201
        private const val REQUEST_CODE_STOP_SERVICE = 202

        const val ACTION_START = "com.example.service.action.START_OVERLAY"
        const val ACTION_STOP = "com.example.service.action.STOP_OVERLAY"
        const val ACTION_UPDATE_CONFIG = "com.example.service.action.UPDATE_CONFIG"
        const val ACTION_RESET_POSITION = "com.example.service.action.RESET_POSITION"
        const val ACTION_EXPAND_PANEL = "com.example.service.action.EXPAND_PANEL"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunningFlow: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isOverlayAttached = MutableStateFlow(false)
        val isOverlayAttachedFlow: StateFlow<Boolean> = _isOverlayAttached.asStateFlow()

        /**
         * Test override hook for local JVM/Robolectric tests where Settings.canDrawOverlays
         * can be configured deterministically.
         */
        @Volatile
        var overlayPermissionCheckerOverride: ((Context) -> Boolean)? = null

        fun canDrawOverlayWindow(context: Context): Boolean {
            overlayPermissionCheckerOverride?.let { return it(context) }
            return Settings.canDrawOverlays(context)
        }

        fun startService(context: Context) {
            val intent = Intent(context, VolumeOverlayService::class.java).apply {
                action = ACTION_START
            }
            ContextCompatStartForeground(context, intent)
        }

        fun stopService(context: Context) {
            val appCtx = context.applicationContext
            OverlayPreferences(appCtx).setUserEnabled(false)
            if (_isServiceRunning.value) {
                val stopIntent = Intent(appCtx, VolumeOverlayService::class.java).apply {
                    action = ACTION_STOP
                }
                runCatching { appCtx.startService(stopIntent) }
                    .onFailure { appCtx.stopService(Intent(appCtx, VolumeOverlayService::class.java)) }
            } else {
                appCtx.stopService(Intent(appCtx, VolumeOverlayService::class.java))
            }
        }

        fun notifyConfigUpdated(context: Context) {
            if (!_isServiceRunning.value) return
            val intent = Intent(context, VolumeOverlayService::class.java).apply {
                action = ACTION_UPDATE_CONFIG
            }
            runCatching { context.startService(intent) }
        }

        fun resetPosition(context: Context) {
            val prefs = OverlayPreferences(context)
            prefs.resetPosition()
            if (_isServiceRunning.value) {
                val intent = Intent(context, VolumeOverlayService::class.java).apply {
                    action = ACTION_RESET_POSITION
                }
                runCatching { context.startService(intent) }
            }
        }

        fun expandPanelNow(context: Context) {
            if (!_isServiceRunning.value) return
            val intent = Intent(context, VolumeOverlayService::class.java).apply {
                action = ACTION_EXPAND_PANEL
            }
            runCatching { context.startService(intent) }
        }

        private fun ContextCompatStartForeground(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

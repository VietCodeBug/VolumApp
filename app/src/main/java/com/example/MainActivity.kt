package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.FloatingVolumeScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.FloatingVolumeTheme

/**
 * Configuration and permission entry-point Activity.
 * NOTE: This Activity NEVER stops [com.example.service.VolumeOverlayService] or removes the overlay
 * in onStop/onDestroy, ensuring the floating volume buttons survive swiping the app from Recent Apps.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FloatingVolumeTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Refresh permissions, volume, and live service state whenever Activity resumes
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            viewModel.refreshSystemState()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    // Even if the user denies POST_NOTIFICATIONS on Android 13+, we handle it gracefully
                    viewModel.refreshSystemState()
                }

                val overlaySettingsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.refreshSystemState()
                }

                FloatingVolumeScreen(
                    uiState = uiState,
                    onToggleOverlayClick = {
                        if (uiState.isServiceActuallyRunning) {
                            viewModel.stopFloatingOverlay()
                        } else if (!uiState.permissions.canDrawOverlays) {
                            openOverlayPermissionSettings(overlaySettingsLauncher::launch)
                        } else {
                            // Start foreground service immediately; request notification permission on Android 13+ if not yet granted
                            viewModel.startFloatingOverlay()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                !uiState.permissions.hasNotificationPermission
                            ) {
                                runCatching {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        }
                    },
                    onRequestOverlayPermissionClick = {
                        openOverlayPermissionSettings(overlaySettingsLauncher::launch)
                    },
                    onRequestNotificationPermissionClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !uiState.permissions.hasNotificationPermission
                        ) {
                            runCatching {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }.onFailure {
                                openAppNotificationSettings()
                            }
                        } else {
                            openAppNotificationSettings()
                        }
                    },
                    onOpenBatterySettingsClick = {
                        openBatteryOptimizationSettings()
                    },
                    onOpenAppDetailsClick = {
                        openApplicationDetailsSettings()
                    },
                    onDisplayModeChange = viewModel::setDisplayMode,
                    onSnapToEdgeChange = viewModel::setSnapToEdge,
                    onSizePercentChange = viewModel::setSizePercent,
                    onOpacityPercentChange = viewModel::setOpacityPercent,
                    onResetPositionClick = viewModel::resetOverlayPosition,
                    onExpandPanelNowClick = viewModel::expandOverlayPanelNow,
                    onAdjustVolumeStep = viewModel::adjustDeviceVolumeByStep,
                    onSetExactVolume = viewModel::setExactDeviceVolume,
                    onToggleMute = viewModel::toggleDeviceMute
                )
            }
        }
    }

    private fun openOverlayPermissionSettings(launchIntent: (Intent) -> Unit) {
        val packageSpecificIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching {
            launchIntent(packageSpecificIntent)
        }.onFailure {
            val genericOverlayIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            runCatching {
                launchIntent(genericOverlayIntent)
            }.onFailure {
                openApplicationDetailsSettings()
            }
        }
    }

    private fun openAppNotificationSettings() {
        val notifIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching {
            startActivity(notifIntent)
        }.onFailure {
            openApplicationDetailsSettings()
        }
    }

    private fun openBatteryOptimizationSettings() {
        val batteryListIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        runCatching {
            startActivity(batteryListIntent)
        }.onFailure {
            openApplicationDetailsSettings()
        }
    }

    private fun openApplicationDetailsSettings() {
        val detailsIntent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$packageName")
        )
        runCatching {
            startActivity(detailsIntent)
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
    }
}

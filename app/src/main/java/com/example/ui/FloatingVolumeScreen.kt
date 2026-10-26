package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.OverlayConfig
import com.example.data.OverlayDisplayMode
import kotlin.math.roundToInt

@Composable
fun FloatingVolumeScreen(
    uiState: MainUiState,
    onToggleOverlayClick: () -> Unit,
    onRequestOverlayPermissionClick: () -> Unit,
    onRequestNotificationPermissionClick: () -> Unit,
    onOpenBatterySettingsClick: () -> Unit,
    onOpenAppDetailsClick: () -> Unit,
    onDisplayModeChange: (OverlayDisplayMode) -> Unit,
    onSnapToEdgeChange: (Boolean) -> Unit,
    onSizePercentChange: (Int) -> Unit,
    onOpacityPercentChange: (Int) -> Unit,
    onResetPositionClick: () -> Unit,
    onExpandPanelNowClick: () -> Unit,
    onAdjustVolumeStep: (Int) -> Unit,
    onSetExactVolume: (Int) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .testTag("main_scroll_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeroStatusCard(
                        uiState = uiState,
                        onToggleOverlayClick = onToggleOverlayClick,
                        onExpandPanelNowClick = onExpandPanelNowClick
                    )
                }

                item {
                    LiveVolumeTestCard(
                        uiState = uiState,
                        onAdjustVolumeStep = onAdjustVolumeStep,
                        onSetExactVolume = onSetExactVolume,
                        onToggleMute = onToggleMute
                    )
                }

                item {
                    PermissionsAndBatteryCard(
                        permissions = uiState.permissions,
                        onRequestOverlayPermissionClick = onRequestOverlayPermissionClick,
                        onRequestNotificationPermissionClick = onRequestNotificationPermissionClick,
                        onOpenBatterySettingsClick = onOpenBatterySettingsClick,
                        onOpenAppDetailsClick = onOpenAppDetailsClick
                    )
                }

                item {
                    OverlayCustomizationCard(
                        config = uiState.config,
                        isServiceRunning = uiState.isServiceActuallyRunning,
                        onDisplayModeChange = onDisplayModeChange,
                        onSnapToEdgeChange = onSnapToEdgeChange,
                        onSizePercentChange = onSizePercentChange,
                        onOpacityPercentChange = onOpacityPercentChange,
                        onResetPositionClick = onResetPositionClick
                    )
                }

                item {
                    LimitationsAndChecklistCard()
                }
            }
        }
    }
}

@Composable
private fun HeroStatusCard(
    uiState: MainUiState,
    onToggleOverlayClick: () -> Unit,
    onExpandPanelNowClick: () -> Unit
) {
    val isRunning = uiState.isServiceActuallyRunning
    val canOverlay = uiState.permissions.canDrawOverlays

    val statusColor by animateColorAsState(
        targetValue = when {
            isRunning -> Color(0xFF10B981)
            !canOverlay -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(250),
        label = "statusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hero_status_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Text(
                            text = when {
                                isRunning -> stringResource(R.string.status_active_title)
                                !canOverlay -> stringResource(R.string.status_permission_needed_title)
                                else -> stringResource(R.string.status_inactive_title)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                            modifier = Modifier.testTag("service_status_text")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.app_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tactile floating pill preview illustration
                FloatingPillDiagram(
                    isActive = isRunning,
                    displayMode = uiState.config.displayMode
                )
            }

            Text(
                text = when {
                    isRunning -> stringResource(R.string.status_active_desc)
                    !canOverlay -> stringResource(R.string.status_permission_needed_desc)
                    else -> stringResource(R.string.status_inactive_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onToggleOverlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("toggle_overlay_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isRunning) {
                        stringResource(R.string.btn_disable_overlay)
                    } else if (!canOverlay) {
                        stringResource(R.string.btn_grant_overlay)
                    } else {
                        stringResource(R.string.btn_enable_overlay)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = isRunning) {
                OutlinedButton(
                    onClick = onExpandPanelNowClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("expand_panel_now_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.btn_expand_panel_now))
                }
            }
        }
    }
}

@Composable
private fun FloatingPillDiagram(
    isActive: Boolean,
    displayMode: OverlayDisplayMode
) {
    val accent = if (isActive) Color(0xFF38BDF8) else Color(0xFF64748B)
    val bgBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
    )

    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 92.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Outer frame border
            drawRoundRect(
                color = accent.copy(alpha = 0.6f),
                topLeft = Offset(w * 0.18f, h * 0.1f),
                size = Size(w * 0.64f, h * 0.8f),
                cornerRadius = CornerRadius(24f, 24f)
            )
            if (displayMode == OverlayDisplayMode.COMPACT_BAR) {
                // Top + pill circle
                drawCircle(
                    color = accent,
                    radius = w * 0.17f,
                    center = Offset(w * 0.5f, h * 0.32f)
                )
                // Bottom - pill circle
                drawCircle(
                    color = Color(0xFF334155),
                    radius = w * 0.17f,
                    center = Offset(w * 0.5f, h * 0.68f)
                )
            } else {
                // Speaker bubble circle
                drawCircle(
                    color = accent,
                    radius = w * 0.24f,
                    center = Offset(w * 0.5f, h * 0.5f)
                )
            }
        }
    }
}

@Composable
private fun LiveVolumeTestCard(
    uiState: MainUiState,
    onAdjustVolumeStep: (Int) -> Unit,
    onSetExactVolume: (Int) -> Unit,
    onToggleMute: () -> Unit
) {
    val vol = uiState.volume

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_volume_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (vol.isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.section_current_volume_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${vol.currentVolume} / ${vol.maxVolume} (${vol.percentage}%)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("current_volume_badge")
                    )
                }
            }

            Slider(
                value = vol.currentVolume.toFloat(),
                onValueChange = { onSetExactVolume(it.roundToInt()) },
                valueRange = vol.minVolume.toFloat()..vol.maxVolume.toFloat(),
                steps = (vol.maxVolume - vol.minVolume - 1).coerceAtLeast(0),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("device_volume_slider")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjustVolumeStep(-1) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_vol_down_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Remove, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.btn_test_vol_down))
                }

                OutlinedButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("test_vol_mute_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (vol.isMuted) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (vol.isMuted) {
                            stringResource(R.string.btn_test_unmute)
                        } else {
                            stringResource(R.string.btn_test_mute)
                        }
                    )
                }

                Button(
                    onClick = { onAdjustVolumeStep(+1) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("test_vol_up_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.btn_test_vol_up))
                }
            }
        }
    }
}

@Composable
private fun PermissionsAndBatteryCard(
    permissions: SystemPermissionState,
    onRequestOverlayPermissionClick: () -> Unit,
    onRequestNotificationPermissionClick: () -> Unit,
    onOpenBatterySettingsClick: () -> Unit,
    onOpenAppDetailsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.section_permissions_title),
                style = MaterialTheme.typography.titleLarge
            )

            // 1. Overlay Permission Row
            PermissionStatusItem(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.perm_overlay_title),
                subtitle = if (permissions.canDrawOverlays) {
                    stringResource(R.string.perm_overlay_granted)
                } else {
                    stringResource(R.string.perm_overlay_denied)
                },
                isGranted = permissions.canDrawOverlays,
                actionLabel = if (permissions.canDrawOverlays) {
                    stringResource(R.string.btn_manage_overlay)
                } else {
                    stringResource(R.string.btn_grant_overlay)
                },
                buttonTestTag = "grant_overlay_permission_button",
                onActionClick = onRequestOverlayPermissionClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // 2. Notification Permission Row
            PermissionStatusItem(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.perm_notif_title),
                subtitle = if (permissions.hasNotificationPermission) {
                    stringResource(R.string.perm_notif_granted)
                } else {
                    stringResource(R.string.perm_notif_denied)
                },
                isGranted = permissions.hasNotificationPermission,
                actionLabel = stringResource(R.string.btn_grant_notif),
                buttonTestTag = "grant_notification_permission_button",
                onActionClick = onRequestNotificationPermissionClick
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // 3. Background & Battery Optimization Guide
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = if (permissions.isBatteryUnrestricted) {
                        Color(0xFF10B981)
                    } else {
                        Color(0xFFF59E0B)
                    }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.battery_opt_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (permissions.isBatteryUnrestricted) {
                            stringResource(R.string.battery_opt_unrestricted)
                        } else {
                            stringResource(R.string.battery_opt_restricted)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (permissions.isBatteryUnrestricted) {
                            Color(0xFF10B981)
                        } else {
                            Color(0xFFF59E0B)
                        }
                    )
                }
            }

            Text(
                text = stringResource(R.string.battery_opt_guide),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenBatterySettingsClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_battery_settings_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_open_battery_settings))
                }

                OutlinedButton(
                    onClick = onOpenAppDetailsClick,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("open_app_details_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_open_app_details))
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    buttonTestTag: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = onActionClick,
            modifier = Modifier
                .height(48.dp)
                .testTag(buttonTestTag),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = actionLabel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCustomizationCard(
    config: OverlayConfig,
    isServiceRunning: Boolean,
    onDisplayModeChange: (OverlayDisplayMode) -> Unit,
    onSnapToEdgeChange: (Boolean) -> Unit,
    onSizePercentChange: (Int) -> Unit,
    onOpacityPercentChange: (Int) -> Unit,
    onResetPositionClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("customization_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.section_customization_title),
                style = MaterialTheme.typography.titleLarge
            )

            // Display Mode Selection (Compact Bar vs Collapsed Bubble)
            Text(
                text = stringResource(R.string.mode_selection_label),
                style = MaterialTheme.typography.titleMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ModeOptionCard(
                    title = stringResource(R.string.mode_compact_bar),
                    description = stringResource(R.string.mode_compact_bar_desc),
                    selected = config.displayMode == OverlayDisplayMode.COMPACT_BAR,
                    testTag = "mode_compact_bar_button",
                    onClick = { onDisplayModeChange(OverlayDisplayMode.COMPACT_BAR) },
                    modifier = Modifier.weight(1f)
                )
                ModeOptionCard(
                    title = stringResource(R.string.mode_collapsed_bubble),
                    description = stringResource(R.string.mode_collapsed_bubble_desc),
                    selected = config.displayMode == OverlayDisplayMode.COLLAPSED_BUBBLE,
                    testTag = "mode_bubble_button",
                    onClick = { onDisplayModeChange(OverlayDisplayMode.COLLAPSED_BUBBLE) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Edge Snap Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSnapToEdgeChange(!config.snapToEdge) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_snap_to_edge_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.setting_snap_to_edge_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = config.snapToEdge,
                    onCheckedChange = onSnapToEdgeChange,
                    modifier = Modifier.testTag("snap_to_edge_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Size Slider + Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.setting_size_label, config.sizePercent),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = config.sizePercent.toFloat(),
                    onValueChange = { onSizePercentChange(it.roundToInt()) },
                    valueRange = OverlayConfig.MIN_SIZE_PERCENT.toFloat()..OverlayConfig.MAX_SIZE_PERCENT.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("size_slider")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(80, 100, 115, 130).forEach { preset ->
                        FilterChip(
                            selected = config.sizePercent == preset,
                            onClick = { onSizePercentChange(preset) },
                            label = { Text("${preset}%") },
                            modifier = Modifier.testTag("size_preset_$preset")
                        )
                    }
                }
            }

            // Opacity Slider + Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.setting_opacity_label, config.opacityPercent),
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = config.opacityPercent.toFloat(),
                    onValueChange = { onOpacityPercentChange(it.roundToInt()) },
                    valueRange = OverlayConfig.MIN_OPACITY_PERCENT.toFloat()..OverlayConfig.MAX_OPACITY_PERCENT.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("opacity_slider")
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(50, 75, 90, 100).forEach { preset ->
                        FilterChip(
                            selected = config.opacityPercent == preset,
                            onClick = { onOpacityPercentChange(preset) },
                            label = { Text("${preset}%") },
                            colors = FilterChipDefaults.filterChipColors(),
                            modifier = Modifier.testTag("opacity_preset_$preset")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            // Saved Position & Reset Button
            val posXLabel = if (config.positionX == OverlayConfig.DEFAULT_POSITION_UNSET) 0 else config.positionX
            val posYLabel = if (config.positionY == OverlayConfig.DEFAULT_POSITION_UNSET) 0 else config.positionY
            Text(
                text = stringResource(R.string.setting_position_label, posXLabel, posYLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = onResetPositionClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_position_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.btn_reset_position))
            }
        }
    }
}

@Composable
private fun ModeOptionCard(
    title: String,
    description: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (selected) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.AutoMirrored.Filled.VolumeDown
                    },
                    contentDescription = null,
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LimitationsAndChecklistCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("limitations_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.section_limitations_title),
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Text(
                text = stringResource(R.string.limitation_item_recents),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.limitation_item_low_ram),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.limitation_item_force_stop),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.limitation_item_rom),
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )

            Text(
                text = stringResource(R.string.section_checklist_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.checklist_step_1),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.checklist_step_2),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.checklist_step_3),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.checklist_step_4),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

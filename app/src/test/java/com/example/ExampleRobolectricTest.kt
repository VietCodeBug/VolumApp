package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.example.audio.DeviceVolumeController
import com.example.data.OverlayConfig
import com.example.data.OverlayDisplayMode
import com.example.data.OverlayPreferences
import com.example.service.VolumeOverlayService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var preferences: OverlayPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(OverlayPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = OverlayPreferences(context)
        VolumeOverlayService.overlayPermissionCheckerOverride = { true }
    }

    @After
    fun tearDown() {
        VolumeOverlayService.overlayPermissionCheckerOverride = null
    }

    @Test
    fun `app_name resource matches Floating Volume`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Floating Volume", appName)
    }

    @Test
    fun `position clamping keeps overlay inside visible bounds on rotation and snaps to edge`() {
        // Simulate dragging outside right/bottom bounds in portrait (1080x2400)
        val (clampedX, clampedY) = OverlayPreferences.clampPositionToBounds(
            x = 1500,
            y = 2600,
            viewWidth = 120,
            viewHeight = 300,
            screenWidth = 1080,
            screenHeight = 2400,
            edgeMarginPx = 8,
            snapToEdge = false
        )
        assertEquals(1080 - 120 - 8, clampedX)
        assertEquals(2400 - 300 - 8, clampedY)

        // Simulate rotating to landscape (2400x1080) with previous portrait Y = 2092 -> must clamp into [8 .. 772]
        val (rotatedX, rotatedY) = OverlayPreferences.clampPositionToBounds(
            x = clampedX,
            y = clampedY,
            viewWidth = 120,
            viewHeight = 300,
            screenWidth = 2400,
            screenHeight = 1080,
            edgeMarginPx = 8,
            snapToEdge = true
        )
        // Since clampedX (952) + 60 = 1012 < 1200 (half of 2400), snapping to nearest edge chooses left margin (8)
        assertEquals(8, rotatedX)
        assertEquals(1080 - 300 - 8, rotatedY)
    }

    @Test
    fun `device volume controller respects min and max bounds and supports mute restore`() {
        val controller = DeviceVolumeController(context, preferences)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // Set to a known mid-level volume
        val midLevel = (maxVol / 2).coerceAtLeast(1)
        val afterSet = controller.setExactVolume(midLevel)
        assertEquals(midLevel, afterSet.currentVolume)

        // Attempt to exceed maxVolume -> must clamp at maxVolume
        val afterOverMax = controller.setExactVolume(maxVol + 50)
        assertEquals(maxVol, afterOverMax.currentVolume)
        val afterStepAboveMax = controller.adjustVolumeByStep(+5)
        assertEquals(maxVol, afterStepAboveMax.currentVolume)

        // Mute from midLevel -> goes to minVolume, then toggleMute again restores midLevel
        controller.setExactVolume(midLevel)
        val muted = controller.toggleMute()
        assertEquals(muted.minVolume, muted.currentVolume)
        assertTrue(muted.isMuted)

        val unmuted = controller.toggleMute()
        assertEquals(midLevel, unmuted.currentVolume)
        assertFalse(unmuted.isMuted)
    }

    @Test
    fun `service survives onTaskRemoved when swiped from Recent Apps and stops cleanly on ACTION_STOP`() {
        val controller = Robolectric.buildService(VolumeOverlayService::class.java).create()
        val service = controller.get()

        // 1. Start service with ACTION_START
        val startIntent = Intent(context, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_START
        }
        val startResult = service.onStartCommand(startIntent, 0, 1)
        assertEquals(android.app.Service.START_STICKY, startResult)
        assertTrue(VolumeOverlayService.isServiceRunningFlow.value)
        assertTrue(VolumeOverlayService.isOverlayAttachedFlow.value)
        assertTrue(preferences.isUserEnabled())

        // 2. Starting again does not create duplicate overlay and keeps service running
        service.onStartCommand(startIntent, 0, 2)
        assertTrue(VolumeOverlayService.isServiceRunningFlow.value)
        assertTrue(VolumeOverlayService.isOverlayAttachedFlow.value)

        // 3. Swipe app from Recent Apps -> onTaskRemoved is called; overlay MUST remain active!
        service.onTaskRemoved(Intent(context, MainActivity::class.java))
        assertTrue(VolumeOverlayService.isServiceRunningFlow.value)
        assertTrue(VolumeOverlayService.isOverlayAttachedFlow.value)
        assertTrue(preferences.isUserEnabled())

        // 4. System recreates service with null Intent (START_STICKY recovery) -> stays active
        val stickyResult = service.onStartCommand(null, 0, 3)
        assertEquals(android.app.Service.START_STICKY, stickyResult)
        assertTrue(VolumeOverlayService.isServiceRunningFlow.value)

        // 5. User taps "Tắt" from Notification (ACTION_STOP) -> stops service, detaches overlay, clears userEnabled
        val stopIntent = Intent(context, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_STOP
        }
        val stopResult = service.onStartCommand(stopIntent, 0, 4)
        assertEquals(android.app.Service.START_NOT_STICKY, stopResult)
        assertFalse(VolumeOverlayService.isServiceRunningFlow.value)
        assertFalse(VolumeOverlayService.isOverlayAttachedFlow.value)
        assertFalse(preferences.isUserEnabled())

        controller.destroy()
    }

    @Test
    fun `service handles revoked overlay permission safely without crashing`() {
        VolumeOverlayService.overlayPermissionCheckerOverride = { false }
        preferences.setUserEnabled(true)

        val controller = Robolectric.buildService(VolumeOverlayService::class.java).create()
        val service = controller.get()

        val startIntent = Intent(context, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_START
        }
        val result = service.onStartCommand(startIntent, 0, 1)

        assertEquals(android.app.Service.START_NOT_STICKY, result)
        assertFalse(VolumeOverlayService.isServiceRunningFlow.value)
        assertFalse(VolumeOverlayService.isOverlayAttachedFlow.value)
        assertFalse(preferences.isUserEnabled())

        controller.destroy()
    }

    @Test
    fun `preferences persist display mode, size, opacity, and edge snap`() {
        preferences.setDisplayMode(OverlayDisplayMode.COLLAPSED_BUBBLE)
        preferences.setSnapToEdge(false)
        preferences.setSizePercent(125)
        preferences.setOpacityPercent(70)
        preferences.savePosition(120, 480)

        val config: OverlayConfig = preferences.readConfig()
        assertEquals(OverlayDisplayMode.COLLAPSED_BUBBLE, config.displayMode)
        assertFalse(config.snapToEdge)
        assertEquals(125, config.sizePercent)
        assertEquals(70, config.opacityPercent)
        assertEquals(120, config.positionX)
        assertEquals(480, config.positionY)
    }
}

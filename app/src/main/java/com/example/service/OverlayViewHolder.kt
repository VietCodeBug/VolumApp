package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.R
import com.example.audio.DeviceVolumeController
import com.example.audio.VolumeSnapshot
import com.example.data.OverlayConfig
import com.example.data.OverlayDisplayMode
import com.example.data.OverlayPreferences
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Owns and manages the native Android View hierarchy attached to [WindowManager] via [VolumeOverlayService].
 * Operates completely independently of any Activity.
 */
class OverlayViewHolder(
    private val serviceContext: Context,
    private val windowManager: WindowManager,
    private val volumeController: DeviceVolumeController,
    private val preferences: OverlayPreferences
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop: Int = ViewConfiguration.get(serviceContext).scaledTouchSlop

    var isAttached: Boolean = false
        private set

    var isPanelExpanded: Boolean = false
        private set

    private var currentConfig: OverlayConfig = preferences.readConfig()

    // Window layout params: strictly WRAP_CONTENT, NOT_FOCUSABLE, NOT_TOUCH_MODAL
    val layoutParams: WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    // Root container and three mode subviews
    val rootContainer = FrameLayout(serviceContext)
    private lateinit var compactBarLayout: LinearLayout
    private lateinit var collapsedBubbleLayout: FrameLayout
    private lateinit var expandedPanelLayout: LinearLayout

    // UI references for live updates
    private lateinit var compactPlusButton: FrameLayout
    private lateinit var compactMinusButton: FrameLayout
    private lateinit var compactCenterButton: LinearLayout
    private lateinit var compactVolumeText: TextView
    private lateinit var compactToBubbleButton: FrameLayout

    private lateinit var bubbleIconView: ImageView
    private lateinit var bubbleBadgeText: TextView

    private lateinit var panelHeaderSpeakerIcon: ImageView
    private lateinit var panelVolumeStepText: TextView
    private lateinit var panelCollapseButton: FrameLayout
    private lateinit var panelSeekBar: SeekBar
    private lateinit var panelMinusButton: LinearLayout
    private lateinit var panelMuteButton: LinearLayout
    private lateinit var panelMuteIcon: ImageView
    private lateinit var panelMuteText: TextView
    private lateinit var panelPlusButton: LinearLayout
    private lateinit var panelModeSwitchButton: TextView

    private var isUserDraggingSeekBar = false
    private var isAwaitingSeekBarVerification = false
    private var pendingSeekBarVolume: Int? = null

    // Keep a user's drag position visible until Android has had a chance to apply the final command.
    private val verifySeekBarVolumeRunnable = Runnable {
        isAwaitingSeekBarVerification = false
        updateVolumeUi(volumeController.refreshVolumeState())
    }

    // Hold-to-repeat state
    private var activeRepeatDelta: Int = 0
    private var isRepeatingHold: Boolean = false
    private val holdRepeatRunnable = object : Runnable {
        override fun run() {
            if (activeRepeatDelta == 0 || !isAttached) return
            isRepeatingHold = true
            val updated = volumeController.adjustVolumeByStep(activeRepeatDelta)
            updateVolumeUi(updated)
            if (isPanelExpanded) {
                scheduleAutoCollapse()
            }
            val hitBoundary = (activeRepeatDelta > 0 && updated.currentVolume >= updated.maxVolume) ||
                (activeRepeatDelta < 0 && updated.currentVolume <= updated.minVolume)
            if (!hitBoundary) {
                mainHandler.postDelayed(this, REPEAT_INTERVAL_MS)
            } else {
                stopHoldRepeat()
            }
        }
    }

    // 4-second auto-collapse timer for Expanded Panel
    private val autoCollapseRunnable = Runnable {
        if (isAttached && isPanelExpanded) {
            collapseExpandedPanel()
        }
    }

    init {
        buildOverlayViewTree()
    }

    private fun dp(value: Float): Int {
        val scale = currentConfig.scaleFactor
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value * scale,
            serviceContext.resources.displayMetrics
        ).roundToInt().coerceAtLeast(1)
    }

    private fun rawDp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            serviceContext.resources.displayMetrics
        ).roundToInt().coerceAtLeast(1)
    }

    private fun sp(value: Float): Float {
        return value * currentConfig.scaleFactor
    }

    private fun createRoundedBackground(
        fillColor: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Float = 0f,
        cornerRadiusDp: Float = 20f
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(cornerRadiusDp).toFloat()
            setColor(fillColor)
            if (strokeWidthDp > 0f) {
                setStroke(rawDp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun buildOverlayViewTree() {
        rootContainer.removeAllViews()
        rootContainer.clipChildren = false
        rootContainer.clipToPadding = false

        compactBarLayout = buildCompactBarView()
        collapsedBubbleLayout = buildCollapsedBubbleView()
        expandedPanelLayout = buildExpandedPanelView()

        rootContainer.addView(
            compactBarLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootContainer.addView(
            collapsedBubbleLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        rootContainer.addView(
            expandedPanelLayout,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        applyVisibilityForCurrentMode()
        rootContainer.alpha = currentConfig.alphaValue
        updateVolumeUi(volumeController.refreshVolumeState())
    }

    /**
     * Mode 1: Compact Edge Pill Bar with "+" and "−" buttons, middle volume/expand button, and bubble toggle.
     */
    private fun buildCompactBarView(): LinearLayout {
        val container = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val padH = dp(6f)
            val padV = dp(6f)
            setPadding(padH, padV, padH, padV)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#EE0F172A"),
                strokeColor = Color.parseColor("#6638BDF8"),
                strokeWidthDp = 1.5f,
                cornerRadiusDp = 26f
            )
            elevation = rawDp(8f).toFloat()
        }

        val btnSize = max(rawDp(48f), dp(48f))

        // "+" Button
        compactPlusButton = FrameLayout(serviceContext).apply {
            contentDescription = serviceContext.getString(R.string.cd_volume_up)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#0EA5E9"),
                cornerRadiusDp = 20f
            )
            val icon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_plus)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val iconSize = dp(24f)
            addView(
                icon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            )
        }
        attachVolumeStepAndDragTouchListener(compactPlusButton, delta = +1)
        container.addView(
            compactPlusButton,
            LinearLayout.LayoutParams(btnSize, btnSize)
        )

        // Middle indicator / Expand slider button (also draggable)
        compactCenterButton = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            contentDescription = serviceContext.getString(R.string.cd_expand_panel)
            setPadding(dp(4f), dp(6f), dp(4f), dp(6f))
        }
        val expandIcon = ImageView(serviceContext).apply {
            setImageResource(R.drawable.ic_expand_panel)
            setColorFilter(Color.parseColor("#38BDF8"))
        }
        compactVolumeText = TextView(serviceContext).apply {
            setTextColor(Color.parseColor("#E2E8F0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11f))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = "0/15"
        }
        compactCenterButton.addView(
            expandIcon,
            LinearLayout.LayoutParams(dp(18f), dp(18f))
        )
        compactCenterButton.addView(
            compactVolumeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        attachTapOrDragTouchListener(
            view = compactCenterButton,
            onTap = { expandFullPanel() }
        )
        container.addView(
            compactCenterButton,
            LinearLayout.LayoutParams(
                btnSize,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4f)
                bottomMargin = dp(4f)
            }
        )

        // "−" Button
        compactMinusButton = FrameLayout(serviceContext).apply {
            contentDescription = serviceContext.getString(R.string.cd_volume_down)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#1E293B"),
                strokeColor = Color.parseColor("#475569"),
                strokeWidthDp = 1f,
                cornerRadiusDp = 20f
            )
            val icon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_minus)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
            val iconSize = dp(24f)
            addView(
                icon,
                FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            )
        }
        attachVolumeStepAndDragTouchListener(compactMinusButton, delta = -1)
        container.addView(
            compactMinusButton,
            LinearLayout.LayoutParams(btnSize, btnSize)
        )

        // Small collapse-to-bubble button at the bottom of the compact bar
        compactToBubbleButton = FrameLayout(serviceContext).apply {
            contentDescription = serviceContext.getString(R.string.cd_volume_bubble)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#172554"),
                cornerRadiusDp = 14f
            )
            val speakerMini = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_speaker)
                setColorFilter(Color.parseColor("#7DD3FC"))
            }
            addView(
                speakerMini,
                FrameLayout.LayoutParams(dp(16f), dp(16f), Gravity.CENTER)
            )
        }
        attachTapOrDragTouchListener(
            view = compactToBubbleButton,
            onTap = {
                preferences.setDisplayMode(OverlayDisplayMode.COLLAPSED_BUBBLE)
                currentConfig = preferences.readConfig()
                applyVisibilityForCurrentMode()
                clampAndUpdateWindowPosition(snapIfConfigured = true)
            }
        )
        container.addView(
            compactToBubbleButton,
            LinearLayout.LayoutParams(btnSize, max(rawDp(32f), dp(30f))).apply {
                topMargin = dp(5f)
            }
        )

        return container
    }

    /**
     * Mode 2: Collapsed Speaker Bubble. Tap opens Expanded Panel; Drag moves the bubble.
     */
    private fun buildCollapsedBubbleView(): FrameLayout {
        val bubbleSize = max(rawDp(52f), dp(54f))
        val container = FrameLayout(serviceContext).apply {
            contentDescription = serviceContext.getString(R.string.cd_volume_bubble)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#EE0F172A"),
                strokeColor = Color.parseColor("#0EA5E9"),
                strokeWidthDp = 2f,
                cornerRadiusDp = 30f
            )
            elevation = rawDp(8f).toFloat()
        }

        val innerWrapper = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        bubbleIconView = ImageView(serviceContext).apply {
            setImageResource(R.drawable.ic_volume_speaker)
            setColorFilter(Color.parseColor("#38BDF8"))
        }
        bubbleBadgeText = TextView(serviceContext).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(10.5f))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            text = "50%"
        }

        innerWrapper.addView(
            bubbleIconView,
            LinearLayout.LayoutParams(dp(22f), dp(22f))
        )
        innerWrapper.addView(
            bubbleBadgeText,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(
            innerWrapper,
            FrameLayout.LayoutParams(bubbleSize, bubbleSize, Gravity.CENTER)
        )

        attachTapOrDragTouchListener(
            view = container,
            onTap = { expandFullPanel() }
        )

        return container
    }

    /**
     * Mode 3: Expanded Panel with header, real-time SeekBar slider, − / Mute / + buttons, and mode switch.
     */
    private fun buildExpandedPanelView(): LinearLayout {
        val panelWidth = max(rawDp(250f), dp(264f))
        val container = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(14f)
            setPadding(pad, pad, pad, pad)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#F50F172A"),
                strokeColor = Color.parseColor("#8838BDF8"),
                strokeWidthDp = 1.5f,
                cornerRadiusDp = 22f
            )
            elevation = rawDp(10f).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                panelWidth,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top Header (Draggable area + Title + Volume Step + Close button)
        val headerRow = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        panelHeaderSpeakerIcon = ImageView(serviceContext).apply {
            setImageResource(R.drawable.ic_volume_speaker)
            setColorFilter(Color.parseColor("#38BDF8"))
        }
        headerRow.addView(
            panelHeaderSpeakerIcon,
            LinearLayout.LayoutParams(dp(20f), dp(20f)).apply {
                marginEnd = dp(8f)
            }
        )

        val titleColumn = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleText = TextView(serviceContext).apply {
            text = serviceContext.getString(R.string.overlay_panel_title)
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(13f))
            typeface = Typeface.DEFAULT_BOLD
        }
        panelVolumeStepText = TextView(serviceContext).apply {
            text = "8 / 15 (53%)"
            setTextColor(Color.parseColor("#38BDF8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f))
            typeface = Typeface.DEFAULT_BOLD
        }
        titleColumn.addView(titleText)
        titleColumn.addView(panelVolumeStepText)

        headerRow.addView(
            titleColumn,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )

        val collapseBtnSize = max(rawDp(40f), dp(38f))
        panelCollapseButton = FrameLayout(serviceContext).apply {
            contentDescription = serviceContext.getString(R.string.cd_collapse_panel)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#1E293B"),
                cornerRadiusDp = 12f
            )
            val closeIcon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_collapse_panel)
                setColorFilter(Color.parseColor("#CBD5E1"))
            }
            addView(
                closeIcon,
                FrameLayout.LayoutParams(dp(18f), dp(18f), Gravity.CENTER)
            )
            setOnClickListener {
                collapseExpandedPanel()
            }
        }
        headerRow.addView(
            panelCollapseButton,
            LinearLayout.LayoutParams(collapseBtnSize, collapseBtnSize)
        )

        // Allow dragging the panel via the header row
        attachTapOrDragTouchListener(
            view = headerRow,
            onTap = { scheduleAutoCollapse() }
        )
        container.addView(
            headerRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Volume SeekBar (Slider)
        panelSeekBar = SeekBar(serviceContext).apply {
            val snap = volumeController.readCurrentSnapshot()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                min = snap.minVolume
            }
            max = snap.maxVolume
            progress = snap.currentVolume
            setPadding(dp(8f), dp(10f), dp(8f), dp(10f))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // The gesture is a preview. Rapid calls to AudioManager while dragging
                        // can be dropped or delayed by vendor audio services.
                        pendingSeekBarVolume = progress
                        val current = volumeController.volumeState.value
                        updateVolumeTextAndIcons(current.copy(currentVolume = progress))
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    isUserDraggingSeekBar = true
                    isAwaitingSeekBarVerification = false
                    pendingSeekBarVolume = seekBar?.progress
                    mainHandler.removeCallbacks(verifySeekBarVolumeRunnable)
                    cancelAutoCollapse()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val target = pendingSeekBarVolume ?: seekBar?.progress
                    pendingSeekBarVolume = null
                    isUserDraggingSeekBar = false
                    if (target != null) {
                        // Apply the final value once, then verify actual system volume after
                        // a short delay rather than immediately snapping to a stale read.
                        isAwaitingSeekBarVerification = true
                        volumeController.setExactVolume(target)
                        mainHandler.removeCallbacks(verifySeekBarVolumeRunnable)
                        mainHandler.postDelayed(verifySeekBarVolumeRunnable, 250L)
                    }
                    scheduleAutoCollapse()
                }
            })
        }
        container.addView(
            panelSeekBar,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8f)
                bottomMargin = dp(8f)
            }
        )

        // Controls row: [ − ]  [ Tắt tiếng / Mở tiếng ]  [ + ]
        val controlsRow = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val controlBtnHeight = max(rawDp(48f), dp(46f))

        // "−" Button in panel
        panelMinusButton = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = serviceContext.getString(R.string.cd_volume_down)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#1E293B"),
                strokeColor = Color.parseColor("#475569"),
                strokeWidthDp = 1f,
                cornerRadiusDp = 14f
            )
            val icon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_minus)
            }
            addView(icon, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        }
        attachPanelButtonHoldListener(panelMinusButton, delta = -1)
        controlsRow.addView(
            panelMinusButton,
            LinearLayout.LayoutParams(0, controlBtnHeight, 1f).apply {
                marginEnd = dp(6f)
            }
        )

        // Quick Mute / Unmute Button in panel
        panelMuteButton = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = serviceContext.getString(R.string.cd_mute_toggle)
            setPadding(dp(8f), 0, dp(8f), 0)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#1E293B"),
                strokeColor = Color.parseColor("#475569"),
                strokeWidthDp = 1f,
                cornerRadiusDp = 14f
            )
            panelMuteIcon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_mute)
            }
            panelMuteText = TextView(serviceContext).apply {
                text = serviceContext.getString(R.string.overlay_mute_btn)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11.5f))
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(4f), 0, 0, 0)
            }
            addView(panelMuteIcon, LinearLayout.LayoutParams(dp(18f), dp(18f)))
            addView(panelMuteText)
            setOnClickListener {
                scheduleAutoCollapse()
                val updated = volumeController.toggleMute()
                updateVolumeUi(updated)
            }
        }
        controlsRow.addView(
            panelMuteButton,
            LinearLayout.LayoutParams(0, controlBtnHeight, 1.45f).apply {
                marginEnd = dp(6f)
            }
        )

        // "+" Button in panel
        panelPlusButton = LinearLayout(serviceContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            contentDescription = serviceContext.getString(R.string.cd_volume_up)
            background = createRoundedBackground(
                fillColor = Color.parseColor("#0EA5E9"),
                cornerRadiusDp = 14f
            )
            val icon = ImageView(serviceContext).apply {
                setImageResource(R.drawable.ic_volume_plus)
            }
            addView(icon, LinearLayout.LayoutParams(dp(22f), dp(22f)))
        }
        attachPanelButtonHoldListener(panelPlusButton, delta = +1)
        controlsRow.addView(
            panelPlusButton,
            LinearLayout.LayoutParams(0, controlBtnHeight, 1f)
        )

        container.addView(
            controlsRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Bottom quick mode switch chip (Switch collapsed target between Compact Bar & Bubble)
        panelModeSwitchButton = TextView(serviceContext).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#94A3B8"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp(11f))
            setPadding(dp(8f), dp(6f), dp(8f), dp(2f))
            updateModeSwitchLabel(this)
            setOnClickListener {
                val nextMode = if (currentConfig.displayMode == OverlayDisplayMode.COMPACT_BAR) {
                    OverlayDisplayMode.COLLAPSED_BUBBLE
                } else {
                    OverlayDisplayMode.COMPACT_BAR
                }
                preferences.setDisplayMode(nextMode)
                currentConfig = preferences.readConfig()
                updateModeSwitchLabel(this)
                scheduleAutoCollapse()
            }
        }
        container.addView(
            panelModeSwitchButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6f)
            }
        )

        return container
    }

    private fun updateModeSwitchLabel(textView: TextView) {
        textView.text = if (currentConfig.displayMode == OverlayDisplayMode.COMPACT_BAR) {
            "Chế độ thu gọn: Thanh +/− (Chạm để đổi sang Bong bóng)"
        } else {
            "Chế độ thu gọn: Bong bóng loa (Chạm để đổi sang Thanh +/−)"
        }
    }

    /**
     * Distinguishes between:
     * 1) Tap -> adjusts volume by 1 step
     * 2) Long press & hold -> adjusts volume continuously until finger release/cancel
     * 3) Drag -> moves the floating overlay across the screen if finger moves > touchSlop before hold triggers
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachVolumeStepAndDragTouchListener(view: View, delta: Int) {
        var initialWindowX = 0
        var initialWindowY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialWindowX = layoutParams.x
                    initialWindowY = layoutParams.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    v.isPressed = true
                    startHoldRepeat(delta)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && !isRepeatingHold && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        v.isPressed = false
                        stopHoldRepeat()
                    }
                    if (isDragging) {
                        moveOverlayTo(
                            targetX = (initialWindowX + dx).roundToInt(),
                            targetY = (initialWindowY + dy).roundToInt()
                        )
                    } else if (!isPointInsideView(v, event.x, event.y)) {
                        // Finger slid outside the button bounds — stop continuous repeat immediately
                        v.isPressed = false
                        stopHoldRepeat()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val wasRepeating = isRepeatingHold
                    v.isPressed = false
                    stopHoldRepeat()
                    if (isDragging) {
                        finishDragAndPersistPosition()
                    } else if (!wasRepeating) {
                        // Single tap
                        val updated = volumeController.adjustVolumeByStep(delta)
                        updateVolumeUi(updated)
                        v.performClick()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopHoldRepeat()
                    if (isDragging) {
                        finishDragAndPersistPosition()
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Hold-to-repeat listener for +/- buttons inside the Expanded Panel.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachPanelButtonHoldListener(view: View, delta: Int) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    scheduleAutoCollapse()
                    startHoldRepeat(delta)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isPointInsideView(v, event.x, event.y)) {
                        v.isPressed = false
                        stopHoldRepeat()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val wasRepeating = isRepeatingHold
                    v.isPressed = false
                    stopHoldRepeat()
                    scheduleAutoCollapse()
                    if (!wasRepeating) {
                        val updated = volumeController.adjustVolumeByStep(delta)
                        updateVolumeUi(updated)
                        v.performClick()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopHoldRepeat()
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Distinguishes Tap vs Drag using system [touchSlop].
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachTapOrDragTouchListener(view: View, onTap: () -> Unit) {
        var initialWindowX = 0
        var initialWindowY = 0
        var downRawX = 0f
        var downRawY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialWindowX = layoutParams.x
                    initialWindowY = layoutParams.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    isDragging = false
                    v.isPressed = true
                    if (isPanelExpanded) {
                        scheduleAutoCollapse()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        v.isPressed = false
                    }
                    if (isDragging) {
                        if (isPanelExpanded) {
                            scheduleAutoCollapse()
                        }
                        moveOverlayTo(
                            targetX = (initialWindowX + dx).roundToInt(),
                            targetY = (initialWindowY + dy).roundToInt()
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    if (isDragging) {
                        finishDragAndPersistPosition()
                    } else {
                        onTap()
                        v.performClick()
                    }
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    if (isDragging) {
                        finishDragAndPersistPosition()
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }
    }

    private fun isPointInsideView(view: View, localX: Float, localY: Float): Boolean {
        val slop = touchSlop.toFloat()
        return localX >= -slop &&
            localY >= -slop &&
            localX <= view.width + slop &&
            localY <= view.height + slop
    }

    private fun startHoldRepeat(delta: Int) {
        stopHoldRepeat()
        activeRepeatDelta = delta
        isRepeatingHold = false
        mainHandler.postDelayed(holdRepeatRunnable, INITIAL_HOLD_DELAY_MS)
    }

    fun stopHoldRepeat() {
        mainHandler.removeCallbacks(holdRepeatRunnable)
        activeRepeatDelta = 0
        isRepeatingHold = false
    }

    private fun scheduleAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        if (isPanelExpanded) {
            mainHandler.postDelayed(autoCollapseRunnable, AUTO_COLLAPSE_DELAY_MS)
        }
    }

    private fun cancelAutoCollapse() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
    }

    fun expandFullPanel() {
        stopHoldRepeat()
        isPanelExpanded = true
        val freshVolume = volumeController.refreshVolumeState()
        updateVolumeUi(freshVolume)
        applyVisibilityForCurrentMode()
        clampAndUpdateWindowPosition(snapIfConfigured = false)
        scheduleAutoCollapse()
    }

    fun collapseExpandedPanel() {
        stopHoldRepeat()
        cancelAutoCollapse()
        isPanelExpanded = false
        applyVisibilityForCurrentMode()
        clampAndUpdateWindowPosition(snapIfConfigured = currentConfig.snapToEdge)
    }

    private fun applyVisibilityForCurrentMode() {
        when {
            isPanelExpanded -> {
                compactBarLayout.visibility = View.GONE
                collapsedBubbleLayout.visibility = View.GONE
                expandedPanelLayout.visibility = View.VISIBLE
            }

            currentConfig.displayMode == OverlayDisplayMode.COLLAPSED_BUBBLE -> {
                compactBarLayout.visibility = View.GONE
                collapsedBubbleLayout.visibility = View.VISIBLE
                expandedPanelLayout.visibility = View.GONE
            }

            else -> {
                compactBarLayout.visibility = View.VISIBLE
                collapsedBubbleLayout.visibility = View.GONE
                expandedPanelLayout.visibility = View.GONE
            }
        }
    }

    fun updateVolumeUi(snapshot: VolumeSnapshot) {
        if (!::compactVolumeText.isInitialized) return
        if (isUserDraggingSeekBar || isAwaitingSeekBarVerification) return
        updateVolumeTextAndIcons(snapshot)
        if (!isUserDraggingSeekBar) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                panelSeekBar.min = snapshot.minVolume
            }
            panelSeekBar.max = snapshot.maxVolume
            panelSeekBar.progress = snapshot.currentVolume
        }
    }

    private fun updateVolumeTextAndIcons(snapshot: VolumeSnapshot) {
        compactVolumeText.text = "${snapshot.currentVolume}/${snapshot.maxVolume}"
        bubbleBadgeText.text = "${snapshot.percentage}%"
        panelVolumeStepText.text =
            "${snapshot.currentVolume} / ${snapshot.maxVolume} (${snapshot.percentage}%)"

        val speakerDrawable = if (snapshot.isMuted) {
            R.drawable.ic_volume_mute
        } else {
            R.drawable.ic_volume_speaker
        }
        val iconTint = if (snapshot.isMuted) {
            Color.parseColor("#F87171")
        } else {
            Color.parseColor("#38BDF8")
        }

        bubbleIconView.setImageResource(speakerDrawable)
        bubbleIconView.setColorFilter(iconTint)
        panelHeaderSpeakerIcon.setImageResource(speakerDrawable)
        panelHeaderSpeakerIcon.setColorFilter(iconTint)

        if (snapshot.isMuted) {
            panelMuteButton.background = createRoundedBackground(
                fillColor = Color.parseColor("#EF4444"),
                cornerRadiusDp = 14f
            )
            panelMuteIcon.setImageResource(R.drawable.ic_volume_speaker)
            panelMuteText.text = serviceContext.getString(R.string.overlay_unmute_btn)
        } else {
            panelMuteButton.background = createRoundedBackground(
                fillColor = Color.parseColor("#1E293B"),
                strokeColor = Color.parseColor("#475569"),
                strokeWidthDp = 1f,
                cornerRadiusDp = 14f
            )
            panelMuteIcon.setImageResource(R.drawable.ic_volume_mute)
            panelMuteText.text = serviceContext.getString(R.string.overlay_mute_btn)
        }
    }

    fun attachToWindow(): Boolean {
        if (isAttached) {
            applyConfig(preferences.readConfig())
            updateVolumeUi(volumeController.refreshVolumeState())
            return true
        }
        currentConfig = preferences.readConfig()
        val (screenW, screenH) = getScreenDimensions()
        val (estW, estH) = getEstimatedOverlaySize()
        val (clampedX, clampedY) = OverlayPreferences.clampPositionToBounds(
            x = currentConfig.positionX,
            y = currentConfig.positionY,
            viewWidth = estW,
            viewHeight = estH,
            screenWidth = screenW,
            screenHeight = screenH,
            snapToEdge = currentConfig.snapToEdge
        )
        layoutParams.x = clampedX
        layoutParams.y = clampedY

        return runCatching {
            windowManager.addView(rootContainer, layoutParams)
            isAttached = true
            preferences.savePosition(clampedX, clampedY)
            updateVolumeUi(volumeController.refreshVolumeState())
            true
        }.getOrElse {
            isAttached = false
            false
        }
    }

    fun detachFromWindow() {
        stopHoldRepeat()
        cancelAutoCollapse()
        mainHandler.removeCallbacks(verifySeekBarVolumeRunnable)
        isUserDraggingSeekBar = false
        isAwaitingSeekBarVerification = false
        pendingSeekBarVolume = null
        if (!isAttached) return
        runCatching {
            windowManager.removeViewImmediate(rootContainer)
        }.onFailure {
            runCatching { windowManager.removeView(rootContainer) }
        }
        isAttached = false
    }

    fun applyConfig(newConfig: OverlayConfig) {
        val sizeChanged = newConfig.sizePercent != currentConfig.sizePercent
        val modeChanged = newConfig.displayMode != currentConfig.displayMode
        currentConfig = newConfig
        rootContainer.alpha = currentConfig.alphaValue

        if (sizeChanged) {
            mainHandler.removeCallbacks(verifySeekBarVolumeRunnable)
            isUserDraggingSeekBar = false
            isAwaitingSeekBarVerification = false
            pendingSeekBarVolume = null
            buildOverlayViewTree()
        } else if (modeChanged) {
            applyVisibilityForCurrentMode()
            updateModeSwitchLabel(panelModeSwitchButton)
        }
        clampAndUpdateWindowPosition(snapIfConfigured = !isPanelExpanded && currentConfig.snapToEdge)
    }

    private fun moveOverlayTo(targetX: Int, targetY: Int) {
        if (!isAttached) return
        val (screenW, screenH) = getScreenDimensions()
        val (estW, estH) = getEstimatedOverlaySize()
        val (clampedX, clampedY) = OverlayPreferences.clampPositionToBounds(
            x = targetX,
            y = targetY,
            viewWidth = estW,
            viewHeight = estH,
            screenWidth = screenW,
            screenHeight = screenH,
            snapToEdge = false
        )
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        runCatching {
            windowManager.updateViewLayout(rootContainer, layoutParams)
        }
    }

    private fun finishDragAndPersistPosition() {
        clampAndUpdateWindowPosition(snapIfConfigured = !isPanelExpanded && currentConfig.snapToEdge)
    }

    fun clampAndUpdateWindowPosition(snapIfConfigured: Boolean) {
        if (!isAttached) return
        val config = preferences.readConfig()
        currentConfig = config
        val (screenW, screenH) = getScreenDimensions()
        val (estW, estH) = getEstimatedOverlaySize()
        val sourceX = if (config.positionX == OverlayConfig.DEFAULT_POSITION_UNSET) {
            OverlayConfig.DEFAULT_POSITION_UNSET
        } else {
            layoutParams.x
        }
        val sourceY = if (config.positionY == OverlayConfig.DEFAULT_POSITION_UNSET) {
            OverlayConfig.DEFAULT_POSITION_UNSET
        } else {
            layoutParams.y
        }
        val (clampedX, clampedY) = OverlayPreferences.clampPositionToBounds(
            x = sourceX,
            y = sourceY,
            viewWidth = estW,
            viewHeight = estH,
            screenWidth = screenW,
            screenHeight = screenH,
            snapToEdge = snapIfConfigured
        )
        layoutParams.x = clampedX
        layoutParams.y = clampedY
        runCatching {
            windowManager.updateViewLayout(rootContainer, layoutParams)
        }
        if (!isPanelExpanded) {
            preferences.savePosition(clampedX, clampedY)
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            max(1, bounds.width()) to max(1, bounds.height())
        } else {
            val dm = serviceContext.resources.displayMetrics
            max(1, dm.widthPixels) to max(1, dm.heightPixels)
        }
    }

    private fun getEstimatedOverlaySize(): Pair<Int, Int> {
        val measuredW = rootContainer.width
        val measuredH = rootContainer.height
        if (measuredW > 0 && measuredH > 0) {
            return measuredW to measuredH
        }
        return when {
            isPanelExpanded -> max(rawDp(250f), dp(264f)) to dp(190f)
            currentConfig.displayMode == OverlayDisplayMode.COLLAPSED_BUBBLE ->
                max(rawDp(52f), dp(54f)) to max(rawDp(52f), dp(54f))
            else -> dp(64f) to dp(196f)
        }
    }

    companion object {
        const val INITIAL_HOLD_DELAY_MS = 350L
        const val REPEAT_INTERVAL_MS = 110L
        const val AUTO_COLLAPSE_DELAY_MS = 4000L
    }
}

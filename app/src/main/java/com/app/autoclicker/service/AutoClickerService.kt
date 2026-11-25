package com.app.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.app.autoclicker.R
import com.app.autoclicker.models.*
import com.app.autoclicker.ui.DialogHelper
import com.app.autoclicker.ui.MainActivity
import com.app.autoclicker.ui.SettingsActivity
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class AutoClickerService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AutoClickerService? = null
    }

    private lateinit var windowManager: WindowManager
    private var controlBarView: LinearLayout? = null
    private lateinit var controlBarParams: WindowManager.LayoutParams
    private lateinit var btnPlay: ImageView
    private lateinit var btnCollapse: ImageView

    private data class TargetBubbleRecord(
        val view: View,
        val params: WindowManager.LayoutParams,
        val point: TargetPoint,
        val isEndNode: Boolean
    )

    private val targetViews = mutableListOf<View>()
    private val targetBubbleRecords = mutableListOf<TargetBubbleRecord>()
    private var probeView: View? = null

    @Volatile
    private var originPhysicalX = 0f

    @Volatile
    private var originPhysicalY = 0f

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isWaitingSchedule = false

    private var isCollapsed = false
    private var activeAppPackage: String? = null

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    private fun getBubbleSizePx(): Int {
        val raw = 36.dp().coerceAtLeast(72)
        return (raw / 2) * 2
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Handler(Looper.getMainLooper()).post { stopClicking() }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        probeView?.post { updateOriginFromProbe(probeView) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopClicking()
        hideOverlay()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return

            if (ConfigStore.autoStartPackages.contains(pkg) && controlBarView == null) {
                Handler(Looper.getMainLooper()).post { showOverlay() }
            }

            if (ConfigStore.activeConfig.pauseOnAppChange && isRunning && activeAppPackage != null && pkg != activeAppPackage) {
                Handler(Looper.getMainLooper()).post {
                    stopClicking()
                    Toast.makeText(this, "Paused: App changed", Toast.LENGTH_SHORT).show()
                }
            }
            activeAppPackage = pkg
        }
    }

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenOffReceiver) } catch (_: Exception) {}
        stopClicking()
        hideOverlay()
        serviceScope.cancel()
        instance = null
    }

    fun isOverlayShowing(): Boolean = controlBarView != null

    fun showOverlay() {
        if (controlBarView != null) return

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1D24"))
                setStroke(1.dp(), Color.parseColor("#383E4E"))
                cornerRadius = 14f.dpF()
            }
            setPadding(4.dp(), 6.dp(), 4.dp(), 6.dp())
        }

        // 1. Play / Pause
        btnPlay = createBarVectorButton(R.drawable.ic_play, Color.WHITE).apply {
            setOnClickListener {
                if (isRunning || isWaitingSchedule) {
                    stopClicking()
                } else {
                    startClickingFlow()
                }
            }
        }

        // 2. Add Target (+)
        val btnAdd = createBarVectorButton(R.drawable.ic_add, Color.WHITE).apply {
            setOnClickListener {
                if (!isRunning) addTarget(isSwipe = false)
            }
            setOnLongClickListener {
                if (!isRunning) addTarget(isSwipe = true)
                true
            }
        }

        // 3. Remove Target (−)
        val btnRemove = createBarVectorButton(R.drawable.ic_remove, Color.WHITE).apply {
            setOnClickListener {
                if (!isRunning) removeLastTarget()
            }
            setOnLongClickListener {
                if (!isRunning) clearAllTargets()
                true
            }
        }

        // 4. Open Settings (⚙)
        val btnSettings = createBarVectorButton(R.drawable.ic_settings, Color.WHITE).apply {
            setOnClickListener {
                if (isRunning) stopClicking()
                val intent = Intent(this@AutoClickerService, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            setOnLongClickListener {
                if (isRunning) stopClicking()
                val intent = Intent(this@AutoClickerService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(intent)
                true
            }
        }

        // 5. Close Floating Overlay (✕)
        val btnClose = createBarVectorButton(R.drawable.ic_close, Color.parseColor("#F87171")).apply {
            setOnClickListener {
                stopClicking()
                hideOverlay()
            }
        }

        // TOP BUTTON: Drag Handle + Shrink (<) / Expand (>)
        btnCollapse = createBarVectorButton(R.drawable.ic_chevron_left, Color.parseColor("#60A5FA")).apply {
            var initialX = 0; var initialY = 0
            var initialTouchX = 0f; var initialTouchY = 0f
            var moved = false

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = controlBarParams.x
                        initialY = controlBarParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            moved = true
                            controlBarParams.x = initialX + dx
                            controlBarParams.y = initialY + dy
                            windowManager.updateViewLayout(controlBarView, controlBarParams)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            toggleCollapse(listOf(btnPlay, btnAdd, btnRemove, btnSettings, btnClose))
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        // Order: Top Drag/Shrink/Expand (< / >), Play, Add, Remove, Settings, Close
        listOf(btnCollapse, btnPlay, btnAdd, btnRemove, btnSettings, btnClose).forEach { bar.addView(it) }

        controlBarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 280
        }

        attachScreenOriginProbe()
        windowManager.addView(bar, controlBarParams)
        controlBarView = bar
        rebuildAllTargetBubbles()
    }

    fun hideOverlay() {
        removeAllTargetViewsOnly()
        probeView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        probeView = null
        controlBarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        controlBarView = null
        isCollapsed = false
    }

    private fun createOverlayWindowParams(widthPx: Int, heightPx: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                fitInsetsSides = 0
            }
        }
    }

    private fun attachScreenOriginProbe() {
        if (probeView != null) return
        val probe = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }
        val params = createOverlayWindowParams(1, 1, touchable = false).apply {
            x = 0
            y = 0
        }
        probe.setOnApplyWindowInsetsListener { v, insets ->
            updateOriginFromProbe(v)
            insets
        }
        windowManager.addView(probe, params)
        probeView = probe
        probe.post { updateOriginFromProbe(probe) }
    }

    private fun updateOriginFromProbe(v: View?) {
        if (v == null || !v.isAttachedToWindow) return

        var displayLeftInset = 0
        var displayTopInset = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            )
            displayLeftInset = insets.left
            displayTopInset = insets.top
        } else {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            val statusBarH = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val cutout = v.rootWindowInsets?.displayCutout
                displayLeftInset = cutout?.safeInsetLeft ?: 0
                displayTopInset = maxOf(statusBarH, cutout?.safeInsetTop ?: 0)
            } else {
                displayTopInset = statusBarH
            }
        }

        val loc = IntArray(2)
        v.getLocationOnScreen(loc)

        var windowLeftInset = 0
        var windowTopInset = 0
        val rootInsets = v.rootWindowInsets
        if (rootInsets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val winInsets = rootInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                )
                windowLeftInset = winInsets.left
                windowTopInset = winInsets.top
            } else {
                @Suppress("DEPRECATION")
                windowLeftInset = rootInsets.systemWindowInsetLeft
                @Suppress("DEPRECATION")
                windowTopInset = rootInsets.systemWindowInsetTop
            }
        }

        val newOriginX = if (loc[0] > 0) {
            loc[0].toFloat()
        } else {
            (displayLeftInset - windowLeftInset).coerceAtLeast(0).toFloat()
        }
        val newOriginY = if (loc[1] > 0) {
            loc[1].toFloat()
        } else {
            (displayTopInset - windowTopInset).coerceAtLeast(0).toFloat()
        }

        if (newOriginX != originPhysicalX || newOriginY != originPhysicalY) {
            originPhysicalX = newOriginX
            originPhysicalY = newOriginY
            repositionAllTargetBubbles()
        }
    }

    private fun getRealScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        } else {
            val pt = Point()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealSize(pt)
            pt.x.coerceAtLeast(1) to pt.y.coerceAtLeast(1)
        }
    }

    private fun toggleCollapse(views: List<View>) {
        isCollapsed = !isCollapsed
        val vis = if (isCollapsed) View.GONE else View.VISIBLE
        views.forEach { it.visibility = vis }
        targetViews.forEach { it.visibility = vis }
        // '>' = Expand (when collapsed), '<' = Shrink/Close (when expanded)
        btnCollapse.setImageResource(
            if (isCollapsed) R.drawable.ic_chevron_right else R.drawable.ic_chevron_left
        )
    }

    private fun createBarVectorButton(iconRes: Int, tintColor: Int): ImageView {
        return ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tintColor)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(8.dp(), 9.dp(), 8.dp(), 9.dp())
            layoutParams = LinearLayout.LayoutParams(42.dp(), 42.dp())
        }
    }

    private fun addTarget(isSwipe: Boolean) {
        val cfg = ConfigStore.activeConfig
        val id = cfg.targets.size + 1
        val (screenW, screenH) = getRealScreenSize()
        val initX = ((screenW * 0.40f) + (id * 30f)).coerceIn(60f, (screenW - 60).coerceAtLeast(61).toFloat())
        val initY = ((screenH * 0.35f) + (id * 30f)).coerceIn(120f, (screenH - 120).coerceAtLeast(121).toFloat())
        val point = TargetPoint(
            id = id,
            x = initX,
            y = initY,
            isSwipe = isSwipe,
            endX = initX,
            endY = (initY - 220f).coerceAtLeast(120f)
        )
        cfg.targets.add(point)
        spawnTargetBubble(point, isEndNode = false)
        if (isSwipe) spawnTargetBubble(point, isEndNode = true)
    }

    private fun rebuildAllTargetBubbles() {
        removeAllTargetViewsOnly()
        val cfg = ConfigStore.activeConfig
        cfg.targets.forEachIndexed { idx, point ->
            point.id = idx + 1
            spawnTargetBubble(point, isEndNode = false)
            if (point.isSwipe) spawnTargetBubble(point, isEndNode = true)
        }
    }

    private fun repositionAllTargetBubbles() {
        val bubbleSize = getBubbleSizePx()
        val halfSize = bubbleSize / 2f
        targetBubbleRecords.forEach { rec ->
            val targetX = if (rec.isEndNode) rec.point.endX else rec.point.x
            val targetY = if (rec.isEndNode) rec.point.endY else rec.point.y
            rec.params.x = (targetX - halfSize - originPhysicalX).roundToInt()
            rec.params.y = (targetY - halfSize - originPhysicalY).roundToInt()
            if (rec.view.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(rec.view, rec.params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun setTargetBubblesTouchable(touchable: Boolean) {
        targetBubbleRecords.forEach { rec ->
            val newFlags = if (touchable) {
                rec.params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                rec.params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (rec.params.flags != newFlags) {
                rec.params.flags = newFlags
                if (rec.view.isAttachedToWindow) {
                    try {
                        windowManager.updateViewLayout(rec.view, rec.params)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun spawnTargetBubble(point: TargetPoint, isEndNode: Boolean) {
        val displayLabel = if (!point.isSwipe) "${point.id}" else if (isEndNode) "${point.id}E" else "${point.id}S"
        val bgColor = when {
            !point.enabled -> Color.parseColor("#B34B5563")
            isEndNode -> Color.parseColor("#CCFF5722")
            else -> Color.parseColor("#CC2979FF")
        }

        val bubbleSize = getBubbleSizePx()
        val halfSize = bubbleSize / 2f

        val bubble = TextView(this).apply {
            text = displayLabel
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (point.enabled) Color.WHITE else Color.parseColor("#D1D5DB"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
                setStroke(3, if (point.enabled) Color.WHITE else Color.parseColor("#9CA3AF"))
            }
            width = bubbleSize
            height = bubbleSize
        }

        val params = createOverlayWindowParams(bubbleSize, bubbleSize, touchable = !isRunning).apply {
            val targetX = if (isEndNode) point.endX else point.x
            val targetY = if (isEndNode) point.endY else point.y
            x = (targetX - halfSize - originPhysicalX).roundToInt()
            y = (targetY - halfSize - originPhysicalY).roundToInt()
        }

        val record = TargetBubbleRecord(bubble, params, point, isEndNode)

        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        var moved = false

        fun updatePointFromCurrentParams() {
            val (screenW, screenH) = getRealScreenSize()
            val physX = (params.x + halfSize + originPhysicalX)
                .coerceIn(1f, (screenW - 1).coerceAtLeast(1).toFloat())
            val physY = (params.y + halfSize + originPhysicalY)
                .coerceIn(1f, (screenH - 1).coerceAtLeast(1).toFloat())
            if (isEndNode) {
                point.endX = physX
                point.endY = physY
            } else {
                point.x = physX
                point.y = physY
            }
        }

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).roundToInt()
                    val dy = (e.rawY - touchY).roundToInt()
                    if (abs(dx) > 6 || abs(dy) > 6) {
                        moved = true
                        params.x = initX + dx
                        params.y = initY + dy
                        windowManager.updateViewLayout(bubble, params)
                        updatePointFromCurrentParams()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val dx = (e.rawX - touchX).roundToInt()
                        val dy = (e.rawY - touchY).roundToInt()
                        params.x = initX + dx
                        params.y = initY + dy
                        windowManager.updateViewLayout(bubble, params)
                        updatePointFromCurrentParams()
                    } else if (!isRunning && ConfigStore.activeConfig.targetProgrammability) {
                        openEditTargetDialog(point)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        targetViews.add(bubble)
        targetBubbleRecords.add(record)
    }

    private fun openEditTargetDialog(point: TargetPoint) {
        val cfg = ConfigStore.activeConfig

        val rootWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 18f.dpF()
                setStroke(1.dp(), Color.parseColor("#2E3444"))
            }
            setPadding(20.dp(), 18.dp(), 20.dp(), 14.dp())
        }

        val tvTitle = TextView(this).apply {
            text = "Edit Target: ${point.id}"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 14.dp())
        }
        rootWrapper.addView(tvTitle)

        val topBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 14.dp() }
        }

        val btnRemoveTarget = TextView(this).apply {
            text = "Remove"
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F59E0B"))
                cornerRadius = 10f.dpF()
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 10.dp()
            }
        }

        val btnToggleEnabled = TextView(this).apply {
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12.dp(), 12.dp(), 12.dp(), 12.dp())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun updateEnableBtnStyle() {
            btnToggleEnabled.text = if (point.enabled) "Enabled" else "Disabled"
            btnToggleEnabled.background = GradientDrawable().apply {
                setColor(if (point.enabled) Color.parseColor("#2979FF") else Color.parseColor("#4B5563"))
                cornerRadius = 10f.dpF()
            }
        }
        updateEnableBtnStyle()

        topBtnRow.addView(btnRemoveTarget)
        topBtnRow.addView(btnToggleEnabled)
        rootWrapper.addView(topBtnRow)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                340.dp()
            )
        }
        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(listContainer)
        rootWrapper.addView(scroll)

        fun renderTargetOptions() {
            listContainer.removeAllViews()

            listContainer.addView(createSectionLabel("Basic Settings:"))

            val startAtText = if (!point.startAtEnabled) {
                "Immediate"
            } else {
                DialogHelper.format12HourWithSeconds(point.startAtHour, point.startAtMinute, point.startAtSecond)
            }
            listContainer.addView(createOptionRow("Start At", startAtText) {
                DialogHelper.showScheduleClockDialog(
                    ctx = this,
                    title = "Target #${point.id} Start At",
                    description = "Schedule this specific target to execute at an exact time (Hour, Minute, Second).",
                    immediateLabel = "Start Immediately (In Sequence)",
                    specificLabel = "Start At Specific Time",
                    isImmediateInitial = !point.startAtEnabled,
                    initHour24 = point.startAtHour,
                    initMinute = point.startAtMinute,
                    initSecond = point.startAtSecond,
                    includeSeconds = true,
                    isOverlay = true
                ) { isImm, h, m, s ->
                    point.startAtEnabled = !isImm
                    point.startAtHour = h
                    point.startAtMinute = m
                    point.startAtSecond = s
                    renderTargetOptions()
                }
            })

            val effStartDelay = point.customStartDelayMs ?: cfg.startDelayMs
            val effStartRand = point.customStartDelayRandomMs ?: cfg.startDelayRandomMs
            listContainer.addView(createOptionRow("Start Delay", "$effStartDelay Millisecond(s)") {
                DialogHelper.showDelayDialog(
                    ctx = this,
                    title = "Start Delay",
                    description = "Set the delay that occurs before Target #${point.id} runs.",
                    currentDelay = effStartDelay,
                    currentRand = effStartRand,
                    isOverlay = true
                ) { d, r ->
                    point.customStartDelayMs = d
                    point.customStartDelayRandomMs = r
                    renderTargetOptions()
                }
            })

            val effEndDelay = point.customEndDelayMs ?: cfg.endDelayMs
            val effEndRand = point.customEndDelayRandomMs ?: cfg.endDelayRandomMs
            listContainer.addView(createOptionRow("End Delay", "$effEndDelay Millisecond(s)") {
                DialogHelper.showDelayDialog(
                    ctx = this,
                    title = "End Delay",
                    description = "Set the delay that occurs after Target #${point.id} runs.",
                    currentDelay = effEndDelay,
                    currentRand = effEndRand,
                    isOverlay = true
                ) { d, r ->
                    point.customEndDelayMs = d
                    point.customEndDelayRandomMs = r
                    renderTargetOptions()
                }
            })

            val effDurVal = point.customDurationValue ?: cfg.durationValue
            val effDurUnit = point.customDurationUnit ?: cfg.durationUnit
            listContainer.addView(createOptionRow("Duration", "$effDurVal ${effDurUnit.label}") {
                DialogHelper.showDurationDialog(
                    ctx = this,
                    description = "Set the click/swipe hold duration for Target #${point.id}.",
                    currentVal = effDurVal,
                    currentUnit = effDurUnit,
                    isOverlay = true
                ) { v, u ->
                    point.customDurationValue = v
                    point.customDurationUnit = u
                    point.customDurationMs = v * u.multiplierMs
                    renderTargetOptions()
                }
            })

            listContainer.addView(createSectionLabel("Advanced Settings:"))

            val repeatLabel = if (point.repeatCount <= 0) "Do Not Repeat" else "Repeat ${point.repeatCount} Time(s)"
            listContainer.addView(createOptionRow("Repeat", repeatLabel) {
                DialogHelper.showNumberDialog(
                    ctx = this,
                    title = "Repeat Target",
                    description = "Set how many extra times Target #${point.id} repeats in each cycle.\n(0 = Do Not Repeat)",
                    initialValue = point.repeatCount.toLong(),
                    isOverlay = true
                ) { count ->
                    point.repeatCount = count.toInt().coerceAtLeast(0)
                    renderTargetOptions()
                }
            })

            val disableLabel = if (point.disableAfterRuns <= 0) "Do Not Disable" else "After ${point.disableAfterRuns} Run(s)"
            listContainer.addView(createOptionRow("Disable After", disableLabel) {
                DialogHelper.showNumberDialog(
                    ctx = this,
                    title = "Disable After",
                    description = "Automatically disable Target #${point.id} after this many runs.\n(0 = Do Not Disable)",
                    initialValue = point.disableAfterRuns.toLong(),
                    isOverlay = true
                ) { runs ->
                    point.disableAfterRuns = runs.toInt().coerceAtLeast(0)
                    renderTargetOptions()
                }
            })

            listContainer.addView(createSectionLabel("Randomization:"))

            val effRadius = point.customRandomizationRadiusPx ?: cfg.randomizationRadiusPx
            listContainer.addView(createOptionRow("Randomization Radius", "$effRadius Pixel(s)") {
                DialogHelper.showNumberDialog(
                    ctx = this,
                    title = "Randomization Radius",
                    description = "Set the radius in pixels around Target #${point.id} for random touch offset.\n(0 = No Randomization)",
                    initialValue = effRadius.toLong(),
                    isOverlay = true
                ) { rad ->
                    point.customRandomizationRadiusPx = rad.toInt().coerceAtLeast(0)
                    renderTargetOptions()
                }
            })
        }

        renderTargetOptions()

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 10.dp(), 0, 0)
        }
        val btnCloseDialog = TextView(this).apply {
            text = "CLOSE"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(16.dp(), 10.dp(), 8.dp(), 6.dp())
        }
        footer.addView(btnCloseDialog)
        rootWrapper.addView(footer)

        val dialog = AlertDialog.Builder(this).setView(rootWrapper).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            )
        }

        btnRemoveTarget.setOnClickListener {
            cfg.targets.remove(point)
            rebuildAllTargetBubbles()
            dialog.dismiss()
        }

        btnToggleEnabled.setOnClickListener {
            point.enabled = !point.enabled
            updateEnableBtnStyle()
            rebuildAllTargetBubbles()
        }

        btnCloseDialog.setOnClickListener {
            rebuildAllTargetBubbles()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createSectionLabel(label: String): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, 12.dp(), 0, 6.dp())
        }
    }

    private fun createOptionRow(title: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp(), 8.dp(), 4.dp(), 8.dp())
            setOnClickListener { onClick() }
            addView(TextView(this@AutoClickerService).apply {
                text = title
                textSize = 16f
                setTextColor(Color.parseColor("#F1F3F6"))
            })
            addView(TextView(this@AutoClickerService).apply {
                text = subtitle
                textSize = 12.5f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(0, 2.dp(), 0, 0)
            })
        }
    }

    private fun removeLastTarget() {
        val cfg = ConfigStore.activeConfig
        if (cfg.targets.isEmpty()) return
        val removed = cfg.targets.removeAt(cfg.targets.lastIndex)
        val countToRemove = if (removed.isSwipe) 2 else 1
        repeat(countToRemove) {
            if (targetViews.isNotEmpty()) {
                try { windowManager.removeView(targetViews.removeAt(targetViews.lastIndex)) } catch (_: Exception) {}
            }
            if (targetBubbleRecords.isNotEmpty()) {
                targetBubbleRecords.removeAt(targetBubbleRecords.lastIndex)
            }
        }
    }

    private fun removeAllTargetViewsOnly() {
        targetViews.forEach {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        targetViews.clear()
        targetBubbleRecords.clear()
    }

    private fun clearAllTargets() {
        ConfigStore.activeConfig.targets.clear()
        removeAllTargetViewsOnly()
    }

    private fun startClickingFlow() {
        val cfg = ConfigStore.activeConfig
        if (cfg.targets.isEmpty()) {
            Toast.makeText(this, "Add at least 1 target (+) first", Toast.LENGTH_SHORT).show()
            return
        }

        clickJob?.cancel()
        clickJob = serviceScope.launch {
            if (cfg.startMode == StartMode.START_AT_SPECIFIC_TIME) {
                isWaitingSchedule = true
                withContext(Dispatchers.Main) {
                    btnPlay.setImageResource(R.drawable.ic_clock)
                    btnPlay.imageTintList = ColorStateList.valueOf(Color.parseColor("#FBBF24"))
                    Toast.makeText(
                        this@AutoClickerService,
                        "Scheduled for %02d:%02d:%02d".format(
                            cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val targetEpoch = computeTargetEpochMillis(
                    cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                )

                while (isActive && isWaitingSchedule) {
                    val remaining = targetEpoch - System.currentTimeMillis()
                    if (remaining <= 0) break
                    if (remaining > 25) delay(remaining - 20)
                }
                isWaitingSchedule = false
            }

            if (!isActive) return@launch

            isRunning = true
            withContext(Dispatchers.Main) {
                setTargetBubblesTouchable(false)
                btnPlay.setImageResource(R.drawable.ic_pause)
                btnPlay.imageTintList = ColorStateList.valueOf(Color.parseColor("#34D399"))
                if (!cfg.showActiveTargets || cfg.ghostAntiDetect) {
                    targetViews.forEach { it.visibility = View.INVISIBLE }
                }
                if (cfg.ghostAntiDetect) {
                    controlBarView?.alpha = 0.45f
                }
            }

            val startTimeMs = System.currentTimeMillis()
            val stopAtEpoch = if (cfg.stopMode == StopMode.AT_SPECIFIC_TIME) {
                computeTargetEpochMillis(cfg.stopAtHour, cfg.stopAtMinute, cfg.stopAtSecond, 0)
            } else Long.MAX_VALUE

            val perTargetRunCounts = mutableMapOf<Int, Int>()
            val perTargetScheduledDone = mutableSetOf<Int>()
            var totalCycles = 0

            while (isActive && isRunning) {
                val enabledTargets = cfg.targets.filter { it.enabled }
                if (enabledTargets.isEmpty()) break

                for (target in enabledTargets) {
                    if (!isActive || !isRunning) break
                    if (!target.enabled) continue

                    if (target.startAtEnabled && !perTargetScheduledDone.contains(target.id)) {
                        val tEpoch = computeTargetEpochMillis(
                            target.startAtHour, target.startAtMinute, target.startAtSecond, 0
                        )
                        while (isActive && isRunning) {
                            val rem = tEpoch - System.currentTimeMillis()
                            if (rem <= 0) break
                            if (rem > 25) delay(rem - 20)
                        }
                        perTargetScheduledDone.add(target.id)
                        if (!isActive || !isRunning) break
                    }

                    val executionsInCycle = 1 + target.repeatCount.coerceAtLeast(0)
                    repeat(executionsInCycle) {
                        if (!isActive || !isRunning || !target.enabled) return@repeat

                        val ghostTimingJitter = if (cfg.ghostAntiDetect) Random.nextLong(3, 18) else 0L
                        val ghostDurationJitter = if (cfg.ghostAntiDetect) Random.nextLong(5, 22) else 0L

                        val baseStart = target.customStartDelayMs ?: cfg.startDelayMs
                        val randStart = target.customStartDelayRandomMs ?: cfg.startDelayRandomMs
                        val sDelay = baseStart +
                                (if (randStart > 0) Random.nextLong(0, randStart + 1) else 0L) +
                                ghostTimingJitter
                        if (sDelay > 0) delay(sDelay)
                        if (!isActive || !isRunning) return@repeat

                        val baseDur = target.customDurationMs ?: cfg.durationMs
                        val duration = (baseDur + ghostDurationJitter).coerceAtLeast(1L)
                        val baseRad = target.customRandomizationRadiusPx ?: cfg.randomizationRadiusPx
                        val effectiveRadius = if (cfg.ghostAntiDetect) maxOf(baseRad, 6) else baseRad

                        withTimeoutOrNull(duration + 400L) {
                            performTargetGestureSuspend(target, duration, effectiveRadius, cfg.ghostAntiDetect)
                        }
                        if (!isActive || !isRunning) return@repeat

                        val baseEnd = target.customEndDelayMs ?: cfg.endDelayMs
                        val randEnd = target.customEndDelayRandomMs ?: cfg.endDelayRandomMs
                        val eDelay = baseEnd +
                                (if (randEnd > 0) Random.nextLong(0, randEnd + 1) else 0L) +
                                ghostTimingJitter
                        delay(eDelay.coerceAtLeast(12L))

                        val runsDone = (perTargetRunCounts[target.id] ?: 0) + 1
                        perTargetRunCounts[target.id] = runsDone
                        if (target.disableAfterRuns > 0 && runsDone >= target.disableAfterRuns) {
                            target.enabled = false
                            withContext(Dispatchers.Main) { rebuildAllTargetBubbles() }
                        }
                    }
                }

                if (!isActive || !isRunning) break
                totalCycles++

                val shouldStop = when (cfg.stopMode) {
                    StopMode.INDEFINITE -> false
                    StopMode.AFTER_RUN_COUNT -> totalCycles >= cfg.stopAfterRunCount
                    StopMode.AFTER_AMOUNT_OF_TIME -> (System.currentTimeMillis() - startTimeMs) >= cfg.stopAfterTimeMs
                    StopMode.AT_SPECIFIC_TIME -> System.currentTimeMillis() >= stopAtEpoch
                }
                if (shouldStop) break
            }

            withContext(Dispatchers.Main) { stopClicking() }
        }
    }

    fun stopClicking() {
        isRunning = false
        isWaitingSchedule = false
        clickJob?.cancel()
        clickJob = null
        if (::btnPlay.isInitialized) {
            btnPlay.setImageResource(R.drawable.ic_play)
            btnPlay.imageTintList = ColorStateList.valueOf(Color.WHITE)
        }
        controlBarView?.alpha = 1.0f
        setTargetBubblesTouchable(true)
        targetViews.forEach { it.visibility = if (isCollapsed) View.GONE else View.VISIBLE }
    }

    private fun computeTargetEpochMillis(hour: Int, minute: Int, second: Int, millis: Int): Long {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, second)
            set(Calendar.MILLISECOND, millis)
        }
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private suspend fun performTargetGestureSuspend(
        target: TargetPoint,
        durationMs: Long,
        radiusPx: Int,
        ghostMode: Boolean
    ) = suspendCancellableCoroutine { cont ->
        val (screenW, screenH) = getRealScreenSize()
        val maxX = (screenW - 1).coerceAtLeast(1).toFloat()
        val maxY = (screenH - 1).coerceAtLeast(1).toFloat()

        val (rawRx, rawRy) = applyRandomRadius(target.x, target.y, radiusPx)
        val rx = rawRx.coerceIn(1f, maxX)
        val ry = rawRy.coerceIn(1f, maxY)

        val path = Path().apply {
            moveTo(rx, ry)
            if (target.isSwipe) {
                val (rawEx, rawEy) = applyRandomRadius(target.endX, target.endY, radiusPx)
                val ex = rawEx.coerceIn(1f, maxX)
                val ey = rawEy.coerceIn(1f, maxY)
                lineTo(ex, ey)
            } else if (ghostMode) {
                val driftX = (rx + Random.nextFloat() * 3f - 1.5f).coerceIn(1f, maxX)
                val driftY = (ry + Random.nextFloat() * 3f - 1.5f).coerceIn(1f, maxY)
                lineTo(driftX, driftY)
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(Unit)
                }
            },
            null
        )
        if (!dispatched && cont.isActive) {
            cont.resume(Unit)
        }
    }

    private fun applyRandomRadius(x: Float, y: Float, radius: Int): Pair<Float, Float> {
        if (radius <= 0) return x to y
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val r = Random.nextDouble(0.0, radius.toDouble())
        return (x + (r * cos(angle)).toFloat()) to (y + (r * sin(angle)).toFloat())
    }
}

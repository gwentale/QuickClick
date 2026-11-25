package com.app.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
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

    private val targetViews = mutableListOf<View>()

    private data class TargetBubbleRecord(
        val view: View,
        val params: WindowManager.LayoutParams,
        val point: TargetPoint,
        val isEndNode: Boolean
    )

    private val targetBubbleRecords = mutableListOf<TargetBubbleRecord>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    private var overlayOffsetX = 0f
    private var overlayOffsetY = 0f

    @Volatile
    private var isRunning = false

    @Volatile
    private var isWaitingSchedule = false

    private var isCollapsed = false
    private var activeAppPackage: String? = null
    private var rhythmStepCounter = 0

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

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

        // 4. Open Settings
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

        // 5. Close Floating Overlay
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

        windowManager.addView(bar, controlBarParams)
        controlBarView = bar
        rebuildAllTargetBubbles()
    }

    fun hideOverlay() {
        removeAllTargetViewsOnly()
        controlBarView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        controlBarView = null
        isCollapsed = false
    }

    private fun toggleCollapse(views: List<View>) {
        isCollapsed = !isCollapsed
        val vis = if (isCollapsed) View.GONE else View.VISIBLE
        views.forEach { it.visibility = vis }
        targetViews.forEach { it.visibility = vis }
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
        val point = TargetPoint(
            id = id,
            x = 400f + (id * 30),
            y = 700f + (id * 30),
            isSwipe = isSwipe,
            endX = 400f + (id * 30),
            endY = 450f + (id * 30)
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

    private fun spawnTargetBubble(point: TargetPoint, isEndNode: Boolean) {
        val displayLabel = if (!point.isSwipe) "${point.id}" else if (isEndNode) "${point.id}E" else "${point.id}S"
        val bgColor = when {
            !point.enabled -> Color.parseColor("#B34B5563")
            isEndNode -> Color.parseColor("#CCFF5722")
            else -> Color.parseColor("#CC2979FF")
        }

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
            width = 96
            height = 96
        }

        val params = WindowManager.LayoutParams(
            96, 96,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            val targetX = if (isEndNode) point.endX else point.x
            val targetY = if (isEndNode) point.endY else point.y
            x = (targetX - 48f - overlayOffsetX).toInt()
            y = (targetY - 48f - overlayOffsetY).toInt()
        }

        val record = TargetBubbleRecord(bubble, params, point, isEndNode)

        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { v, e ->
            val w = if (v.width > 0) v.width else 96
            val h = if (v.height > 0) v.height else 96

            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    overlayOffsetX = (e.rawX - e.x) - params.x
                    overlayOffsetY = (e.rawY - e.y) - params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        params.x = initX + dx
                        params.y = initY + dy
                        windowManager.updateViewLayout(bubble, params)
                        val screenCenterX = params.x + overlayOffsetX + (w / 2f)
                        val screenCenterY = params.y + overlayOffsetY + (h / 2f)
                        if (isEndNode) {
                            point.endX = screenCenterX
                            point.endY = screenCenterY
                        } else {
                            point.x = screenCenterX
                            point.y = screenCenterY
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        val screenCenterX = e.rawX - e.x + (w / 2f)
                        val screenCenterY = e.rawY - e.y + (h / 2f)
                        if (isEndNode) {
                            point.endX = screenCenterX
                            point.endY = screenCenterY
                        } else {
                            point.x = screenCenterX
                            point.y = screenCenterY
                        }
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

    // =========================================================================
    // EXECUTION LOOP: ENHANCED GHOST ANTI-DETECT & SINGLE-BURST MODE
    // =========================================================================

    private fun startClickingFlow() {
        val cfg = ConfigStore.activeConfig
        if (cfg.targets.isEmpty()) {
            Toast.makeText(this, "Add at least 1 target (+) first", Toast.LENGTH_SHORT).show()
            return
        }

        rhythmStepCounter = 0
        clickJob?.cancel()
        clickJob = serviceScope.launch {
            val isBurstMode = cfg.singleBurstMode
            val isGhostMode = cfg.ghostAntiDetect

            if (cfg.startMode == StartMode.START_AT_SPECIFIC_TIME) {
                isWaitingSchedule = true
                withContext(Dispatchers.Main) {
                    btnPlay.setImageResource(R.drawable.ic_clock)
                    btnPlay.imageTintList = ColorStateList.valueOf(Color.parseColor("#FBBF24"))
                    val modeTag = if (isBurstMode) " [Single-Burst]" else ""
                    Toast.makeText(
                        this@AutoClickerService,
                        "Armed for %02d:%02d:%02d%s".format(
                            cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, modeTag
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val targetEpoch = computeTargetEpochMillis(
                    cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                )

                awaitPrecisionEpoch(targetEpoch, highPrecisionLeadMs = if (isBurstMode) 140L else 35L)
                isWaitingSchedule = false

                // Subtle human trigger launch reaction (4-15 ms) when Ghost Anti-Detect is enabled
                if (isActive && isGhostMode) {
                    val launchJitter = gaussianClamped(mean = 9.0, stdDev = 3.0, minVal = 4.0, maxVal = 16.0).roundToLong()
                    delay(launchJitter)
                }
            }

            if (!isActive) return@launch

            isRunning = true
            withContext(Dispatchers.Main) {
                setTargetBubblesTouchable(false)
                btnPlay.setImageResource(R.drawable.ic_pause)
                btnPlay.imageTintList = ColorStateList.valueOf(Color.parseColor("#34D399"))
                if (!cfg.showActiveTargets || isGhostMode || isBurstMode) {
                    targetViews.forEach { it.visibility = View.INVISIBLE }
                }
                if (isGhostMode || isBurstMode) {
                    controlBarView?.alpha = 0.35f
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

                    // Per-target Start At schedule
                    if (target.startAtEnabled && !perTargetScheduledDone.contains(target.id)) {
                        val tEpoch = computeTargetEpochMillis(
                            target.startAtHour, target.startAtMinute, target.startAtSecond, 0
                        )
                        awaitPrecisionEpoch(tEpoch, highPrecisionLeadMs = if (isBurstMode) 140L else 35L)
                        perTargetScheduledDone.add(target.id)
                        if (!isActive || !isRunning) break
                    }

                    val executionsInCycle = 1 + target.repeatCount.coerceAtLeast(0)
                    repeat(executionsInCycle) {
                        if (!isActive || !isRunning || !target.enabled) return@repeat

                        rhythmStepCounter++

                        // 1. Humanized Start Delay (Gaussian bell curve + subtle organic rhythm wave)
                        val baseStart = target.customStartDelayMs ?: cfg.startDelayMs
                        val randStart = target.customStartDelayRandomMs ?: cfg.startDelayRandomMs
                        val sDelay = computeHumanizedInterval(
                            baseMs = baseStart,
                            randomRangeMs = randStart,
                            ghostMode = isGhostMode,
                            addBaseMicroJitter = (baseStart > 0L)
                        )
                        if (sDelay > 0L) delay(sDelay)
                        if (!isActive || !isRunning) return@repeat

                        // 2. Humanized Touch Hold / Swipe Duration (Gaussian distribution)
                        val baseDur = target.customDurationMs ?: cfg.durationMs
                        val duration = computeHumanizedDuration(
                            baseDurationMs = baseDur,
                            isSwipe = target.isSwipe,
                            ghostMode = isGhostMode
                        )

                        // 3. Humanized 2D Gaussian Scatter + Quadratic Bezier Micro-Drift
                        val baseRad = target.customRandomizationRadiusPx ?: cfg.randomizationRadiusPx
                        val effectiveRadius = if (isGhostMode) maxOf(baseRad, 4) else baseRad

                        withTimeoutOrNull(duration + 450L) {
                            performHumanizedGestureSuspend(target, duration, effectiveRadius, isGhostMode)
                        }
                        if (!isActive || !isRunning) return@repeat

                        // 4. Humanized End Delay between sequential targets
                        val baseEnd = target.customEndDelayMs ?: cfg.endDelayMs
                        val randEnd = target.customEndDelayRandomMs ?: cfg.endDelayRandomMs
                        val rawEndDelay = computeHumanizedInterval(
                            baseMs = baseEnd,
                            randomRangeMs = randEnd,
                            ghostMode = isGhostMode,
                            addBaseMicroJitter = true
                        )
                        // In Single-Burst + Ghost mode, enforce a safe human minimum cadence
                        val minCadenceFloor = when {
                            isBurstMode && isGhostMode -> 48L
                            isGhostMode -> 24L
                            else -> 12L
                        }
                        delay(rawEndDelay.coerceAtLeast(minCadenceFloor))

                        // Per-target auto-disable counter
                        val runsDone = (perTargetRunCounts[target.id] ?: 0) + 1
                        perTargetRunCounts[target.id] = runsDone
                        if (target.disableAfterRuns > 0 && runsDone >= target.disableAfterRuns) {
                            target.enabled = false
                            withContext(Dispatchers.Main) {
                                rebuildAllTargetBubbles()
                                setTargetBubblesTouchable(false)
                            }
                        }
                    }
                }

                if (!isActive || !isRunning) break
                totalCycles++

                // Single-Burst Mode: stop strictly after 1 complete pass
                if (isBurstMode) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AutoClickerService,
                            "Single-burst completed (1 pass)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    break
                }

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

    /**
     * Hybrid scheduler wait: uses coroutine delay for the coarse interval and a short
     * Thread.yield() spin window for sub-millisecond trigger accuracy on scheduled drops.
     */
    private suspend fun awaitPrecisionEpoch(targetEpoch: Long, highPrecisionLeadMs: Long) {
        while (currentCoroutineContext().isActive && (isWaitingSchedule || isRunning)) {
            val remaining = targetEpoch - System.currentTimeMillis()
            if (remaining <= 0L) break
            if (remaining > highPrecisionLeadMs) {
                delay((remaining - (highPrecisionLeadMs - 10L)).coerceAtLeast(1L))
            } else {
                while (currentCoroutineContext().isActive &&
                    (isWaitingSchedule || isRunning) &&
                    System.currentTimeMillis() < targetEpoch
                ) {
                    Thread.yield()
                }
                break
            }
        }
    }

    // =========================================================================
    // BIOMECHANICAL GAUSSIAN & BEZIER HUMANIZATION ENGINE
    // =========================================================================

    /**
     * Standard normal variate N(0, 1) via Box-Muller transform.
     */
    private fun nextGaussian(): Double {
        var u1 = Random.nextDouble()
        while (u1 <= 1e-7) {
            u1 = Random.nextDouble()
        }
        val u2 = Random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    private fun gaussianClamped(mean: Double, stdDev: Double, minVal: Double, maxVal: Double): Double {
        if (stdDev <= 0.0) return mean.coerceIn(minVal, maxVal)
        val raw = mean + nextGaussian() * stdDev
        return raw.coerceIn(minVal, maxVal)
    }

    /**
     * Computes a human-like delay interval using Gaussian bell-curve distribution
     * plus subtle sinusoidal rhythm drift so intervals never form a flat uniform signature.
     */
    private fun computeHumanizedInterval(
        baseMs: Long,
        randomRangeMs: Long,
        ghostMode: Boolean,
        addBaseMicroJitter: Boolean
    ): Long {
        if (!ghostMode) {
            val uniformRand = if (randomRangeMs > 0L) Random.nextLong(0L, randomRangeMs + 1L) else 0L
            return (baseMs + uniformRand).coerceAtLeast(0L)
        }

        val rangedOffset = if (randomRangeMs > 0L) {
            gaussianClamped(
                mean = randomRangeMs * 0.48,
                stdDev = randomRangeMs * 0.24,
                minVal = 0.0,
                maxVal = randomRangeMs.toDouble()
            ).roundToLong()
        } else {
            0L
        }

        val microJitter = if (addBaseMicroJitter) {
            val waveDrift = sin(rhythmStepCounter * 0.65) * 3.5
            gaussianClamped(
                mean = 11.0 + waveDrift,
                stdDev = 5.5,
                minVal = 2.0,
                maxVal = 28.0
            ).roundToLong()
        } else {
            0L
        }

        return (baseMs + rangedOffset + microJitter).coerceAtLeast(0L)
    }

    /**
     * Models natural human finger dwell time (contact duration) with a Gaussian curve.
     */
    private fun computeHumanizedDuration(
        baseDurationMs: Long,
        isSwipe: Boolean,
        ghostMode: Boolean
    ): Long {
        if (!ghostMode) return baseDurationMs.coerceAtLeast(1L)

        val jitter = if (isSwipe) {
            gaussianClamped(mean = 18.0, stdDev = 9.0, minVal = 4.0, maxVal = 45.0).roundToLong()
        } else {
            gaussianClamped(mean = 14.0, stdDev = 6.5, minVal = 4.0, maxVal = 34.0).roundToLong()
        }
        return (baseDurationMs + jitter).coerceAtLeast(12L)
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

    private suspend fun performHumanizedGestureSuspend(
        target: TargetPoint,
        durationMs: Long,
        radiusPx: Int,
        ghostMode: Boolean
    ) = suspendCancellableCoroutine { cont ->
        val (rawSx, rawSy) = applyHumanizedOffset(target.x, target.y, radiusPx, ghostMode)
        val sx = rawSx.coerceAtLeast(1f)
        val sy = rawSy.coerceAtLeast(1f)
        val path = Path().apply {
            moveTo(sx, sy)
            if (target.isSwipe) {
                val (rawEx, rawEy) = applyHumanizedOffset(target.endX, target.endY, radiusPx, ghostMode)
                val ex = rawEx.coerceAtLeast(1f)
                val ey = rawEy.coerceAtLeast(1f)
                if (ghostMode) {
                    // Curved Quadratic Bezier swipe trajectory mimicking thumb/finger arc
                    val dx = ex - sx
                    val dy = ey - sy
                    val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
                    val perpX = -dy / dist
                    val perpY = dx / dist
                    val maxArc = (dist * 0.08f).coerceIn(6f, 28f)
                    val arcOffset = gaussianClamped(0.0, maxArc * 0.55, -maxArc.toDouble(), maxArc.toDouble()).toFloat()
                    val t = gaussianClamped(0.50, 0.08, 0.35, 0.65).toFloat()
                    val ctrlX = (sx + dx * t + perpX * arcOffset).coerceAtLeast(1f)
                    val ctrlY = (sy + dy * t + perpY * arcOffset).coerceAtLeast(1f)
                    quadTo(ctrlX, ctrlY, ex, ey)
                } else {
                    lineTo(ex, ey)
                }
            } else if (ghostMode) {
                // Curved Bezier micro-drift (1.2 - 4.2 px) simulating fingertip skin roll on glass
                val driftAngle = Random.nextDouble(0.0, 2.0 * PI)
                val driftDist = gaussianClamped(mean = 2.4, stdDev = 0.75, minVal = 1.1, maxVal = 4.3).toFloat()
                val ex = (sx + (driftDist * cos(driftAngle)).toFloat()).coerceAtLeast(1f)
                val ey = (sy + (driftDist * sin(driftAngle)).toFloat()).coerceAtLeast(1f)

                val bowAngle = driftAngle + (if (Random.nextBoolean()) PI / 2.0 else -PI / 2.0)
                val bowDist = driftDist * 0.45f
                val ctrlX = ((sx + ex) * 0.5f + (bowDist * cos(bowAngle)).toFloat()).coerceAtLeast(1f)
                val ctrlY = ((sy + ey) * 0.5f + (bowDist * sin(bowAngle)).toFloat()).coerceAtLeast(1f)
                quadTo(ctrlX, ctrlY, ex, ey)
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

    /**
     * Generates a touch coordinate offset around (x, y).
     * In Ghost mode, uses a 2D Gaussian distribution so touches cluster naturally near
     * the button center with rare organic outliers inside [radius].
     */
    private fun applyHumanizedOffset(
        x: Float,
        y: Float,
        radius: Int,
        ghostMode: Boolean
    ): Pair<Float, Float> {
        if (radius <= 0) return x to y
        return if (ghostMode) {
            val sigma = radius / 2.2
            val ox = gaussianClamped(0.0, sigma, -radius.toDouble(), radius.toDouble()).toFloat()
            val oy = gaussianClamped(0.0, sigma, -radius.toDouble(), radius.toDouble()).toFloat()
            val dist = hypot(ox.toDouble(), oy.toDouble()).toFloat()
            if (dist > radius) {
                val scale = radius / dist
                (x + ox * scale) to (y + oy * scale)
            } else {
                (x + ox) to (y + oy)
            }
        } else {
            val angle = Random.nextDouble(0.0, 2.0 * PI)
            val r = Random.nextDouble(0.0, radius.toDouble())
            (x + (r * cos(angle)).toFloat()) to (y + (r * sin(angle)).toFloat())
        }
    }
}

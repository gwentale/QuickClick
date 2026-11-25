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
import kotlin.math.abs
import kotlin.math.cos
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

    private val targetViews = mutableListOf<View>()
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

        // 1. Play / Pause Vector Button
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

        // 5. Collapse / Drag (>)
        val btnCollapse = createBarVectorButton(R.drawable.ic_chevron_right, Color.WHITE).apply {
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
                            toggleCollapse(listOf(btnPlay, btnAdd, btnRemove, btnSettings))
                        }
                        true
                    }
                    else -> false
                }
            }
        }

        // 6. Close Floating Overlay (✕)
        val btnClose = createBarVectorButton(R.drawable.ic_close, Color.parseColor("#F87171")).apply {
            setOnClickListener {
                stopClicking()
                hideOverlay()
            }
        }

        listOf(btnPlay, btnAdd, btnRemove, btnSettings, btnCollapse, btnClose).forEach { bar.addView(it) }

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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (isEndNode) point.endX else point.x).toInt() - 48
            y = (if (isEndNode) point.endY else point.y).toInt() - 48
        }

        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        var moved = false

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
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
                        if (isEndNode) {
                            point.endX = params.x + 48f
                            point.endY = params.y + 48f
                        } else {
                            point.x = params.x + 48f
                            point.y = params.y + 48f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && !isRunning && ConfigStore.activeConfig.targetProgrammability) {
                        openEditTargetDialog(point)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        targetViews.add(bubble)
    }

    // =========================================================================
    // FULL "EDIT TARGET: X" DIALOG (MATCHING REFERENCE + PER-TARGET START AT)
    // =========================================================================

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

        // Top 2 Action Buttons: [ Remove ] (Orange) & [ Enabled / Disabled ] (Blue)
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

            // Basic Settings:
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

            // Advanced Settings:
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

            // Randomization:
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
        }
    }

    private fun removeAllTargetViewsOnly() {
        targetViews.forEach {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        targetViews.clear()
    }

    private fun clearAllTargets() {
        ConfigStore.activeConfig.targets.clear()
        removeAllTargetViewsOnly()
    }

    // =========================================================================
    // CLICK LOOP WITH PER-TARGET START AT, REPEAT, DISABLE AFTER & SAFE STOP
    // =========================================================================

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
                computeTargetEpochMillis(cfg.stopAtHour, cfg.stopAtMinute, 0, 0)
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

                    // Per-target Start At Specific Time (with seconds)
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
        val (rx, ry) = applyRandomRadius(target.x, target.y, radiusPx)
        val path = Path().apply {
            moveTo(rx, ry)
            if (target.isSwipe) {
                val (ex, ey) = applyRandomRadius(target.endX, target.endY, radiusPx)
                lineTo(ex, ey)
            } else if (ghostMode) {
                val driftX = rx + Random.nextFloat() * 3f - 1.5f
                val driftY = ry + Random.nextFloat() * 3f - 1.5f
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

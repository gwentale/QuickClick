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
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.app.autoclicker.models.*
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
    private lateinit var btnPlay: TextView

    private val targetViews = mutableListOf<View>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    @Volatile
    private var isRunning = false

    @Volatile
    private var isWaitingSchedule = false

    private var isCollapsed = false
    private var activeAppPackage: String? = null

    // Emergency stop when screen is locked / turned off
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
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1D24"))
                setStroke(2, Color.parseColor("#383E4E"))
                cornerRadius = 26f
            }
            setPadding(8, 12, 8, 12)
        }

        // 1. Play / Pause Button (Responds immediately on tap)
        btnPlay = createBarButton("▶").apply {
            setOnClickListener {
                if (isRunning || isWaitingSchedule) {
                    stopClicking()
                } else {
                    startClickingFlow()
                }
            }
        }

        // 2. Add Target (+)
        val btnAdd = createBarButton("+").apply {
            setOnClickListener {
                if (!isRunning) addTarget(isSwipe = false)
            }
            setOnLongClickListener {
                if (!isRunning) addTarget(isSwipe = true)
                true
            }
        }

        // 3. Remove Target (−)
        val btnRemove = createBarButton("−").apply {
            setOnClickListener {
                if (!isRunning) removeLastTarget()
            }
            setOnLongClickListener {
                if (!isRunning) clearAllTargets()
                true
            }
        }

        // 4. Open Settings (⚙) — Tap: Settings, Hold: Main App
        val btnSettings = createBarButton("⚙").apply {
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
        val btnCollapse = createBarButton(">").apply {
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
        val btnClose = createBarButton("✕").apply {
            setTextColor(Color.parseColor("#F87171"))
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
    }

    fun hideOverlay() {
        clearAllTargets()
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

    private fun createBarButton(symbol: String): TextView {
        return TextView(this).apply {
            text = symbol
            textSize = 19f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(22, 13, 22, 13)
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

    private fun spawnTargetBubble(point: TargetPoint, isEndNode: Boolean) {
        val label = if (!point.isSwipe) "${point.id}" else if (isEndNode) "${point.id}S" else "${point.id}E"
        val displayLabel = if (!point.isSwipe) "${point.id}" else if (isEndNode) "${point.id}E" else "${point.id}S"

        val bubble = TextView(this).apply {
            text = displayLabel
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isEndNode) Color.parseColor("#CCFF5722") else Color.parseColor("#CC2979FF"))
                setStroke(3, Color.WHITE)
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
                        openTargetConfigOverlay(point)
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        targetViews.add(bubble)
    }

    private fun openTargetConfigOverlay(point: TargetPoint) {
        val cfg = ConfigStore.activeConfig
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#2E3444"))
            }
            setPadding(44, 32, 44, 24)
        }

        wrapper.addView(TextView(this).apply {
            text = "Target #${point.id} Settings"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        })

        fun makeField(label: String, initial: Long): EditText {
            wrapper.addView(TextView(this).apply {
                text = label; textSize = 12f; setTextColor(Color.parseColor("#8A8F9E")); setPadding(0, 10, 0, 4)
            })
            return EditText(this).apply {
                setText(initial.toString())
                setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_NUMBER
                backgroundTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
                wrapper.addView(this)
            }
        }

        val etStart = makeField("Start Delay (ms)", point.customStartDelayMs ?: cfg.startDelayMs)
        val etEnd = makeField("End Delay (ms)", point.customEndDelayMs ?: cfg.endDelayMs)
        val etDur = makeField("Duration (ms)", point.customDurationMs ?: cfg.durationMs)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 24, 0, 0)
        }
        val btnCancel = TextView(this).apply {
            text = "CANCEL"; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF")); setPadding(24, 14, 24, 14)
        }
        val btnSave = TextView(this).apply {
            text = "SAVE"; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA")); setPadding(24, 14, 24, 14)
        }
        btnRow.addView(btnCancel)
        btnRow.addView(btnSave)
        wrapper.addView(btnRow)

        val dialog = AlertDialog.Builder(this).setView(wrapper).create()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setType(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            )
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            point.customStartDelayMs = etStart.text.toString().toLongOrNull()
            point.customEndDelayMs = etEnd.text.toString().toLongOrNull()
            point.customDurationMs = etDur.text.toString().toLongOrNull()
            dialog.dismiss()
        }
        dialog.show()
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

    private fun clearAllTargets() {
        ConfigStore.activeConfig.targets.clear()
        targetViews.forEach {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        targetViews.clear()
    }

    // =========================================================================
    // CLICK LOOP WITH RELIABLE STOP & GESTURE COMPLETION
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
                    btnPlay.text = "⏳"
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
                btnPlay.text = "⏸"
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

            var runCount = 0

            while (isActive && isRunning) {
                for (target in cfg.targets.toList()) {
                    if (!isActive || !isRunning) break

                    val ghostTimingJitter = if (cfg.ghostAntiDetect) Random.nextLong(3, 18) else 0L
                    val ghostDurationJitter = if (cfg.ghostAntiDetect) Random.nextLong(5, 22) else 0L

                    val sDelay = (target.customStartDelayMs ?: cfg.startDelayMs) +
                            (if (cfg.startDelayRandomMs > 0) Random.nextLong(0, cfg.startDelayRandomMs + 1) else 0L) +
                            ghostTimingJitter
                    if (sDelay > 0) delay(sDelay)
                    if (!isActive || !isRunning) break

                    val duration = ((target.customDurationMs ?: cfg.durationMs) + ghostDurationJitter).coerceAtLeast(1L)
                    val effectiveRadius = if (cfg.ghostAntiDetect) maxOf(cfg.randomizationRadiusPx, 6) else cfg.randomizationRadiusPx

                    // Dispatch gesture and await completion (with timeout) so we never flood the UI thread
                    withTimeoutOrNull(duration + 400L) {
                        performTargetGestureSuspend(target, duration, effectiveRadius, cfg.ghostAntiDetect)
                    }
                    if (!isActive || !isRunning) break

                    val eDelay = (target.customEndDelayMs ?: cfg.endDelayMs) +
                            (if (cfg.endDelayRandomMs > 0) Random.nextLong(0, cfg.endDelayRandomMs + 1) else 0L) +
                            ghostTimingJitter
                    // Always yield at least 12ms so touch events on the Stop button are processed immediately
                    delay(eDelay.coerceAtLeast(12L))
                }

                if (!isActive || !isRunning) break
                runCount++

                val shouldStop = when (cfg.stopMode) {
                    StopMode.INDEFINITE -> false
                    StopMode.AFTER_RUN_COUNT -> runCount >= cfg.stopAfterRunCount
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
        if (::btnPlay.isInitialized) btnPlay.text = "▶"
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

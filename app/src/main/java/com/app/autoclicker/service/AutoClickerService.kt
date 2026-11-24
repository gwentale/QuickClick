package com.app.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.app.autoclicker.models.*
import com.app.autoclicker.ui.SettingsActivity
import kotlinx.coroutines.*
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AutoClickerService : AccessibilityService() {

    companion object {
        var instance: AutoClickerService? = null
    }

    private lateinit var windowManager: WindowManager
    private var controlBarView: LinearLayout? = null
    private lateinit var controlBarParams: WindowManager.LayoutParams
    private lateinit var btnPlay: TextView

    private val targetViews = mutableListOf<View>()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var clickJob: Job? = null

    private var isRunning = false
    private var isWaitingSchedule = false
    private var isCollapsed = false
    private var activeAppPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg == packageName) return

            // Fitur Auto Start: tampilkan overlay saat membuka app yang dicentang
            if (ConfigStore.autoStartPackages.contains(pkg) && controlBarView == null) {
                Handler(Looper.getMainLooper()).post { showOverlay() }
            }

            // Fitur Pause On App Change
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
        stopClicking()
        hideOverlay()
        serviceScope.cancel()
        instance = null
    }

    fun showOverlay() {
        if (controlBarView != null) return

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E6EEEEEE"))
                setStroke(3, Color.DKGRAY)
                cornerRadius = 16f
            }
            setPadding(8, 12, 8, 12)
        }

        // 1. Tombol Play / Pause / Waiting
        btnPlay = createBarButton("▶").apply {
            setOnClickListener {
                if (isRunning || isWaitingSchedule) stopClicking() else startClickingFlow()
            }
        }

        // 2. Tombol + (Tap: Click target, Hold: Swipe target)
        val btnAdd = createBarButton("+").apply {
            setOnClickListener { addTarget(isSwipe = false) }
            setOnLongClickListener {
                addTarget(isSwipe = true)
                true
            }
        }

        // 3. Tombol - (Tap: Hapus terakhir, Hold: Hapus semua)
        val btnRemove = createBarButton("−").apply {
            setOnClickListener { removeLastTarget() }
            setOnLongClickListener {
                clearAllTargets()
                true
            }
        }

        // 4. Tombol Settings
        val btnSettings = createBarButton("⚙").apply {
            setOnClickListener {
                val intent = Intent(this@AutoClickerService, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

        // 5. Tombol > (Tap: Collapse, Hold/Drag: Reposition)
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
                        if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) {
                            moved = true
                            controlBarParams.x = initialX + dx
                            controlBarParams.y = initialY + dy
                            windowManager.updateViewLayout(controlBarView, controlBarParams)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleCollapse(listOf(btnPlay, btnAdd, btnRemove, btnSettings))
                        true
                    }
                    else -> false
                }
            }
        }

        listOf(btnPlay, btnAdd, btnRemove, btnSettings, btnCollapse).forEach { bar.addView(it) }

        controlBarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 300
        }

        windowManager.addView(bar, controlBarParams)
        controlBarView = bar
    }

    fun hideOverlay() {
        clearAllTargets()
        controlBarView?.let { windowManager.removeView(it) }
        controlBarView = null
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
            textSize = 22f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setPadding(20, 14, 20, 14)
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
        val label = if (!point.isSwipe) "${point.id}" else if (isEndNode) "${point.id}E" else "${point.id}S"
        val bubble = TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isEndNode) Color.parseColor("#CCFF5722") else Color.parseColor("#CC2196F3"))
                setStroke(4, Color.WHITE)
            }
            width = 100
            height = 100
        }

        val params = WindowManager.LayoutParams(
            100, 100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (if (isEndNode) point.endX else point.x).toInt() - 50
            y = (if (isEndNode) point.endY else point.y).toInt() - 50
        }

        var initX = 0; var initY = 0; var touchX = 0f; var touchY = 0f
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x; initY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initX + (e.rawX - touchX).toInt()
                    params.y = initY + (e.rawY - touchY).toInt()
                    windowManager.updateViewLayout(bubble, params)
                    if (isEndNode) {
                        point.endX = params.x + 50f
                        point.endY = params.y + 50f
                    } else {
                        point.x = params.x + 50f
                        point.y = params.y + 50f
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        targetViews.add(bubble)
    }

    private fun removeLastTarget() {
        val cfg = ConfigStore.activeConfig
        if (cfg.targets.isEmpty()) return
        val removed = cfg.targets.removeAt(cfg.targets.lastIndex)
        val countToRemove = if (removed.isSwipe) 2 else 1
        repeat(countToRemove) {
            if (targetViews.isNotEmpty()) {
                windowManager.removeView(targetViews.removeAt(targetViews.lastIndex))
            }
        }
    }

    private fun clearAllTargets() {
        ConfigStore.activeConfig.targets.clear()
        targetViews.forEach { windowManager.removeView(it) }
        targetViews.clear()
    }

    // =========================================================================
    // LOGIKA START SCHEDULE (PRESISI MILIDETIK) & LOOP EKSEKUSI
    // =========================================================================

    private fun startClickingFlow() {
        val cfg = ConfigStore.activeConfig
        if (cfg.targets.isEmpty()) {
            Toast.makeText(this, "Tambahkan minimal 1 target (+) dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        clickJob?.cancel()
        clickJob = serviceScope.launch {
            // 1. Cek apakah mode Start At Specific Time aktif
            if (cfg.startMode == StartMode.START_AT_SPECIFIC_TIME) {
                isWaitingSchedule = true
                withContext(Dispatchers.Main) {
                    btnPlay.text = "⏳"
                    Toast.makeText(
                        this@AutoClickerService,
                        "Menunggu jam %02d:%02d:%02d.%03d".format(
                            cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val targetEpoch = computeTargetEpochMillis(
                    cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                )

                // Hybrid wait: tidur efisien hingga H-25ms, lalu spin-wait untuk presisi ms
                while (isActive) {
                    val remaining = targetEpoch - System.currentTimeMillis()
                    if (remaining <= 0) break
                    if (remaining > 25) {
                        delay(remaining - 20)
                    }
                }
                isWaitingSchedule = false
            }

            if (!isActive) return@launch

            // 2. Mulai eksekusi klik
            isRunning = true
            withContext(Dispatchers.Main) {
                btnPlay.text = "⏸"
                if (!cfg.showActiveTargets) {
                    targetViews.forEach { it.visibility = View.INVISIBLE }
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

                    val sDelay = (target.customStartDelayMs ?: cfg.startDelayMs) +
                            if (cfg.startDelayRandomMs > 0) Random.nextLong(0, cfg.startDelayRandomMs + 1) else 0L
                    if (sDelay > 0) delay(sDelay)

                    val duration = (target.customDurationMs ?: cfg.durationMs).coerceAtLeast(1L)
                    performTargetGesture(target, duration, cfg.randomizationRadiusPx)

                    val eDelay = (target.customEndDelayMs ?: cfg.endDelayMs) +
                            if (cfg.endDelayRandomMs > 0) Random.nextLong(0, cfg.endDelayRandomMs + 1) else 0L
                    delay(duration + eDelay)
                }

                runCount++

                // Cek kondisi Stop After
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

    private fun stopClicking() {
        isRunning = false
        isWaitingSchedule = false
        clickJob?.cancel()
        if (::btnPlay.isInitialized) btnPlay.text = "▶"
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
        // Jika waktu sudah lewat hari ini, jadwalkan untuk besok
        if (cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    private fun performTargetGesture(target: TargetPoint, durationMs: Long, radiusPx: Int) {
        val (rx, ry) = applyRandomRadius(target.x, target.y, radiusPx)
        val path = Path().apply {
            moveTo(rx, ry)
            if (target.isSwipe) {
                val (ex, ey) = applyRandomRadius(target.endX, target.endY, radiusPx)
                lineTo(ex, ey)
            }
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun applyRandomRadius(x: Float, y: Float, radius: Int): Pair<Float, Float> {
        if (radius <= 0) return x to y
        val angle = Random.nextDouble(0.0, 2 * Math.PI)
        val r = Random.nextDouble(0.0, radius.toDouble())
        return (x + (r * cos(angle)).toFloat()) to (y + (r * sin(angle)).toFloat())
    }
}

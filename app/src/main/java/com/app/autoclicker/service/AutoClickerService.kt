package com.app.autoclicker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
        private const val CHANNEL_ID = "quickclick_keepalive_channel"
        private const val NOTIF_ID = 1001
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
        startKeepAliveForeground()
    }

    private fun startKeepAliveForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "QuickClick Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps QuickClick accessibility service running reliably"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val notifBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = notifBuilder
            .setContentTitle("QuickClick Active")
            .setContentText("Accessibility engine is running in background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (_: Exception) {
            // Fallback if notification permission is not yet granted
        }
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
                setColor(Color.parseColor("#E61E1E24"))
                setStroke(2, Color.parseColor("#3D3D4A"))
                cornerRadius = 24f
            }
            setPadding(8, 12, 8, 12)
        }

        btnPlay = createBarButton("▶").apply {
            setOnClickListener {
                if (isRunning || isWaitingSchedule) stopClicking() else startClickingFlow()
            }
        }

        val btnAdd = createBarButton("+").apply {
            setOnClickListener { addTarget(isSwipe = false) }
            setOnLongClickListener {
                addTarget(isSwipe = true)
                true
            }
        }

        val btnRemove = createBarButton("−").apply {
            setOnClickListener { removeLastTarget() }
            setOnLongClickListener {
                clearAllTargets()
                true
            }
        }

        val btnSettings = createBarButton("⚙").apply {
            setOnClickListener {
                val intent = Intent(this@AutoClickerService, SettingsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }

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
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(22, 14, 22, 14)
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
                        point.endX = params.x + 48f
                        point.endY = params.y + 48f
                    } else {
                        point.x = params.x + 48f
                        point.y = params.y + 48f
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
                        "Scheduled for %02d:%02d:%02d.%03d".format(
                            cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val targetEpoch = computeTargetEpochMillis(
                    cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
                )

                while (isActive) {
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
                // Hide targets if Show Active Targets is disabled OR Ghost Anti-Detect is enabled
                if (!cfg.showActiveTargets || cfg.ghostAntiDetect) {
                    targetViews.forEach { it.visibility = View.INVISIBLE }
                }
                if (cfg.ghostAntiDetect) {
                    controlBarView?.alpha = 0.25f
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

                    // Ghost Anti-Detect adds subtle timing jitter if enabled
                    val ghostTimingJitter = if (cfg.ghostAntiDetect) Random.nextLong(3, 18) else 0L
                    val ghostDurationJitter = if (cfg.ghostAntiDetect) Random.nextLong(5, 22) else 0L

                    val sDelay = (target.customStartDelayMs ?: cfg.startDelayMs) +
                            (if (cfg.startDelayRandomMs > 0) Random.nextLong(0, cfg.startDelayRandomMs + 1) else 0L) +
                            ghostTimingJitter
                    if (sDelay > 0) delay(sDelay)

                    val duration = ((target.customDurationMs ?: cfg.durationMs) + ghostDurationJitter).coerceAtLeast(1L)
                    val effectiveRadius = if (cfg.ghostAntiDetect) maxOf(cfg.randomizationRadiusPx, 6) else cfg.randomizationRadiusPx

                    performTargetGesture(target, duration, effectiveRadius, cfg.ghostAntiDetect)

                    val eDelay = (target.customEndDelayMs ?: cfg.endDelayMs) +
                            (if (cfg.endDelayRandomMs > 0) Random.nextLong(0, cfg.endDelayRandomMs + 1) else 0L) +
                            ghostTimingJitter
                    delay(duration + eDelay)
                }

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

    private fun stopClicking() {
        isRunning = false
        isWaitingSchedule = false
        clickJob?.cancel()
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

    private fun performTargetGesture(
        target: TargetPoint,
        durationMs: Long,
        radiusPx: Int,
        ghostMode: Boolean
    ) {
        val (rx, ry) = applyRandomRadius(target.x, target.y, radiusPx)
        val path = Path().apply {
            moveTo(rx, ry)
            if (target.isSwipe) {
                val (ex, ey) = applyRandomRadius(target.endX, target.endY, radiusPx)
                lineTo(ex, ey)
            } else if (ghostMode) {
                // Micro finger drift (1-2px) to simulate natural human touch
                val driftX = rx + Random.nextFloat() * 3f - 1.5f
                val driftY = ry + Random.nextFloat() * 3f - 1.5f
                lineTo(driftX, driftY)
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

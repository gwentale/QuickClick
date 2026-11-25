package com.app.autoclicker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.autoclicker.R
import com.app.autoclicker.service.AutoClickerService

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: LinearLayout

    private var isOverlayDescExpanded = false
    private var isAccessibilityDescExpanded = false
    private var isBatteryDescExpanded = false

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F1115"))
            isFillViewport = true
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 22.dp(), 18.dp(), 20.dp())
        }

        scroll.addView(rootContainer)
        setContentView(scroll)

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        renderUI()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAccessibilityEnabled(): Boolean {
        return AutoClickerService.instance != null
    }

    private fun getAppVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
    }

    private fun renderUI() {
        rootContainer.removeAllViews()

        // App Header with Vector Logo
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp(), 0, 0, 12.dp())
        }
        val logoBadge = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2979FF"))
                cornerRadius = 10f.dpF()
                setStroke(1.dp(), Color.parseColor("#2979FF"))
            }
            layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { rightMargin = 10.dp() }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_target)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
                layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp())
            })
        }
        headerRow.addView(logoBadge)
        headerRow.addView(TextView(this).apply {
            text = "QuickClick"
            textSize = 25f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        rootContainer.addView(headerRow)

        addSectionLabel("SYSTEM REQUIREMENTS")

        val overlayGranted = Settings.canDrawOverlays(this)
        addStatusCard(
            iconRes = R.drawable.ic_layers,
            title = "Overlay Permission",
            statusText = if (overlayGranted) "ACTIVE" else "REQUIRED",
            isActive = overlayGranted
        ) {
            if (!overlayGranted) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                Toast.makeText(this, "Overlay permission is already active", Toast.LENGTH_SHORT).show()
            }
        }

        val accEnabled = isAccessibilityEnabled()
        addStatusCard(
            iconRes = R.drawable.ic_shield,
            title = "Accessibility Service",
            statusText = if (accEnabled) "RUNNING" else "INACTIVE",
            isActive = accEnabled
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val batteryIgnored = isBatteryOptimizationIgnored()
        addStatusCard(
            iconRes = R.drawable.ic_battery,
            title = "Keep Alive (Battery)",
            statusText = if (batteryIgnored) "UNRESTRICTED" else "OPTIMIZED",
            isActive = batteryIgnored
        ) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        addSectionLabel("CONTROLS")

        val svc = AutoClickerService.instance
        val overlayActive = svc?.isOverlayShowing() == true

        addPrimaryButton(
            iconRes = if (overlayActive) R.drawable.ic_pause else R.drawable.ic_play,
            label = if (overlayActive) "STOP MULTI-TARGET MODE" else "START MULTI-TARGET MODE",
            bgColor = if (overlayActive) "#DC2626" else "#2979FF"
        ) {
            val currentSvc = AutoClickerService.instance
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
            } else if (currentSvc == null) {
                Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
            } else {
                if (currentSvc.isOverlayShowing()) {
                    currentSvc.stopClicking()
                    currentSvc.hideOverlay()
                    Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
                } else {
                    currentSvc.showOverlay()
                    Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
                }
                renderUI()
            }
        }

        addSecondaryButton(R.drawable.ic_settings, "Multi-Target Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        addSecondaryButton(R.drawable.ic_rocket, "Auto-Open Settings") {
            startActivity(Intent(this, AutoStartActivity::class.java))
        }

        addSecondaryButton(R.drawable.ic_info, "Instructions") {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }

        // Expandable & Collapsible Permission Details Section
        addPermissionDetailsSection()

        // Bottom-Right App Version Footer
        val tvVersion = TextView(this).apply {
            text = "Version ${getAppVersionName()}"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#6B7280"))
            gravity = Gravity.END
            setPadding(0, 14.dp(), 4.dp(), 4.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(tvVersion)
    }

    private fun addPermissionDetailsSection() {
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(2.dp(), 12.dp(), 2.dp(), 6.dp())
        }

        val tvSectionTitle = TextView(this).apply {
            text = "PERMISSION DETAILS"
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvToggleAll = TextView(this).apply {
            text = if (isAllDetailsExpanded()) "Collapse All" else "Expand All"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(8.dp(), 2.dp(), 2.dp(), 2.dp())
        }

        headerRow.addView(tvSectionTitle)
        headerRow.addView(tvToggleAll)
        rootContainer.addView(headerRow)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp() }
        }

        fun updateToggleAllText() {
            tvToggleAll.text = if (isAllDetailsExpanded()) "Collapse All" else "Expand All"
        }

        val overlayItem = createExpandablePermissionItem(
            parentCard = card,
            iconRes = R.drawable.ic_layers,
            title = "Overlay Permission",
            description = "Allows QuickClick to display the floating control bar and touch target indicators over other apps so you can position and control auto-clicks anywhere on screen.",
            initialExpanded = isOverlayDescExpanded
        ) { expanded ->
            isOverlayDescExpanded = expanded
            updateToggleAllText()
        }

        val accessibilityItem = createExpandablePermissionItem(
            parentCard = card,
            iconRes = R.drawable.ic_shield,
            title = "Accessibility Service",
            description = "Starting the QuickClick accessibility service allows this application to send clicks/taps and swipes on your behalf. You can disable it at any time, but it will need to be enabled for QuickClick to run.",
            initialExpanded = isAccessibilityDescExpanded
        ) { expanded ->
            isAccessibilityDescExpanded = expanded
            updateToggleAllText()
        }

        val batteryItem = createExpandablePermissionItem(
            parentCard = card,
            iconRes = R.drawable.ic_battery,
            title = "Keep Alive (Battery)",
            description = "Disabling battery optimization prevents Android from putting QuickClick to sleep or stopping background execution during long scheduled timers and continuous click cycles.",
            initialExpanded = isBatteryDescExpanded
        ) { expanded ->
            isBatteryDescExpanded = expanded
            updateToggleAllText()
        }

        card.addView(overlayItem.container)
        card.addView(createDivider())
        card.addView(accessibilityItem.container)
        card.addView(createDivider())
        card.addView(batteryItem.container)

        headerRow.setOnClickListener {
            val expandAll = !isAllDetailsExpanded()
            TransitionManager.beginDelayedTransition(card, AutoTransition().apply { duration = 200 })

            isOverlayDescExpanded = expandAll
            isAccessibilityDescExpanded = expandAll
            isBatteryDescExpanded = expandAll

            overlayItem.setExpanded(expandAll)
            accessibilityItem.setExpanded(expandAll)
            batteryItem.setExpanded(expandAll)
            updateToggleAllText()
        }

        rootContainer.addView(card)
    }

    private data class ExpandableItemHolder(
        val container: LinearLayout,
        val setExpanded: (Boolean) -> Unit
    )

    private fun createExpandablePermissionItem(
        parentCard: LinearLayout,
        iconRes: Int,
        title: String,
        description: String,
        initialExpanded: Boolean,
        onStateChanged: (Boolean) -> Unit
    ): ExpandableItemHolder {
        var expanded = initialExpanded

        val itemContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6.dp(), 0, 6.dp())
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2.dp(), 0, 2.dp())
        }

        val ivLeftIcon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
            layoutParams = LinearLayout.LayoutParams(16.dp(), 16.dp()).apply { rightMargin = 8.dp() }
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F3F6"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val ivChevron = ImageView(this).apply {
            setImageDrawable(createChevronDrawable(Color.parseColor("#8A8F9E")))
            rotation = if (expanded) 180f else 0f
            layoutParams = LinearLayout.LayoutParams(18.dp(), 18.dp())
        }

        val tvDescription = TextView(this).apply {
            text = description
            textSize = 12.5f
            setLineSpacing(3.5f, 1.15f)
            setTextColor(Color.parseColor("#9CA3AF"))
            visibility = if (expanded) View.VISIBLE else View.GONE
            setPadding(24.dp(), 6.dp(), 4.dp(), 2.dp())
        }

        titleRow.addView(ivLeftIcon)
        titleRow.addView(tvTitle)
        titleRow.addView(ivChevron)

        itemContainer.addView(titleRow)
        itemContainer.addView(tvDescription)

        val applyState: (Boolean) -> Unit = { newState ->
            expanded = newState
            tvDescription.visibility = if (expanded) View.VISIBLE else View.GONE
            ivChevron.animate()
                .rotation(if (expanded) 180f else 0f)
                .setDuration(200)
                .start()
        }

        itemContainer.setOnClickListener {
            TransitionManager.beginDelayedTransition(parentCard, AutoTransition().apply { duration = 200 })
            applyState(!expanded)
            onStateChanged(expanded)
        }

        return ExpandableItemHolder(itemContainer, applyState)
    }

    private fun isAllDetailsExpanded(): Boolean {
        return isOverlayDescExpanded && isAccessibilityDescExpanded && isBatteryDescExpanded
    }

    private fun createChevronDrawable(color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 2f.dpF()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun draw(canvas: Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.38f)
                    lineTo(w * 0.50f, h * 0.63f)
                    lineTo(w * 0.75f, h * 0.38f)
                }
                canvas.drawPath(path, paint)
            }

            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
            }

            override fun setColorFilter(colorFilter: ColorFilter?) {
                paint.colorFilter = colorFilter
            }

            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }
    }

    private fun createDivider(): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#282C37"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.dp()
            ).apply {
                topMargin = 6.dp()
                bottomMargin = 6.dp()
            }
        }
    }

    private fun addSectionLabel(label: String) {
        rootContainer.addView(TextView(this).apply {
            text = label
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(2.dp(), 12.dp(), 0, 6.dp())
        })
    }

    private fun addStatusCard(
        iconRes: Int,
        title: String,
        statusText: String,
        isActive: Boolean,
        onClick: () -> Unit
    ) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
            setOnClickListener { onClick() }
        }

        val ivIcon = ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(
                if (isActive) Color.parseColor("#34D399") else Color.parseColor("#8A8F9E")
            )
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 12.dp() }
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F3F6"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(this).apply {
            text = statusText
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isActive) Color.parseColor("#34D399") else Color.parseColor("#FBBF24"))
            setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            background = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#1A34D399") else Color.parseColor("#1AFBBF24"))
                cornerRadius = 50f.dpF()
            }
        }

        card.addView(ivIcon)
        card.addView(tvTitle)
        card.addView(badge)
        rootContainer.addView(card)
    }

    private fun addPrimaryButton(iconRes: Int, label: String, bgColor: String, onClick: () -> Unit) {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp(), 15.dp(), 16.dp(), 15.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = 12f.dpF()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
            setOnClickListener { onClick() }
        }

        btn.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 8.dp() }
        })

        btn.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })

        rootContainer.addView(btn)
    }

    private fun addSecondaryButton(iconRes: Int, label: String, onClick: () -> Unit) {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
            setOnClickListener { onClick() }
        }

        btn.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 10.dp() }
        })

        btn.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
        })

        rootContainer.addView(btn)
    }
}

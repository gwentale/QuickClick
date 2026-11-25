package com.app.autoclicker.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
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
import com.app.autoclicker.models.ConfigStore
import com.app.autoclicker.service.AutoClickerService
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: LinearLayout

    private var isOverlayDescExpanded = false
    private var isAccessibilityDescExpanded = false
    private var isBatteryDescExpanded = false

    companion object {
        private const val AUTHOR_HANDLE = "@gwentale"
        private const val GITHUB_PROFILE_URL = "https://github.com/gwentale"
        private const val GITHUB_REPO_URL = "https://github.com/gwentale/QuickClick"
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        restoreAutoStartPackages()

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

    private fun restoreAutoStartPackages() {
        val prefs = getSharedPreferences("quickclick_autostart", Context.MODE_PRIVATE)
        val raw = prefs.getString("enabled_packages_order", null) ?: return
        runCatching {
            val arr = JSONArray(raw)
            ConfigStore.autoStartPackages.clear()
            for (i in 0 until arr.length()) {
                val pkg = arr.getString(i)
                if (pkg.isNotBlank()) ConfigStore.autoStartPackages.add(pkg)
            }
        }
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

    private fun getAppVersionCode(): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else @Suppress("DEPRECATION") pInfo.versionCode.toLong()
        } catch (_: Exception) {
            1L
        }
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyTextToClipboard(label: String, text: String, toastMsg: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
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
            text = getString(R.string.app_name)
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

        // Bottom-Right Clickable Version Footer -> Opens About App Dialog
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 12.dp(), 0, 4.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvVersion = TextView(this).apply {
            text = "Version ${getAppVersionName()}"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(10.dp(), 6.dp(), 10.dp(), 6.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#141923"))
                cornerRadius = 8f.dpF()
                setStroke(1.dp(), Color.parseColor("#252C3A"))
            }
            setOnClickListener { showAboutAppDialog() }
        }

        footerRow.addView(tvVersion)
        rootContainer.addView(footerRow)
    }

    private fun showAboutAppDialog() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 6.dp())
        }

        // App Header Badge inside Dialog
        val headerBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 12.dp(), 10.dp(), 12.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp() }
        }

        headerBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_target)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
            layoutParams = LinearLayout.LayoutParams(28.dp(), 28.dp()).apply { rightMargin = 10.dp() }
        })

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8.dp()
            }
            addView(TextView(context).apply {
                text = this@MainActivity.getString(R.string.app_name)
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
            })
            addView(TextView(context).apply {
                text = "Version ${getAppVersionName()} (Build ${getAppVersionCode()})"
                textSize = 11.5f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(0, 2.dp(), 0, 0)
            })
        }
        headerBox.addView(titleCol)

        // Mini @gwentale GitHub Pill on the Right Side of Version Card (Redirect + Copy)
        val authorPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A212E"))
                cornerRadius = 8f.dpF()
                setStroke(1.dp(), Color.parseColor("#2E384D"))
            }
        }

        val authorLinkArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp(), 6.dp(), 7.dp(), 6.dp())
            setOnClickListener { openExternalUrl(GITHUB_PROFILE_URL) }
            setOnLongClickListener {
                copyTextToClipboard("Author Handle", AUTHOR_HANDLE, "Copied $AUTHOR_HANDLE")
                true
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_github)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(13.dp(), 13.dp()).apply { rightMargin = 5.dp() }
            })
            addView(TextView(context).apply {
                text = AUTHOR_HANDLE
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E2E8F0"))
            })
        }

        val pillDivider = View(this).apply {
            setBackgroundColor(Color.parseColor("#2E384D"))
            layoutParams = LinearLayout.LayoutParams(1.dp(), 16.dp())
        }

        val btnCopyHandle = ImageView(this).apply {
            setImageDrawable(createCopyDrawable(Color.parseColor("#94A3B8")))
            setPadding(6.dp(), 6.dp(), 7.dp(), 6.dp())
            layoutParams = LinearLayout.LayoutParams(25.dp(), 25.dp())
            setOnClickListener {
                copyTextToClipboard("Author Handle", AUTHOR_HANDLE, "Copied $AUTHOR_HANDLE")
            }
        }

        authorPill.addView(authorLinkArea)
        authorPill.addView(pillDivider)
        authorPill.addView(btnCopyHandle)
        headerBox.addView(authorPill)

        content.addView(headerBox)

        // Brief Specifications Card
        content.addView(TextView(this).apply {
            text = "SPECIFICATIONS"
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(2.dp(), 0, 0, 6.dp())
        })

        val specsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12.dp(), 10.dp(), 12.dp(), 10.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp() }
        }

        val specs = listOf(
            "Engine" to "Android AccessibilityService & Floating Overlay",
            "Modes" to "Multi-Target Tap & Swipe Sequencer",
            "Scheduler" to "Exact Start/Stop Clock (Hour, Minute, Second)",
            "Anti-Detect" to "Ghost Micro-Jitter, Touch Drift & Radius Offset",
            "Backup" to "JSON Preset Save, Update, Export & Import"
        )

        specs.forEachIndexed { idx, (key, value) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, if (idx == 0) 0 else 5.dp(), 0, if (idx == specs.lastIndex) 0 else 5.dp())
                addView(TextView(context).apply {
                    text = key
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#CBD5E1"))
                })
                addView(TextView(context).apply {
                    text = value
                    textSize = 12f
                    setTextColor(Color.parseColor("#8A8F9E"))
                })
            }
            specsCard.addView(row)
        }
        content.addView(specsCard)

        // GitHub Source Code Section (Clean Vector Icon + App Name Link + Mini Copy Button)
        content.addView(TextView(this).apply {
            text = "SOURCE CODE"
            textSize = 11f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(2.dp(), 0, 0, 6.dp())
        })

        val repoCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val repoLinkArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 11.dp(), 10.dp(), 11.dp())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openExternalUrl(GITHUB_REPO_URL) }
            setOnLongClickListener {
                copyTextToClipboard("QuickClick GitHub", GITHUB_REPO_URL, "GitHub link copied")
                true
            }
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_github)
                imageTintList = ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(18.dp(), 18.dp()).apply { rightMargin = 10.dp() }
            })
            addView(TextView(context).apply {
                text = this@MainActivity.getString(R.string.app_name)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#E2E8F0"))
            })
        }

        val repoDivider = View(this).apply {
            setBackgroundColor(Color.parseColor("#282C37"))
            layoutParams = LinearLayout.LayoutParams(1.dp(), 22.dp())
        }

        val btnCopyRepo = ImageView(this).apply {
            setImageDrawable(createCopyDrawable(Color.parseColor("#94A3B8")))
            setPadding(11.dp(), 11.dp(), 12.dp(), 11.dp())
            layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp())
            setOnClickListener {
                copyTextToClipboard("QuickClick GitHub", GITHUB_REPO_URL, "GitHub link copied")
            }
        }

        repoCard.addView(repoLinkArea)
        repoCard.addView(repoDivider)
        repoCard.addView(btnCopyRepo)
        content.addView(repoCard)

        DialogHelper.showStyledDialog(this, "About App", content, null, "CLOSE", false)
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

    private fun createCopyDrawable(color: Int): Drawable {
        return object : Drawable() {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 1.4f.dpF()
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            override fun draw(canvas: Canvas) {
                val w = bounds.width().toFloat()
                val h = bounds.height().toFloat()
                val r = w * 0.12f

                // Back sheet corner
                val backPath = Path().apply {
                    moveTo(w * 0.36f, h * 0.24f)
                    lineTo(w * 0.36f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.16f)
                    lineTo(w * 0.84f, h * 0.64f)
                    lineTo(w * 0.76f, h * 0.64f)
                }
                canvas.drawPath(backPath, paint)

                // Front sheet
                val frontRect = RectF(w * 0.16f, h * 0.36f, w * 0.64f, h * 0.84f)
                canvas.drawRoundRect(frontRect, r, r, paint)
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
            gravity = Gravity.CENTER_VERTICAL
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
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 12.dp() }
        })

        btn.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        rootContainer.addView(btn)
    }

    private fun addSecondaryButton(iconRes: Int, label: String, onClick: () -> Unit) {
        val btn = LinearLayout(this).apply {
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

        btn.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 12.dp() }
        })

        btn.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        rootContainer.addView(btn)
    }
}

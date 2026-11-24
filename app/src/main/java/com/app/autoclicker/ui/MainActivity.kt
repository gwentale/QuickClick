package com.app.autoclicker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.app.autoclicker.service.AutoClickerService

class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F1115"))
        }

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 56, 48, 48)
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
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
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

    private fun renderUI() {
        rootContainer.removeAllViews()

        // App Title Header
        rootContainer.addView(TextView(this).apply {
            text = "QuickClick"
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 36)
        })

        // Section: Permissions & Stability
        addSectionLabel("SYSTEM REQUIREMENTS")

        val overlayGranted = Settings.canDrawOverlays(this)
        addStatusCard(
            title = "Overlay Permission",
            statusText = if (overlayGranted) "ACTIVE" else "REQUIRED",
            isActive = overlayGranted
        ) {
            if (!overlayGranted) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                Toast.makeText(this, "Overlay permission is already active", Toast.LENGTH_SHORT).show()
            }
        }

        val accEnabled = isAccessibilityEnabled()
        addStatusCard(
            title = "Accessibility Service",
            statusText = if (accEnabled) "RUNNING" else "INACTIVE",
            isActive = accEnabled
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val batteryIgnored = isBatteryOptimizationIgnored()
        addStatusCard(
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

        // Section: Controls
        addSectionLabel("CONTROLS")

        addPrimaryButton("START MULTI-TARGET MODE") {
            val svc = AutoClickerService.instance
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show()
            } else if (svc == null) {
                Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show()
            } else {
                svc.showOverlay()
                Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
            }
        }

        addSecondaryButton("Multi-Target Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        addSecondaryButton("Auto-Start Apps") {
            startActivity(Intent(this, AutoStartActivity::class.java))
        }
    }

    private fun addSectionLabel(label: String) {
        rootContainer.addView(TextView(this).apply {
            text = label
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(4, 24, 0, 14)
        })
    }

    private fun addStatusCard(title: String, statusText: String, isActive: Boolean, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 32, 36, 32)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            setOnClickListener { onClick() }
        }

        val tvTitle = TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(Color.parseColor("#F1F3F6"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val badge = TextView(this).apply {
            text = statusText
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isActive) Color.parseColor("#34D399") else Color.parseColor("#FBBF24"))
            setPadding(22, 10, 22, 10)
            background = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#1A34D399") else Color.parseColor("#1AFBBF24"))
                cornerRadius = 100f
            }
        }

        card.addView(tvTitle)
        card.addView(badge)
        rootContainer.addView(card)
    }

    private fun addPrimaryButton(label: String, onClick: () -> Unit) {
        val btn = TextView(this).apply {
            text = label
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 38, 32, 38)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 28f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }
            setOnClickListener { onClick() }
        }
        rootContainer.addView(btn)
    }

    private fun addSecondaryButton(label: String, onClick: () -> Unit) {
        val btn = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            setPadding(32, 34, 32, 34)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 18 }
            setOnClickListener { onClick() }
        }
        rootContainer.addView(btn)
    }
}

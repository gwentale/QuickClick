package com.app.autoclicker.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.app.autoclicker.service.AutoClickerService

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "WELCOME"
            textSize = 24f
            setPadding(0, 0, 0, 16)
        })

        root.addView(TextView(this).apply {
            text = "Berikan izin Overlay dan Accessibility Service untuk mulai menggunakan AutoClicker."
            setPadding(0, 0, 0, 32)
        })

        // Overlay Permission
        root.addView(Button(this).apply {
            text = "GRANT OVERLAY PERMISSION"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    Toast.makeText(this@MainActivity, "Izin Overlay sudah aktif", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Accessibility Service
        root.addView(Button(this).apply {
            text = "ENABLE ACCESSIBILITY SERVICE"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        })

        // Start Multi Target Mode
        root.addView(Button(this).apply {
            text = "START MULTI TARGET MODE"
            setOnClickListener {
                val svc = AutoClickerService.instance
                if (svc == null) {
                    Toast.makeText(this@MainActivity, "Aktifkan Accessibility Service dahulu", Toast.LENGTH_SHORT).show()
                } else {
                    svc.showOverlay()
                }
            }
        })

        // Settings & Auto Start
        root.addView(Button(this).apply {
            text = "SETTINGS - MULTI TARGET"
            setOnClickListener { startActivity(Intent(this@MainActivity, SettingsActivity::class.java)) }
        })

        root.addView(Button(this).apply {
            text = "SETTINGS - AUTO START APPS"
            setOnClickListener { startActivity(Intent(this@MainActivity, AutoStartActivity::class.java)) }
        })

        setContentView(root)
    }
}

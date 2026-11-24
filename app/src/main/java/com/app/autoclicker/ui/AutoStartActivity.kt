package com.app.autoclicker.ui

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.app.autoclicker.models.ConfigStore

class AutoStartActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
        }

        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

        apps.forEach { app ->
            val cb = CheckBox(this).apply {
                text = pm.getApplicationLabel(app)
                isChecked = ConfigStore.autoStartPackages.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) ConfigStore.autoStartPackages.add(app.packageName)
                    else ConfigStore.autoStartPackages.remove(app.packageName)
                }
            }
            list.addView(cb)
        }

        scroll.addView(list)
        setContentView(scroll)
    }
}

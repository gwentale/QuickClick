package com.app.autoclicker.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.models.ConfigStore
import kotlinx.coroutines.*

class AutoStartActivity : AppCompatActivity() {

    private data class AppEntry(
        val name: String,
        val packageName: String,
        val icon: Drawable
    )

    private lateinit var listContainer: LinearLayout
    private lateinit var loadingView: ProgressBar
    private val uiScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allApps: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1115"))
        }

        // Modern Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 44, 36, 20)
        }
        val btnBack = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 32, 0)
            setOnClickListener { finish() }
        }
        val tvTitle = TextView(this).apply {
            text = "Auto-Open Settings"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        topBar.addView(btnBack)
        topBar.addView(tvTitle)
        root.addView(topBar)

        // Subtitle Description
        root.addView(TextView(this).apply {
            text = "Automatically show the floating overlay when selected apps are opened."
            textSize = 12.5f
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(40, 0, 40, 24)
        })

        // Search Bar
        val searchBox = EditText(this).apply {
            hint = "Search apps..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            isSingleLine = true
            setPadding(36, 26, 36, 26)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 40
                rightMargin = 40
                bottomMargin = 20
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderAppList(s?.toString()?.trim().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(searchBox)

        // Loading Indicator
        loadingView = ProgressBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 48
            }
        }
        root.addView(loadingView)

        // Scrollable App List
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 8, 40, 48)
        }
        scroll.addView(listContainer)
        root.addView(scroll)

        setContentView(root)
        loadInstalledApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiScope.cancel()
    }

    private fun loadInstalledApps() {
        uiScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
                val seenPackages = mutableSetOf<String>()

                resolveInfos.mapNotNull { info ->
                    val pkg = info.activityInfo.packageName
                    if (pkg == packageName || !seenPackages.add(pkg)) return@mapNotNull null
                    AppEntry(
                        name = info.loadLabel(pm).toString(),
                        packageName = pkg,
                        icon = info.loadIcon(pm)
                    )
                }.sortedBy { it.name.lowercase() }
            }

            allApps = loaded
            loadingView.visibility = View.GONE
            renderAppList("")
        }
    }

    private fun renderAppList(query: String) {
        listContainer.removeAllViews()
        val filtered = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        if (filtered.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No apps found."
                textSize = 14f
                setTextColor(Color.parseColor("#8A8F9E"))
                gravity = Gravity.CENTER
                setPadding(0, 64, 0, 0)
            })
            return
        }

        filtered.forEach { app ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(28, 22, 28, 22)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A1D24"))
                    cornerRadius = 26f
                    setStroke(2, Color.parseColor("#282C37"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 14 }
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(app.icon)
                layoutParams = LinearLayout.LayoutParams(96, 96).apply {
                    rightMargin = 28
                }
            }

            val tvName = TextView(this).apply {
                text = app.name
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F1F3F6"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val sw = SwitchCompat(this).apply {
                isChecked = ConfigStore.autoStartPackages.contains(app.packageName)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) ConfigStore.autoStartPackages.add(app.packageName)
                    else ConfigStore.autoStartPackages.remove(app.packageName)
                }
            }

            // Ketuk kartu juga bisa toggle switch
            card.setOnClickListener { sw.toggle() }

            card.addView(iconView)
            card.addView(tvName)
            card.addView(sw)
            listContainer.addView(card)
        }
    }
}

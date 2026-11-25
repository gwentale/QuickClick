package com.app.autoclicker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.R
import com.app.autoclicker.models.*
import org.json.JSONArray

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1115"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 18.dp(), 16.dp(), 12.dp())
        }
        val btnBack = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_left)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(28.dp(), 28.dp()).apply { rightMargin = 12.dp() }
            setOnClickListener { finish() }
        }
        val tvHeader = TextView(this).apply {
            text = "Multi-Target Settings"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        topBar.addView(btnBack)
        topBar.addView(tvHeader)
        root.addView(topBar)

        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 4.dp(), 16.dp(), 24.dp())
        }
        scroll.addView(container)
        root.addView(scroll)

        setContentView(root)
        renderSettings()
    }

    private fun renderSettings() {
        container.removeAllViews()
        val cfg = ConfigStore.activeConfig

        addSectionHeader("START SCHEDULE")
        val startVal = if (cfg.startMode == StartMode.IMMEDIATE) {
            "Immediate"
        } else {
            DialogHelper.format12HourWithSeconds(cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond)
        }
        addSettingCard(
            title = "Start Mode",
            description = "Choose whether clicking starts immediately or at a specific time with seconds.",
            valueBadge = startVal
        ) {
            DialogHelper.showScheduleClockDialog(
                ctx = this,
                title = "Start Mode",
                description = "Select when to start clicking. Tap or drag on the clock dial to set Hours, Minutes, and Seconds.",
                immediateLabel = "Start Immediately",
                specificLabel = "Start At Specific Time",
                isImmediateInitial = cfg.startMode == StartMode.IMMEDIATE,
                initHour24 = cfg.startAtHour,
                initMinute = cfg.startAtMinute,
                initSecond = cfg.startAtSecond,
                includeSeconds = true,
                isOverlay = false
            ) { isImm, h, m, s ->
                cfg.startMode = if (isImm) StartMode.IMMEDIATE else StartMode.START_AT_SPECIFIC_TIME
                cfg.startAtHour = h
                cfg.startAtMinute = m
                cfg.startAtSecond = s
                cfg.startAtMillis = 0
                renderSettings()
            }
        }

        addSectionHeader("STEALTH & ANTI-DETECTION")
        addSwitchCard(
            title = "Ghost Anti-Detect",
            description = "Applies humanized micro-jitter, natural touch drift, and hides targets during execution.",
            checked = cfg.ghostAntiDetect
        ) { cfg.ghostAntiDetect = it }

        addSectionHeader("DEFAULT SETTINGS")
        addSettingCard(
            title = "Start Delay",
            description = "Set the default delay that occurs before each action.",
            valueBadge = "${cfg.startDelayMs} ms"
        ) {
            DialogHelper.showDelayDialog(
                ctx = this,
                title = "Start Delay",
                description = "Set the default delay that occurs before each action.",
                currentDelay = cfg.startDelayMs,
                currentRand = cfg.startDelayRandomMs,
                isOverlay = false
            ) { d, r ->
                cfg.startDelayMs = d
                cfg.startDelayRandomMs = r
                renderSettings()
            }
        }

        addSettingCard(
            title = "End Delay",
            description = "Set the default delay that occurs after each action.",
            valueBadge = "${cfg.endDelayMs} ms"
        ) {
            DialogHelper.showDelayDialog(
                ctx = this,
                title = "End Delay",
                description = "Set the default delay that occurs after each action.",
                currentDelay = cfg.endDelayMs,
                currentRand = cfg.endDelayRandomMs,
                isOverlay = false
            ) { d, r ->
                cfg.endDelayMs = d
                cfg.endDelayRandomMs = r
                renderSettings()
            }
        }

        addSettingCard(
            title = "Click/Swipe Duration",
            description = "Set the default duration of each action.",
            valueBadge = "${cfg.durationValue} ${cfg.durationUnit.label}"
        ) {
            DialogHelper.showDurationDialog(
                ctx = this,
                description = "Set the default duration of each action.\n\nFor clicks, this is how long the click will be held down.\n\nFor swipes, this controls how long the swipe will take to complete.",
                currentVal = cfg.durationValue,
                currentUnit = cfg.durationUnit,
                isOverlay = false
            ) { v, u ->
                cfg.durationValue = v
                cfg.durationUnit = u
                cfg.durationMs = v * u.multiplierMs
                renderSettings()
            }
        }

        val stopVal = when (cfg.stopMode) {
            StopMode.INDEFINITE -> "∞"
            StopMode.AFTER_AMOUNT_OF_TIME -> "${cfg.stopAfterTimeValue} ${cfg.stopAfterTimeUnit.label}"
            StopMode.AT_SPECIFIC_TIME -> DialogHelper.format12HourWithSeconds(cfg.stopAtHour, cfg.stopAtMinute, cfg.stopAtSecond)
            StopMode.AFTER_RUN_COUNT -> "${cfg.stopAfterRunCount} Run(s)"
        }
        addSettingCard(
            title = "Stop After",
            description = "Select a trigger for pausing the application.",
            valueBadge = stopVal
        ) { showStopAfterDialog() }

        addSectionHeader("RANDOMIZATION")
        addSettingCard(
            title = "Randomization Radius",
            description = "Set the radius in pixels around the target that the click/swipe coordinate will be randomly chosen from.",
            valueBadge = "${cfg.randomizationRadiusPx} px"
        ) {
            DialogHelper.showNumberDialog(
                ctx = this,
                title = "Randomization Radius",
                description = "Set the radius in pixels around the target that the click/swipe coordinate will be randomly chosen from.\n\n(0 = No Randomization)",
                initialValue = cfg.randomizationRadiusPx.toLong(),
                isOverlay = false
            ) { rad ->
                cfg.randomizationRadiusPx = rad.toInt().coerceAtLeast(0)
                renderSettings()
            }
        }

        addSectionHeader("ADVANCED SETTINGS")
        addSwitchCard(
            title = "Show Active Targets",
            description = "Keep target overlays visible on screen while actively clicking.",
            checked = cfg.showActiveTargets
        ) { cfg.showActiveTargets = it }

        addSwitchCard(
            title = "Target Programmability",
            description = "Allow individual timing configuration for each target.",
            checked = cfg.targetProgrammability
        ) { cfg.targetProgrammability = it }

        addSwitchCard(
            title = "Pause On App Change",
            description = "Automatically pause clicking when switching to another app.",
            checked = cfg.pauseOnAppChange
        ) { cfg.pauseOnAppChange = it }

        val btnManage = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 12f.dpF()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.dp() }
            setOnClickListener { showManageConfigurationsDialog() }
        }
        btnManage.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_folder)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(20.dp(), 20.dp()).apply { rightMargin = 8.dp() }
        })
        btnManage.addView(TextView(this).apply {
            text = "MANAGE CONFIGURATIONS"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        })
        container.addView(btnManage)
    }

    private fun showStopAfterDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = DialogHelper.createDarkDialogContent(this, "Select a trigger for pausing the application.")

        var selectedIdx = cfg.stopMode.ordinal
        val radioButtons = mutableListOf<RadioButton>()
        val subtitleViews = mutableListOf<TextView>()

        fun getSubtitles(): List<String> = listOf(
            "∞",
            "${cfg.stopAfterTimeValue} ${cfg.stopAfterTimeUnit.label}",
            if (cfg.stopMode == StopMode.AT_SPECIFIC_TIME) {
                DialogHelper.format12HourWithSeconds(cfg.stopAtHour, cfg.stopAtMinute, cfg.stopAtSecond)
            } else {
                "Do Not Stop By Clock (${DialogHelper.format12HourWithSeconds(cfg.stopAtHour, cfg.stopAtMinute, cfg.stopAtSecond)})"
            },
            "${cfg.stopAfterRunCount} Run(s)"
        )

        fun refreshDialogLabels() {
            val latest = getSubtitles()
            subtitleViews.forEachIndexed { i, tv -> tv.text = latest[i] }
        }

        fun selectOnly(targetIdx: Int) {
            selectedIdx = targetIdx
            cfg.stopMode = StopMode.values()[targetIdx]
            radioButtons.forEachIndexed { i, rb -> rb.isChecked = (i == targetIdx) }
            refreshDialogLabels()
            renderSettings()
        }

        val titles = listOf(
            "Run Indefinitely",
            "Stop After Amount Of Time",
            "Stop At Specific Time",
            "Stop After Run Count"
        )
        val initialSubs = getSubtitles()

        titles.forEachIndexed { idx, title ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4.dp(), 6.dp(), 4.dp(), 6.dp())
            }
            val rb = RadioButton(this).apply {
                id = idx
                text = title
                textSize = 15f
                setTextColor(Color.parseColor("#F1F3F6"))
                buttonTintList = DialogHelper.accentTint
                isChecked = (idx == selectedIdx)
            }
            radioButtons.add(rb)

            val tvSub = TextView(this).apply {
                text = initialSubs[idx]
                textSize = 12.5f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(32.dp(), 1.dp(), 0, 0)
            }
            subtitleViews.add(tvSub)

            val openOptionEditor = {
                val chosenMode = StopMode.values()[idx]
                when (chosenMode) {
                    StopMode.INDEFINITE -> {
                        selectOnly(idx)
                    }
                    StopMode.AFTER_AMOUNT_OF_TIME -> {
                        selectOnly(idx)
                        DialogHelper.showDurationDialog(
                            ctx = this,
                            description = "Set how long you want to click for.",
                            currentVal = cfg.stopAfterTimeValue,
                            currentUnit = cfg.stopAfterTimeUnit,
                            isOverlay = false
                        ) { amount, unit ->
                            cfg.stopAfterTimeValue = amount
                            cfg.stopAfterTimeUnit = unit
                            cfg.stopAfterTimeMs = amount * unit.multiplierMs
                            selectOnly(idx)
                        }
                    }
                    StopMode.AT_SPECIFIC_TIME -> {
                        DialogHelper.showScheduleClockDialog(
                            ctx = this,
                            title = "Stop At Specific Time",
                            description = "Set a time (Hour, Minute, Second) to stop clicking.",
                            immediateLabel = "Do Not Stop By Clock",
                            specificLabel = "Stop At Specific Time",
                            isImmediateInitial = (cfg.stopMode != StopMode.AT_SPECIFIC_TIME),
                            initHour24 = cfg.stopAtHour,
                            initMinute = cfg.stopAtMinute,
                            initSecond = cfg.stopAtSecond,
                            includeSeconds = true,
                            isOverlay = false
                        ) { isDoNotStopByClock, h, m, s ->
                            cfg.stopAtHour = h
                            cfg.stopAtMinute = m
                            cfg.stopAtSecond = s
                            if (isDoNotStopByClock) {
                                selectOnly(StopMode.INDEFINITE.ordinal)
                            } else {
                                selectOnly(StopMode.AT_SPECIFIC_TIME.ordinal)
                            }
                        }
                    }
                    StopMode.AFTER_RUN_COUNT -> {
                        selectOnly(idx)
                        DialogHelper.showNumberDialog(
                            ctx = this,
                            title = "Stop After Run Count",
                            description = "Set how many times you want to run through all set targets.",
                            initialValue = cfg.stopAfterRunCount.toLong(),
                            isOverlay = false
                        ) { count ->
                            cfg.stopAfterRunCount = count.toInt().coerceAtLeast(1)
                            selectOnly(idx)
                        }
                    }
                }
            }

            rb.setOnClickListener {
                if (idx == StopMode.AT_SPECIFIC_TIME.ordinal) {
                    openOptionEditor()
                } else {
                    selectOnly(idx)
                }
            }
            tvSub.setOnClickListener { openOptionEditor() }
            row.setOnClickListener { openOptionEditor() }

            row.addView(rb)
            row.addView(tvSub)
            layout.addView(row)
        }

        DialogHelper.showStyledDialog(this, "Stop After", layout, "SAVE", "CLOSE", false) {
            cfg.stopMode = StopMode.values()[selectedIdx]
            renderSettings()
        }
    }

    private fun showManageConfigurationsDialog() {
        val prefs = getSharedPreferences("quickclick_presets", Context.MODE_PRIVATE)
        val layout = DialogHelper.createDarkDialogContent(
            this,
            "Save current targets & settings as a preset, update an existing preset, or load one."
        )

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4.dp(), 0, 12.dp())
        }
        val etName = EditText(this).apply {
            hint = "Configuration name..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(12.dp(), 10.dp(), 12.dp(), 12.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 10f.dpF()
                setStroke(1.dp(), Color.parseColor("#3B4254"))
            }
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 8.dp() }
        }
        val btnSavePreset = TextView(this).apply {
            text = "SAVE NEW"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(14.dp(), 12.dp(), 14.dp(), 12.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 10f.dpF()
            }
        }
        saveRow.addView(etName)
        saveRow.addView(btnSavePreset)
        layout.addView(saveRow)

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(listBox)

        fun refreshPresets() {
            listBox.removeAllViews()
            val arr = JSONArray(prefs.getString("presets_list", "[]"))
            if (arr.length() == 0) {
                listBox.addView(TextView(this).apply {
                    text = "No saved configurations yet."
                    textSize = 13f
                    setTextColor(Color.parseColor("#6B7280"))
                    setPadding(0, 6.dp(), 0, 6.dp())
                })
                return
            }
            for (i in 0 until arr.length()) {
                val jsonStr = arr.getString(i)
                val parsed = runCatching { ClickConfig.fromJson(jsonStr) }.getOrNull() ?: continue
                val itemCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(12.dp(), 10.dp(), 8.dp(), 10.dp())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#12151C"))
                        cornerRadius = 10f.dpF()
                        setStroke(1.dp(), Color.parseColor("#282C37"))
                    }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp() }
                }

                val tvTitle = TextView(this).apply {
                    text = "${parsed.name} (${parsed.targets.size} targets)"
                    textSize = 13.5f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 6.dp() }
                }

                val btnUpdate = TextView(this).apply {
                    text = "UPDATE"
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#60A5FA"))
                    setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1A2979FF"))
                        cornerRadius = 6f.dpF()
                    }
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 6.dp() }
                    setOnClickListener {
                        ConfigStore.activeConfig.name = parsed.name
                        val updatedArr = JSONArray(prefs.getString("presets_list", "[]"))
                        if (i < updatedArr.length()) {
                            updatedArr.put(i, ConfigStore.activeConfig.toJson())
                            prefs.edit().putString("presets_list", updatedArr.toString()).apply()
                            refreshPresets()
                            Toast.makeText(
                                this@SettingsActivity,
                                "Updated: ${parsed.name} (${ConfigStore.activeConfig.targets.size} targets)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                val btnLoad = TextView(this).apply {
                    text = "LOAD"
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#34D399"))
                    setPadding(8.dp(), 5.dp(), 8.dp(), 5.dp())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1A34D399"))
                        cornerRadius = 6f.dpF()
                    }
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 4.dp() }
                    setOnClickListener {
                        ConfigStore.activeConfig = ClickConfig.fromJson(jsonStr)
                        renderSettings()
                        Toast.makeText(this@SettingsActivity, "Loaded: ${parsed.name}", Toast.LENGTH_SHORT).show()
                    }
                }

                val ivDel = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    imageTintList = ColorStateList.valueOf(Color.parseColor("#F87171"))
                    setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                    layoutParams = LinearLayout.LayoutParams(24.dp(), 24.dp())
                    setOnClickListener {
                        val updated = JSONArray()
                        for (j in 0 until arr.length()) if (j != i) updated.put(arr.getString(j))
                        prefs.edit().putString("presets_list", updated.toString()).apply()
                        refreshPresets()
                    }
                }

                itemCard.addView(tvTitle)
                itemCard.addView(btnUpdate)
                itemCard.addView(btnLoad)
                itemCard.addView(ivDel)
                listBox.addView(itemCard)
            }
        }

        btnSavePreset.setOnClickListener {
            val presetName = etName.text.toString().trim().ifEmpty { "Config ${System.currentTimeMillis() % 1000}" }
            ConfigStore.activeConfig.name = presetName
            val arr = JSONArray(prefs.getString("presets_list", "[]"))
            arr.put(ConfigStore.activeConfig.toJson())
            prefs.edit().putString("presets_list", arr.toString()).apply()
            etName.setText("")
            refreshPresets()
            Toast.makeText(this, "Saved: $presetName", Toast.LENGTH_SHORT).show()
        }

        refreshPresets()
        DialogHelper.showStyledDialog(this, "Manage Configurations", layout, "DONE", "CLOSE", false) {}
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(2.dp(), 12.dp(), 0, 6.dp())
        })
    }

    private fun addSettingCard(title: String, description: String, valueBadge: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 14.dp(), 14.dp())
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

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 10.dp(), 0)
            addView(TextView(context).apply {
                text = title
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F1F3F6"))
            })
            addView(TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(0, 3.dp(), 0, 0)
            })
        }

        val badge = TextView(this).apply {
            text = valueBadge
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(10.dp(), 5.dp(), 10.dp(), 5.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2979FF"))
                cornerRadius = 50f.dpF()
            }
        }

        card.addView(textCol)
        card.addView(badge)
        container.addView(card)
    }

    private fun addSwitchCard(title: String, description: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 14.dp(), 14.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 10.dp(), 0)
            addView(TextView(context).apply {
                text = title
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#F1F3F6"))
            })
            addView(TextView(context).apply {
                text = description
                textSize = 12f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(0, 3.dp(), 0, 0)
            })
        }

        val sw = SwitchCompat(this).apply {
            isChecked = checked
            thumbTintList = DialogHelper.accentTint
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

        card.addView(textCol)
        card.addView(sw)
        container.addView(card)
    }
}

package com.app.autoclicker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.R
import com.app.autoclicker.models.*
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout
    private var pendingExportJson: String = ""
    private var onImportCompletedCallback: (() -> Unit)? = null

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingExportJson.isNotEmpty()) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pendingExportJson.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "Exported successfully", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                    it.readText()
                } ?: ""
                if (importJsonData(raw)) {
                    renderSettings()
                    onImportCompletedCallback?.invoke()
                    Toast.makeText(this, "Imported settings & configurations", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid configuration file format", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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

        addSwitchCard(
            title = "Single-Burst Mode",
            description = "Executes exactly 1 rapid sequence pass at the scheduled time and stops immediately to prevent repetitive loop detection.",
            checked = cfg.singleBurstMode
        ) { cfg.singleBurstMode = it }

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

        addSectionHeader("CONFIGURATIONS & BACKUP")

        // Manage Configurations Button
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
            ).apply { bottomMargin = 8.dp() }
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

        // Export / Import Settings & Configurations Row
        val backupRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnExport = createActionOutlineButton(R.drawable.ic_export, "EXPORT", "#60A5FA").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 6.dp()
            }
            setOnClickListener {
                showExportOptionsDialog(
                    title = "Export Settings & Configurations",
                    fileNamePrefix = "QuickClick_Backup",
                    jsonPayload = buildFullBackupJson()
                )
            }
        }

        val btnImport = createActionOutlineButton(R.drawable.ic_import, "IMPORT", "#34D399").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 6.dp()
            }
            setOnClickListener {
                showImportOptionsDialog(onDone = null)
            }
        }

        backupRow.addView(btnExport)
        backupRow.addView(btnImport)
        container.addView(backupRow)
    }

    private fun createActionOutlineButton(iconRes: Int, label: String, accentHex: String): LinearLayout {
        val accent = Color.parseColor(accentHex)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(14.dp(), 13.dp(), 14.dp(), 13.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#2E3444"))
            }
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(accent)
                layoutParams = LinearLayout.LayoutParams(18.dp(), 18.dp()).apply { rightMargin = 8.dp() }
            })
            addView(TextView(context).apply {
                text = label
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(accent)
            })
        }
    }

    // =========================================================================
    // EXPORT & IMPORT LOGIC (FILE + CLIPBOARD JSON)
    // =========================================================================

    private fun buildFullBackupJson(): String {
        val prefs = getSharedPreferences("quickclick_presets", Context.MODE_PRIVATE)
        val presetsArr = JSONArray(prefs.getString("presets_list", "[]"))
        val pkgArr = JSONArray().apply {
            ConfigStore.autoStartPackages.forEach { put(it) }
        }
        return JSONObject().apply {
            put("format", "QuickClickBackup")
            put("version", 1)
            put("activeConfig", JSONObject(ConfigStore.activeConfig.toJson()))
            put("savedPresets", presetsArr)
            put("autoStartPackages", pkgArr)
        }.toString(2)
    }

    private fun importJsonData(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val obj = JSONObject(trimmed)
            val prefs = getSharedPreferences("quickclick_presets", Context.MODE_PRIVATE)

            if (obj.optString("format") == "QuickClickBackup") {
                // Full backup (active settings + all saved presets + auto-open packages)
                if (obj.has("activeConfig")) {
                    ConfigStore.activeConfig = ClickConfig.fromJson(obj.getJSONObject("activeConfig").toString())
                }
                if (obj.has("savedPresets")) {
                    val incomingPresets = obj.getJSONArray("savedPresets")
                    val existingPresets = JSONArray(prefs.getString("presets_list", "[]"))
                    for (i in 0 until incomingPresets.length()) {
                        val presetStr = incomingPresets.getString(i)
                        // Validate before adding
                        ClickConfig.fromJson(presetStr)
                        existingPresets.put(presetStr)
                    }
                    prefs.edit().putString("presets_list", existingPresets.toString()).apply()
                }
                if (obj.has("autoStartPackages")) {
                    val pkgs = obj.getJSONArray("autoStartPackages")
                    for (i in 0 until pkgs.length()) {
                        ConfigStore.autoStartPackages.add(pkgs.getString(i))
                    }
                }
                true
            } else {
                // Single ClickConfig JSON
                val importedCfg = ClickConfig.fromJson(trimmed)
                ConfigStore.activeConfig = importedCfg
                val existingPresets = JSONArray(prefs.getString("presets_list", "[]"))
                existingPresets.put(importedCfg.toJson())
                prefs.edit().putString("presets_list", existingPresets.toString()).apply()
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun showExportOptionsDialog(title: String, fileNamePrefix: String, jsonPayload: String) {
        val layout = DialogHelper.createDarkDialogContent(
            this,
            "Save as a .json file or copy the JSON text to clipboard."
        )

        val btnFile = createActionOutlineButton(R.drawable.ic_export, "SAVE TO .JSON FILE", "#60A5FA").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp() }
            setOnClickListener {
                pendingExportJson = jsonPayload
                val safeName = fileNamePrefix.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                createDocumentLauncher.launch("${safeName}_${System.currentTimeMillis() / 1000}.json")
            }
        }

        val btnCopy = createActionOutlineButton(R.drawable.ic_folder, "COPY JSON TO CLIPBOARD", "#34D399").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("QuickClick JSON", jsonPayload))
                Toast.makeText(this@SettingsActivity, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        layout.addView(btnFile)
        layout.addView(btnCopy)
        DialogHelper.showStyledDialog(this, title, layout, null, "CLOSE", false)
    }

    private fun showImportOptionsDialog(onDone: (() -> Unit)?) {
        val layout = DialogHelper.createDarkDialogContent(
            this,
            "Select a .json backup file from storage or paste JSON data below."
        )

        val btnPickFile = createActionOutlineButton(R.drawable.ic_import, "SELECT .JSON FILE", "#34D399").apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp() }
            setOnClickListener {
                onImportCompletedCallback = onDone
                openDocumentLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
        layout.addView(btnPickFile)

        layout.addView(TextView(this).apply {
            text = "Or paste JSON text:"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(0, 0, 0, 6.dp())
        })

        val etJson = EditText(this).apply {
            hint = "{\"format\":\"QuickClickBackup\", ...}"
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 12.5f
            minLines = 3
            maxLines = 5
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(12.dp(), 10.dp(), 12.dp(), 12.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 10f.dpF()
                setStroke(1.dp(), Color.parseColor("#3B4254"))
            }
        }
        layout.addView(etJson)

        DialogHelper.showStyledDialog(this, "Import Settings & Configurations", layout, "IMPORT JSON", "CANCEL", false) {
            val pasted = etJson.text.toString()
            if (importJsonData(pasted)) {
                renderSettings()
                onDone?.invoke()
                Toast.makeText(this, "Imported successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Invalid JSON data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // =========================================================================
    // STOP AFTER DIALOG
    // =========================================================================

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

    // =========================================================================
    // MANAGE CONFIGURATIONS DIALOG (WITH UPDATE, LOAD, EXPORT, IMPORT)
    // =========================================================================

    private fun showManageConfigurationsDialog() {
        val prefs = getSharedPreferences("quickclick_presets", Context.MODE_PRIVATE)
        val layout = DialogHelper.createDarkDialogContent(
            this,
            "Save, update, load, or export/import configuration presets."
        )

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 4.dp(), 0, 10.dp())
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
                    setPadding(0, 6.dp(), 0, 8.dp())
                })
                return
            }
            for (i in 0 until arr.length()) {
                val jsonStr = arr.getString(i)
                val parsed = runCatching { ClickConfig.fromJson(jsonStr) }.getOrNull() ?: continue
                val itemCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(10.dp(), 9.dp(), 6.dp(), 9.dp())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#12151C"))
                        cornerRadius = 10f.dpF()
                        setStroke(1.dp(), Color.parseColor("#282C37"))
                    }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dp() }
                }

                val tvTitle = TextView(this).apply {
                    text = "${parsed.name} (${parsed.targets.size})"
                    textSize = 13f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 4.dp() }
                }

                val btnUpdate = TextView(this).apply {
                    text = "UPDATE"
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#60A5FA"))
                    setPadding(6.dp(), 5.dp(), 6.dp(), 5.dp())
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1A2979FF"))
                        cornerRadius = 6f.dpF()
                    }
                    layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = 4.dp() }
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
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#34D399"))
                    setPadding(6.dp(), 5.dp(), 6.dp(), 5.dp())
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

                val ivExportItem = ImageView(this).apply {
                    setImageResource(R.drawable.ic_export)
                    imageTintList = ColorStateList.valueOf(Color.parseColor("#FBBF24"))
                    setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                    layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp()).apply { rightMargin = 2.dp() }
                    setOnClickListener {
                        showExportOptionsDialog(
                            title = "Export: ${parsed.name}",
                            fileNamePrefix = parsed.name,
                            jsonPayload = JSONObject(jsonStr).toString(2)
                        )
                    }
                }

                val ivDel = ImageView(this).apply {
                    setImageResource(R.drawable.ic_close)
                    imageTintList = ColorStateList.valueOf(Color.parseColor("#F87171"))
                    setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
                    layoutParams = LinearLayout.LayoutParams(22.dp(), 22.dp())
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
                itemCard.addView(ivExportItem)
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

        // Bottom Export All / Import Bar inside Manage Configurations
        val presetTransferRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6.dp(), 0, 0)
        }
        val btnExportAll = createActionOutlineButton(R.drawable.ic_export, "EXPORT ALL", "#60A5FA").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 6.dp() }
            setOnClickListener {
                showExportOptionsDialog(
                    title = "Export All Configurations",
                    fileNamePrefix = "QuickClick_Configs",
                    jsonPayload = buildFullBackupJson()
                )
            }
        }
        val btnImportPreset = createActionOutlineButton(R.drawable.ic_import, "IMPORT", "#34D399").apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = 6.dp() }
            setOnClickListener {
                showImportOptionsDialog { refreshPresets() }
            }
        }
        presetTransferRow.addView(btnExportAll)
        presetTransferRow.addView(btnImportPreset)
        layout.addView(presetTransferRow)

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

package com.app.autoclicker.ui

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.models.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

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
            setPadding(36, 44, 36, 28)
        }
        val btnBack = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 32, 0)
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
            setPadding(40, 8, 40, 48)
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
            "%02d:%02d:%02d.%03d".format(
                cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
            )
        }
        addSettingCard(
            title = "Start Mode",
            description = "Choose whether clicking starts immediately or at an exact scheduled time.",
            valueBadge = startVal
        ) { showStartScheduleDialog() }

        addSectionHeader("STEALTH & ANTI-DETECTION")
        addSwitchCard(
            title = "Ghost Anti-Detect",
            description = "Applies humanized micro-jitter, natural touch drift, and hides targets during execution.",
            checked = cfg.ghostAntiDetect
        ) { cfg.ghostAntiDetect = it }

        addSectionHeader("DEFAULT TIMING")
        addSettingCard(
            title = "Start Delay",
            description = "Default delay that occurs before each action.",
            valueBadge = "${cfg.startDelayMs} ms (±${cfg.startDelayRandomMs})"
        ) {
            showDelayDialog(
                title = "Start Delay",
                description = "Set the default delay that occurs before each action.",
                currentDelay = cfg.startDelayMs,
                currentRand = cfg.startDelayRandomMs
            ) { d, r ->
                cfg.startDelayMs = d; cfg.startDelayRandomMs = r; renderSettings()
            }
        }

        addSettingCard(
            title = "End Delay",
            description = "Default delay that occurs after each action.",
            valueBadge = "${cfg.endDelayMs} ms (±${cfg.endDelayRandomMs})"
        ) {
            showDelayDialog(
                title = "End Delay",
                description = "Set the default delay that occurs after each action.",
                currentDelay = cfg.endDelayMs,
                currentRand = cfg.endDelayRandomMs
            ) { d, r ->
                cfg.endDelayMs = d; cfg.endDelayRandomMs = r; renderSettings()
            }
        }

        addSettingCard(
            title = "Click/Swipe Duration",
            description = "How long each click is held down or how long a swipe takes to complete.",
            valueBadge = "${cfg.durationMs} ms"
        ) { showDurationDialog() }

        val stopVal = when (cfg.stopMode) {
            StopMode.INDEFINITE -> "∞"
            StopMode.AFTER_AMOUNT_OF_TIME -> "${cfg.stopAfterTimeMs / 1000}s"
            StopMode.AT_SPECIFIC_TIME -> "%02d:%02d".format(cfg.stopAtHour, cfg.stopAtMinute)
            StopMode.AFTER_RUN_COUNT -> "${cfg.stopAfterRunCount} Runs"
        }
        addSettingCard(
            title = "Stop After",
            description = "Select a trigger for automatically pausing the application.",
            valueBadge = stopVal
        ) { showStopAfterDialog() }

        addSectionHeader("RANDOMIZATION")
        addSettingCard(
            title = "Randomization Radius",
            description = "Radius in pixels around the target where click/swipe coordinates are randomly chosen.",
            valueBadge = "${cfg.randomizationRadiusPx} px"
        ) {
            showRadiusDialog(cfg.randomizationRadiusPx.toLong()) {
                cfg.randomizationRadiusPx = it.toInt(); renderSettings()
            }
        }

        addSectionHeader("ADVANCED")
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
    }

    // =========================================================================
    // DIALOGS WITH DESCRIPTIONS
    // =========================================================================

    private fun showStartScheduleDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDialogLayout(
            "Select when the auto-clicker should begin after pressing Play.\n\nUse 24-hour format (HH:mm:ss.SSS) for millisecond-accurate scheduled starts."
        )

        val rg = RadioGroup(this)
        val rbNow = RadioButton(this).apply { text = "Start Immediately"; id = 1 }
        val rbTime = RadioButton(this).apply { text = "Start At Specific Time"; id = 2 }
        rg.addView(rbNow); rg.addView(rbTime)
        rg.check(if (cfg.startMode == StartMode.IMMEDIATE) 1 else 2)
        layout.addView(rg)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val etH = EditText(this).apply { hint = "HH"; setText("%02d".format(cfg.startAtHour)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etM = EditText(this).apply { hint = "mm"; setText("%02d".format(cfg.startAtMinute)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etS = EditText(this).apply { hint = "ss"; setText("%02d".format(cfg.startAtSecond)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etMs = EditText(this).apply { hint = "SSS"; setText("%03d".format(cfg.startAtMillis)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1.2f) }
        listOf(etH, etM, etS, etMs).forEach { row.addView(it) }
        layout.addView(row)

        AlertDialog.Builder(this)
            .setTitle("Start Schedule")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                cfg.startMode = if (rg.checkedRadioButtonId == 1) StartMode.IMMEDIATE else StartMode.START_AT_SPECIFIC_TIME
                cfg.startAtHour = (etH.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23)
                cfg.startAtMinute = (etM.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
                cfg.startAtSecond = (etS.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
                cfg.startAtMillis = (etMs.text.toString().toIntOrNull() ?: 0).coerceIn(0, 999)
                renderSettings()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showDelayDialog(
        title: String,
        description: String,
        currentDelay: Long,
        currentRand: Long,
        onSave: (Long, Long) -> Unit
    ) {
        val layout = createDialogLayout(description)

        layout.addView(TextView(this).apply { text = "Delay (Milliseconds)"; textSize = 12f; setTextColor(Color.parseColor("#2979FF")); setPadding(0, 16, 0, 4) })
        val etDelay = EditText(this).apply { setText(currentDelay.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(etDelay)

        layout.addView(TextView(this).apply { text = "Randomization Range (Milliseconds)"; textSize = 12f; setTextColor(Color.parseColor("#2979FF")); setPadding(0, 16, 0, 4) })
        val etRand = EditText(this).apply { setText(currentRand.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(etRand)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                onSave(etDelay.text.toString().toLongOrNull() ?: 0L, etRand.text.toString().toLongOrNull() ?: 0L)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showDurationDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDialogLayout(
            "Set the default duration of each action.\n\nFor clicks, this is how long the click will be held down.\n\nFor swipes, this controls how long the swipe will take to complete."
        )

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 16, 0, 0) }
        val etVal = EditText(this).apply {
            setText(cfg.durationMs.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, TimeUnitType.values().map { it.label })
        }
        row.addView(etVal); row.addView(spinner)
        layout.addView(row)

        AlertDialog.Builder(this)
            .setTitle("Click/Swipe Duration")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val unit = TimeUnitType.values()[spinner.selectedItemPosition]
                cfg.durationMs = (etVal.text.toString().toLongOrNull() ?: 25L) * unit.multiplierMs
                cfg.durationUnit = unit
                renderSettings()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showStopAfterDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDialogLayout("Select a trigger for pausing the application.")

        val rg = RadioGroup(this).apply { setPadding(0, 12, 0, 0) }
        val options = listOf(
            "Run Indefinitely (∞)",
            "Stop After Amount Of Time (${cfg.stopAfterTimeMs / 1000}s)",
            "Stop At Specific Time (%02d:%02d)".format(cfg.stopAtHour, cfg.stopAtMinute),
            "Stop After Run Count (${cfg.stopAfterRunCount} Runs)"
        )
        options.forEachIndexed { index, label ->
            rg.addView(RadioButton(this).apply { text = label; id = index; setPadding(8, 14, 8, 14) })
        }
        rg.check(cfg.stopMode.ordinal)
        layout.addView(rg)

        AlertDialog.Builder(this)
            .setTitle("Stop After")
            .setView(layout)
            .setPositiveButton("NEXT") { _, _ ->
                cfg.stopMode = StopMode.values()[rg.checkedRadioButtonId]
                when (cfg.stopMode) {
                    StopMode.AFTER_AMOUNT_OF_TIME -> showSimpleNumberDialog("Stop After Amount Of Time", "Enter duration in seconds:", cfg.stopAfterTimeMs / 1000) {
                        cfg.stopAfterTimeMs = it * 1000L; renderSettings()
                    }
                    StopMode.AFTER_RUN_COUNT -> showSimpleNumberDialog("Stop After Run Count", "Enter total number of cycles:", cfg.stopAfterRunCount.toLong()) {
                        cfg.stopAfterRunCount = it.toInt(); renderSettings()
                    }
                    StopMode.AT_SPECIFIC_TIME -> showStopTimePicker()
                    StopMode.INDEFINITE -> renderSettings()
                }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showRadiusDialog(current: Long, onSave: (Long) -> Unit) {
        val layout = createDialogLayout(
            "Set the radius in pixels around the target that the click/swipe coordinate will be randomly chosen from.\n\n(0 = No Randomization)"
        )
        val et = EditText(this).apply { setText(current.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(et)

        AlertDialog.Builder(this)
            .setTitle("Randomization Radius")
            .setView(layout)
            .setPositiveButton("SAVE") { _, _ -> onSave(et.text.toString().toLongOrNull() ?: 0L) }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun showSimpleNumberDialog(title: String, desc: String, current: Long, onSave: (Long) -> Unit) {
        val layout = createDialogLayout(desc)
        val et = EditText(this).apply { setText(current.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(et)
        AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setPositiveButton("SAVE") { _, _ -> onSave(et.text.toString().toLongOrNull() ?: 0L) }
            .setNegativeButton("CANCEL", null).show()
    }

    private fun showStopTimePicker() {
        val cfg = ConfigStore.activeConfig
        val layout = createDialogLayout("Enter the 24-hour time (HH:mm) to automatically stop.")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val etH = EditText(this).apply { hint = "HH"; setText("%02d".format(cfg.stopAtHour)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etM = EditText(this).apply { hint = "mm"; setText("%02d".format(cfg.stopAtMinute)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        row.addView(etH); row.addView(etM)
        layout.addView(row)

        AlertDialog.Builder(this).setTitle("Stop At Specific Time").setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                cfg.stopAtHour = (etH.text.toString().toIntOrNull() ?: 12).coerceIn(0, 23)
                cfg.stopAtMinute = (etM.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
                renderSettings()
            }
            .setNegativeButton("CANCEL", null).show()
    }

    // =========================================================================
    // MODERN DARK UI BUILDERS
    // =========================================================================

    private fun createDialogLayout(description: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 28, 56, 16)
            addView(TextView(context).apply {
                text = description
                textSize = 13.5f
                setPadding(0, 0, 0, 12)
            })
        }
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(8, 28, 0, 12)
        })
    }

    private fun addSettingCard(title: String, description: String, valueBadge: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 30, 32, 30)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 26f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
            setOnClickListener { onClick() }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 20, 0)
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
                setPadding(0, 6, 0, 0)
            })
        }

        val badge = TextView(this).apply {
            text = valueBadge
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(22, 10, 22, 10)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2979FF"))
                cornerRadius = 100f
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
            setPadding(36, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 26f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(0, 0, 20, 0)
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
                setPadding(0, 6, 0, 0)
            })
        }

        val sw = SwitchCompat(this).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

        card.addView(textCol)
        card.addView(sw)
        container.addView(card)
    }
}

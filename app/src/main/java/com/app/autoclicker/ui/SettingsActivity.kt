package com.app.autoclicker.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.app.autoclicker.models.*

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 32)
        }
        scroll.addView(container)
        setContentView(scroll)
        renderSettings()
    }

    private fun renderSettings() {
        container.removeAllViews()
        val cfg = ConfigStore.activeConfig

        addSectionHeader("Schedule (Fitur Baru):")
        val startDesc = if (cfg.startMode == StartMode.IMMEDIATE) {
            "Immediate (Langsung mulai saat klik Play)"
        } else {
            "Start At %02d:%02d:%02d.%03d".format(
                cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond, cfg.startAtMillis
            )
        }
        addClickableItem("Start Mode", startDesc) { showStartScheduleDialog() }

        addSectionHeader("Default Settings:")
        addClickableItem("Start Delay", "${cfg.startDelayMs} ms (Random ±${cfg.startDelayRandomMs} ms)") {
            showDelayDialog("Start Delay", cfg.startDelayMs, cfg.startDelayRandomMs) { d, r ->
                cfg.startDelayMs = d; cfg.startDelayRandomMs = r; renderSettings()
            }
        }
        addClickableItem("End Delay", "${cfg.endDelayMs} ms (Random ±${cfg.endDelayRandomMs} ms)") {
            showDelayDialog("End Delay", cfg.endDelayMs, cfg.endDelayRandomMs) { d, r ->
                cfg.endDelayMs = d; cfg.endDelayRandomMs = r; renderSettings()
            }
        }
        addClickableItem("Click/Swipe Duration", "${cfg.durationMs} Millisecond(s)") {
            showDurationDialog()
        }

        val stopDesc = when (cfg.stopMode) {
            StopMode.INDEFINITE -> "∞ (Run Indefinitely)"
            StopMode.AFTER_AMOUNT_OF_TIME -> "After ${cfg.stopAfterTimeMs / 1000} Second(s)"
            StopMode.AT_SPECIFIC_TIME -> "At %02d:%02d".format(cfg.stopAtHour, cfg.stopAtMinute)
            StopMode.AFTER_RUN_COUNT -> "After ${cfg.stopAfterRunCount} Run(s)"
        }
        addClickableItem("Stop After", stopDesc) { showStopAfterDialog() }

        addSectionHeader("Randomization:")
        addClickableItem("Randomization Radius", "${cfg.randomizationRadiusPx} Pixel(s)") {
            showSingleNumberDialog("Randomization Radius (px)", cfg.randomizationRadiusPx.toLong()) {
                cfg.randomizationRadiusPx = it.toInt(); renderSettings()
            }
        }

        addSectionHeader("Advanced Settings:")
        addCheckboxItem("Show Active Targets", cfg.showActiveTargets) { cfg.showActiveTargets = it }
        addCheckboxItem("Target Programmability", cfg.targetProgrammability) { cfg.targetProgrammability = it }
        addCheckboxItem("Pause On App Change", cfg.pauseOnAppChange) { cfg.pauseOnAppChange = it }
    }

    private fun showStartScheduleDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 16)
        }

        val rg = RadioGroup(this)
        val rbNow = RadioButton(this).apply { text = "Start Immediately"; id = 1 }
        val rbTime = RadioButton(this).apply { text = "Start At Specific Time (HH:mm:ss.SSS)"; id = 2 }
        rg.addView(rbNow); rg.addView(rbTime)
        rg.check(if (cfg.startMode == StartMode.IMMEDIATE) 1 else 2)
        layout.addView(rg)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
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
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showStopAfterDialog() {
        val cfg = ConfigStore.activeConfig
        val options = arrayOf("Run Indefinitely", "Stop After Amount Of Time (detik)", "Stop At Specific Time (HH:mm)", "Stop After Run Count")
        val selected = cfg.stopMode.ordinal

        AlertDialog.Builder(this)
            .setTitle("Stop After")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                cfg.stopMode = StopMode.values()[which]
                dialog.dismiss()
                when (cfg.stopMode) {
                    StopMode.AFTER_AMOUNT_OF_TIME -> showSingleNumberDialog("Durasi (Detik)", cfg.stopAfterTimeMs / 1000) {
                        cfg.stopAfterTimeMs = it * 1000L; renderSettings()
                    }
                    StopMode.AFTER_RUN_COUNT -> showSingleNumberDialog("Jumlah Siklus (Run Count)", cfg.stopAfterRunCount.toLong()) {
                        cfg.stopAfterRunCount = it.toInt(); renderSettings()
                    }
                    StopMode.AT_SPECIFIC_TIME -> showStopTimePicker()
                    StopMode.INDEFINITE -> renderSettings()
                }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun showStopTimePicker() {
        val cfg = ConfigStore.activeConfig
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(48, 24, 48, 16) }
        val etH = EditText(this).apply { hint = "HH"; setText("%02d".format(cfg.stopAtHour)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val etM = EditText(this).apply { hint = "mm"; setText("%02d".format(cfg.stopAtMinute)); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        row.addView(etH); row.addView(etM)
        AlertDialog.Builder(this).setTitle("Stop At Specific Time").setView(row)
            .setPositiveButton("SAVE") { _, _ ->
                cfg.stopAtHour = (etH.text.toString().toIntOrNull() ?: 12).coerceIn(0, 23)
                cfg.stopAtMinute = (etM.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
                renderSettings()
            }.show()
    }

    private fun showDelayDialog(title: String, currentDelay: Long, currentRand: Long, onSave: (Long, Long) -> Unit) {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 16) }
        val etDelay = EditText(this).apply { hint = "Delay (ms)"; setText(currentDelay.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        val etRand = EditText(this).apply { hint = "Randomization Range (ms)"; setText(currentRand.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(etDelay); layout.addView(etRand)
        AlertDialog.Builder(this).setTitle(title).setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                onSave(etDelay.text.toString().toLongOrNull() ?: 0L, etRand.text.toString().toLongOrNull() ?: 0L)
            }.setNegativeButton("CLOSE", null).show()
    }

    private fun showDurationDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(48, 24, 48, 16) }
        val etVal = EditText(this).apply { setText(cfg.durationMs.toString()); inputType = InputType.TYPE_CLASS_NUMBER; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, TimeUnitType.values().map { it.label })
        }
        layout.addView(etVal); layout.addView(spinner)
        AlertDialog.Builder(this).setTitle("Click/Swipe Duration").setView(layout)
            .setPositiveButton("SAVE") { _, _ ->
                val unit = TimeUnitType.values()[spinner.selectedItemPosition]
                cfg.durationMs = (etVal.text.toString().toLongOrNull() ?: 25L) * unit.multiplierMs
                cfg.durationUnit = unit
                renderSettings()
            }.setNegativeButton("CLOSE", null).show()
    }

    private fun showSingleNumberDialog(title: String, current: Long, onSave: (Long) -> Unit) {
        val et = EditText(this).apply { setText(current.toString()); inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("SAVE") { _, _ -> onSave(et.text.toString().toLongOrNull() ?: 0L) }
            .setNegativeButton("CLOSE", null).show()
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title; textSize = 14f; setTextColor(0xFF1976D2.toInt()); setPadding(0, 28, 0, 8)
        })
    }

    private fun addClickableItem(title: String, subtitle: String, onClick: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
            setOnClickListener { onClick() }
            addView(TextView(context).apply { text = title; textSize = 18f })
            addView(TextView(context).apply { text = subtitle; textSize = 14f; setTextColor(0xFF666666.toInt()) })
        }
        container.addView(item)
    }

    private fun addCheckboxItem(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
        container.addView(CheckBox(this).apply {
            text = title; isChecked = checked; textSize = 16f
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        })
    }
}

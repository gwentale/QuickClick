package com.app.autoclicker.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.models.*
import org.json.JSONArray
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SettingsActivity : AppCompatActivity() {

    private lateinit var container: LinearLayout

    private val accentTint = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.parseColor("#2979FF"), Color.parseColor("#6B7280"))
    )

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
            setPadding(40, 44, 40, 24)
        }
        val btnBack = TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 28, 0)
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
            setPadding(40, 4, 40, 48)
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
            format12HourWithSeconds(cfg.startAtHour, cfg.startAtMinute, cfg.startAtSecond)
        }
        addSettingCard(
            title = "Start Mode",
            description = "Choose whether clicking starts immediately or at a specific time with seconds.",
            valueBadge = startVal
        ) { showStartModeClockDialog() }

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
            description = "Set the default delay that occurs after each action.",
            valueBadge = "${cfg.endDelayMs} ms"
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
            description = "Set the default duration of each action.",
            valueBadge = "${cfg.durationValue} ${cfg.durationUnit.label}"
        ) { showDurationDialog() }

        val stopVal = when (cfg.stopMode) {
            StopMode.INDEFINITE -> "∞"
            StopMode.AFTER_AMOUNT_OF_TIME -> "${cfg.stopAfterTimeValue} ${cfg.stopAfterTimeUnit.label}"
            StopMode.AT_SPECIFIC_TIME -> format12Hour(cfg.stopAtHour, cfg.stopAtMinute)
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
            showRadiusDialog(cfg.randomizationRadiusPx.toLong()) {
                cfg.randomizationRadiusPx = it.toInt(); renderSettings()
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

        val btnManage = TextView(this).apply {
            text = "MANAGE CONFIGURATIONS"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(36, 34, 36, 34)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 24f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnClickListener { showManageConfigurationsDialog() }
        }
        container.addView(btnManage)
    }

    // =========================================================================
    // START MODE DIALOG (ANALOG CLOCK DIAL WITH HOUR, MINUTE & SECONDS)
    // =========================================================================

    private fun showStartModeClockDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent(
            "Select when to start clicking. Tap or drag on the clock dial to set Hours, Minutes, and Seconds."
        )

        val rg = RadioGroup(this).apply { setPadding(0, 4, 0, 14) }
        val rbNow = RadioButton(this).apply {
            id = 1
            text = "Start Immediately"
            textSize = 14.5f
            setTextColor(Color.parseColor("#F1F3F6"))
            buttonTintList = accentTint
        }
        val rbSpecific = RadioButton(this).apply {
            id = 2
            text = "Start At Specific Time"
            textSize = 14.5f
            setTextColor(Color.parseColor("#F1F3F6"))
            buttonTintList = accentTint
        }
        rg.addView(rbNow)
        rg.addView(rbSpecific)
        rg.check(if (cfg.startMode == StartMode.IMMEDIATE) 1 else 2)
        layout.addView(rg)

        var hour24 = cfg.startAtHour
        var minuteVal = cfg.startAtMinute
        var secondVal = cfg.startAtSecond
        var activeDialMode = CircularClockView.DialMode.HOUR

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val tvHour = TextView(this).apply { textSize = 32f; setTypeface(null, Typeface.BOLD) }
        val tvColon1 = TextView(this).apply { text = ":"; textSize = 30f; setTextColor(Color.parseColor("#BBD6FF")); setPadding(6, 0, 6, 0) }
        val tvMin = TextView(this).apply { textSize = 32f; setTypeface(null, Typeface.BOLD) }
        val tvColon2 = TextView(this).apply { text = ":"; textSize = 30f; setTextColor(Color.parseColor("#BBD6FF")); setPadding(6, 0, 6, 0) }
        val tvSec = TextView(this).apply { textSize = 32f; setTypeface(null, Typeface.BOLD) }

        val amPmCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 0, 0, 0)
        }
        val tvAm = TextView(this).apply { text = "AM"; textSize = 12.5f; setTypeface(null, Typeface.BOLD); setPadding(4, 2, 4, 2) }
        val tvPm = TextView(this).apply { text = "PM"; textSize = 12.5f; setTypeface(null, Typeface.BOLD); setPadding(4, 2, 4, 2) }
        amPmCol.addView(tvAm)
        amPmCol.addView(tvPm)

        lateinit var clockView: CircularClockView

        fun refreshBanner() {
            val isPm = hour24 >= 12
            val h12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            tvHour.text = "%02d".format(h12)
            tvMin.text = "%02d".format(minuteVal)
            tvSec.text = "%02d".format(secondVal)

            tvHour.setTextColor(if (activeDialMode == CircularClockView.DialMode.HOUR) Color.WHITE else Color.parseColor("#BBD6FF"))
            tvMin.setTextColor(if (activeDialMode == CircularClockView.DialMode.MINUTE) Color.WHITE else Color.parseColor("#BBD6FF"))
            tvSec.setTextColor(if (activeDialMode == CircularClockView.DialMode.SECOND) Color.WHITE else Color.parseColor("#BBD6FF"))

            tvAm.setTextColor(if (!isPm) Color.WHITE else Color.parseColor("#93C5FD"))
            tvPm.setTextColor(if (isPm) Color.WHITE else Color.parseColor("#93C5FD"))
        }

        clockView = CircularClockView(this).apply {
            layoutParams = LinearLayout.LayoutParams(540, 540).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setModeAndValue(CircularClockView.DialMode.HOUR, if (hour24 % 12 == 0) 12 else hour24 % 12)
            onValueChanged = { newVal, isRelease ->
                when (activeDialMode) {
                    CircularClockView.DialMode.HOUR -> {
                        val isPm = hour24 >= 12
                        hour24 = when {
                            newVal == 12 && !isPm -> 0
                            newVal == 12 && isPm -> 12
                            isPm -> newVal + 12
                            else -> newVal
                        }
                        if (isRelease) {
                            activeDialMode = CircularClockView.DialMode.MINUTE
                            setModeAndValue(CircularClockView.DialMode.MINUTE, minuteVal)
                        }
                    }
                    CircularClockView.DialMode.MINUTE -> {
                        minuteVal = newVal
                        if (isRelease) {
                            activeDialMode = CircularClockView.DialMode.SECOND
                            setModeAndValue(CircularClockView.DialMode.SECOND, secondVal)
                        }
                    }
                    CircularClockView.DialMode.SECOND -> {
                        secondVal = newVal
                    }
                }
                refreshBanner()
            }
        }

        tvHour.setOnClickListener {
            activeDialMode = CircularClockView.DialMode.HOUR
            clockView.setModeAndValue(activeDialMode, if (hour24 % 12 == 0) 12 else hour24 % 12)
            refreshBanner()
        }
        tvMin.setOnClickListener {
            activeDialMode = CircularClockView.DialMode.MINUTE
            clockView.setModeAndValue(activeDialMode, minuteVal)
            refreshBanner()
        }
        tvSec.setOnClickListener {
            activeDialMode = CircularClockView.DialMode.SECOND
            clockView.setModeAndValue(activeDialMode, secondVal)
            refreshBanner()
        }
        tvAm.setOnClickListener {
            if (hour24 >= 12) hour24 -= 12
            refreshBanner()
        }
        tvPm.setOnClickListener {
            if (hour24 < 12) hour24 += 12
            refreshBanner()
        }

        refreshBanner()

        banner.addView(tvHour)
        banner.addView(tvColon1)
        banner.addView(tvMin)
        banner.addView(tvColon2)
        banner.addView(tvSec)
        banner.addView(amPmCol)

        val clockSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        clockSection.addView(banner)
        clockSection.addView(clockView)

        val manualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 24, 0, 24)
        }
        val etH = createDarkEditText("%02d".format(hour24)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(140, -2)
        }
        val etM = createDarkEditText("%02d".format(minuteVal)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(140, -2).apply { leftMargin = 16 }
        }
        val etS = createDarkEditText("%02d".format(secondVal)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(140, -2).apply { leftMargin = 16 }
        }
        manualRow.addView(etH); manualRow.addView(etM); manualRow.addView(etS)
        clockSection.addView(manualRow)

        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2)
            setPadding(8, 8, 8, 0)
        }
        val btnKeyboardToggle = TextView(this).apply {
            text = "⌨"
            textSize = 20f
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(12, 8, 12, 8)
            var isManual = false
            setOnClickListener {
                isManual = !isManual
                if (isManual) {
                    clockView.visibility = View.GONE
                    manualRow.visibility = View.VISIBLE
                    etH.setText("%02d".format(hour24))
                    etM.setText("%02d".format(minuteVal))
                    etS.setText("%02d".format(secondVal))
                    text = "🕒"
                } else {
                    hour24 = (etH.text.toString().toIntOrNull() ?: hour24).coerceIn(0, 23)
                    minuteVal = (etM.text.toString().toIntOrNull() ?: minuteVal).coerceIn(0, 59)
                    secondVal = (etS.text.toString().toIntOrNull() ?: secondVal).coerceIn(0, 59)
                    clockView.setModeAndValue(activeDialMode, when (activeDialMode) {
                        CircularClockView.DialMode.HOUR -> if (hour24 % 12 == 0) 12 else hour24 % 12
                        CircularClockView.DialMode.MINUTE -> minuteVal
                        CircularClockView.DialMode.SECOND -> secondVal
                    })
                    refreshBanner()
                    manualRow.visibility = View.GONE
                    clockView.visibility = View.VISIBLE
                    text = "⌨"
                }
            }
        }
        toggleRow.addView(btnKeyboardToggle)
        clockSection.addView(toggleRow)
        layout.addView(clockSection)

        val updateEnabledState = { isSpecific: Boolean ->
            clockSection.alpha = if (isSpecific) 1.0f else 0.35f
        }
        updateEnabledState(cfg.startMode == StartMode.START_AT_SPECIFIC_TIME)
        rg.setOnCheckedChangeListener { _, checkedId ->
            updateEnabledState(checkedId == 2)
        }

        showStyledDialog("Start Mode", layout, "SAVE", "CANCEL") {
            if (manualRow.visibility == View.VISIBLE) {
                hour24 = (etH.text.toString().toIntOrNull() ?: hour24).coerceIn(0, 23)
                minuteVal = (etM.text.toString().toIntOrNull() ?: minuteVal).coerceIn(0, 59)
                secondVal = (etS.text.toString().toIntOrNull() ?: secondVal).coerceIn(0, 59)
            }
            cfg.startMode = if (rg.checkedRadioButtonId == 1) StartMode.IMMEDIATE else StartMode.START_AT_SPECIFIC_TIME
            cfg.startAtHour = hour24
            cfg.startAtMinute = minuteVal
            cfg.startAtSecond = secondVal
            cfg.startAtMillis = 0
            renderSettings()
        }
    }

    // =========================================================================
    // STOP AFTER & SUB-MENUS (EXCLUSIVE SINGLE SELECTION)
    // =========================================================================

    private fun showStopAfterDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent("Select a trigger for pausing the application.")

        var selectedIdx = cfg.stopMode.ordinal
        val radioButtons = mutableListOf<RadioButton>()

        val items = listOf(
            "Run Indefinitely" to "∞",
            "Stop After Amount Of Time" to "${cfg.stopAfterTimeValue} ${cfg.stopAfterTimeUnit.label}",
            "Stop At Specific Time" to format12Hour(cfg.stopAtHour, cfg.stopAtMinute),
            "Stop After Run Count" to "${cfg.stopAfterRunCount} Run(s)"
        )

        fun selectOnly(targetIdx: Int) {
            selectedIdx = targetIdx
            radioButtons.forEachIndexed { i, rb ->
                rb.isChecked = (i == targetIdx)
            }
        }

        items.forEachIndexed { idx, (title, sub) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 12, 8, 12)
            }
            val rb = RadioButton(this).apply {
                id = idx
                text = title
                textSize = 15f
                setTextColor(Color.parseColor("#F1F3F6"))
                buttonTintList = accentTint
                isChecked = (idx == selectedIdx)
            }
            radioButtons.add(rb)

            val tvSub = TextView(this).apply {
                text = sub
                textSize = 12.5f
                setTextColor(Color.parseColor("#8A8F9E"))
                setPadding(64, 2, 0, 0)
            }

            val handleOptionTap = {
                selectOnly(idx)
                val chosenMode = StopMode.values()[idx]
                cfg.stopMode = chosenMode
                when (chosenMode) {
                    StopMode.INDEFINITE -> renderSettings()
                    StopMode.AFTER_AMOUNT_OF_TIME -> showStopAfterAmountOfTimeDialog()
                    StopMode.AT_SPECIFIC_TIME -> showStopAtSpecificTimeDialog()
                    StopMode.AFTER_RUN_COUNT -> showStopAfterRunCountDialog()
                }
            }

            rb.setOnClickListener { handleOptionTap() }
            row.setOnClickListener { handleOptionTap() }

            row.addView(rb)
            row.addView(tvSub)
            layout.addView(row)
        }

        showStyledDialog("Stop After", layout, "SAVE", "CLOSE") {
            val chosenMode = StopMode.values()[selectedIdx]
            cfg.stopMode = chosenMode
            renderSettings()
        }
    }

    private fun showStopAfterAmountOfTimeDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent("Set how long you want to click for.")

        var selectedUnit = cfg.stopAfterTimeUnit

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 10)
        }

        val etAmount = createDarkEditText(cfg.stopAfterTimeValue.toString()).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 20
            }
        }

        val unitSelector = createDarkUnitSelector(selectedUnit) { chosen ->
            selectedUnit = chosen
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        row.addView(etAmount)
        row.addView(unitSelector)
        layout.addView(row)

        showStyledDialog("Stop After Amount Of Time", layout, "SAVE", "CANCEL") {
            val amount = (etAmount.text.toString().toLongOrNull() ?: 1L).coerceAtLeast(1L)
            cfg.stopAfterTimeValue = amount
            cfg.stopAfterTimeUnit = selectedUnit
            cfg.stopAfterTimeMs = amount * selectedUnit.multiplierMs
            renderSettings()
        }
    }

    private fun showStopAtSpecificTimeDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent("Set a time to stop clicking.")

        var hour24 = cfg.stopAtHour
        var minuteVal = cfg.stopAtMinute
        var activeDialMode = CircularClockView.DialMode.HOUR

        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 18f
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }

        val tvHour = TextView(this).apply { textSize = 36f; setTypeface(null, Typeface.BOLD) }
        val tvColon = TextView(this).apply { text = ":"; textSize = 34f; setTextColor(Color.parseColor("#BBD6FF")); setPadding(6, 0, 6, 0) }
        val tvMin = TextView(this).apply { textSize = 36f; setTypeface(null, Typeface.BOLD) }

        val amPmCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 0, 0, 0)
        }
        val tvAm = TextView(this).apply { text = "AM"; textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(4, 2, 4, 2) }
        val tvPm = TextView(this).apply { text = "PM"; textSize = 13f; setTypeface(null, Typeface.BOLD); setPadding(4, 2, 4, 2) }
        amPmCol.addView(tvAm)
        amPmCol.addView(tvPm)

        lateinit var clockView: CircularClockView

        fun refreshBanner() {
            val isPm = hour24 >= 12
            val h12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            tvHour.text = "%d".format(h12)
            tvMin.text = "%02d".format(minuteVal)
            tvHour.setTextColor(if (activeDialMode == CircularClockView.DialMode.HOUR) Color.WHITE else Color.parseColor("#BBD6FF"))
            tvMin.setTextColor(if (activeDialMode == CircularClockView.DialMode.MINUTE) Color.WHITE else Color.parseColor("#BBD6FF"))
            tvAm.setTextColor(if (!isPm) Color.WHITE else Color.parseColor("#93C5FD"))
            tvPm.setTextColor(if (isPm) Color.WHITE else Color.parseColor("#93C5FD"))
        }

        clockView = CircularClockView(this).apply {
            layoutParams = LinearLayout.LayoutParams(540, 540).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setModeAndValue(CircularClockView.DialMode.HOUR, if (hour24 % 12 == 0) 12 else hour24 % 12)
            onValueChanged = { newVal, isRelease ->
                if (activeDialMode == CircularClockView.DialMode.HOUR) {
                    val isPm = hour24 >= 12
                    hour24 = when {
                        newVal == 12 && !isPm -> 0
                        newVal == 12 && isPm -> 12
                        isPm -> newVal + 12
                        else -> newVal
                    }
                    if (isRelease) {
                        activeDialMode = CircularClockView.DialMode.MINUTE
                        setModeAndValue(CircularClockView.DialMode.MINUTE, minuteVal)
                    }
                } else {
                    minuteVal = newVal
                }
                refreshBanner()
            }
        }

        tvHour.setOnClickListener {
            activeDialMode = CircularClockView.DialMode.HOUR
            clockView.setModeAndValue(activeDialMode, if (hour24 % 12 == 0) 12 else hour24 % 12)
            refreshBanner()
        }
        tvMin.setOnClickListener {
            activeDialMode = CircularClockView.DialMode.MINUTE
            clockView.setModeAndValue(activeDialMode, minuteVal)
            refreshBanner()
        }
        tvAm.setOnClickListener { if (hour24 >= 12) hour24 -= 12; refreshBanner() }
        tvPm.setOnClickListener { if (hour24 < 12) hour24 += 12; refreshBanner() }

        refreshBanner()

        banner.addView(tvHour)
        banner.addView(tvColon)
        banner.addView(tvMin)
        banner.addView(amPmCol)
        layout.addView(banner)
        layout.addView(clockView)

        val manualRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, 24, 0, 24)
        }
        val etH = createDarkEditText("%02d".format(hour24)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(160, -2)
        }
        val etM = createDarkEditText("%02d".format(minuteVal)).apply {
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(160, -2).apply { leftMargin = 20 }
        }
        manualRow.addView(etH); manualRow.addView(etM)
        layout.addView(manualRow)

        val btnKeyboardToggle = TextView(this).apply {
            text = "⌨"
            textSize = 20f
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(16, 12, 16, 4)
            var isManual = false
            setOnClickListener {
                isManual = !isManual
                if (isManual) {
                    clockView.visibility = View.GONE
                    manualRow.visibility = View.VISIBLE
                    etH.setText("%02d".format(hour24))
                    etM.setText("%02d".format(minuteVal))
                    text = "🕒"
                } else {
                    hour24 = (etH.text.toString().toIntOrNull() ?: hour24).coerceIn(0, 23)
                    minuteVal = (etM.text.toString().toIntOrNull() ?: minuteVal).coerceIn(0, 59)
                    clockView.setModeAndValue(activeDialMode, if (activeDialMode == CircularClockView.DialMode.HOUR) {
                        if (hour24 % 12 == 0) 12 else hour24 % 12
                    } else minuteVal)
                    refreshBanner()
                    manualRow.visibility = View.GONE
                    clockView.visibility = View.VISIBLE
                    text = "⌨"
                }
            }
        }
        layout.addView(btnKeyboardToggle)

        showStyledDialog("Stop At Specific Time", layout, "SAVE", "CANCEL") {
            if (manualRow.visibility == View.VISIBLE) {
                hour24 = (etH.text.toString().toIntOrNull() ?: hour24).coerceIn(0, 23)
                minuteVal = (etM.text.toString().toIntOrNull() ?: minuteVal).coerceIn(0, 59)
            }
            cfg.stopAtHour = hour24
            cfg.stopAtMinute = minuteVal
            renderSettings()
        }
    }

    private fun showStopAfterRunCountDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent("Set how many times you want to run through all set targets.")

        val etCount = createDarkEditText(cfg.stopAfterRunCount.toString()).apply {
            setPadding(12, 20, 12, 12)
        }
        layout.addView(etCount)

        showStyledDialog("Stop After Run Count", layout, "SAVE", "CANCEL") {
            cfg.stopAfterRunCount = (etCount.text.toString().toIntOrNull() ?: 50).coerceAtLeast(1)
            renderSettings()
        }
    }

    // =========================================================================
    // OTHER TIMING, RANDOMIZATION & MANAGE CONFIGURATIONS
    // =========================================================================

    private fun showDelayDialog(
        title: String,
        description: String,
        currentDelay: Long,
        currentRand: Long,
        onSave: (Long, Long) -> Unit
    ) {
        val layout = createDarkDialogContent(description)

        layout.addView(TextView(this).apply {
            text = "Delay (Millisecond(s))"
            textSize = 12f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA")); setPadding(0, 14, 0, 6)
        })
        val etDelay = createDarkEditText(currentDelay.toString())
        layout.addView(etDelay)

        layout.addView(TextView(this).apply {
            text = "Randomization Range (Millisecond(s))"
            textSize = 12f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA")); setPadding(0, 16, 0, 6)
        })
        val etRand = createDarkEditText(currentRand.toString())
        layout.addView(etRand)

        showStyledDialog(title, layout, "SAVE", "CLOSE") {
            onSave(
                etDelay.text.toString().toLongOrNull() ?: 0L,
                etRand.text.toString().toLongOrNull() ?: 0L
            )
        }
    }

    private fun showDurationDialog() {
        val cfg = ConfigStore.activeConfig
        val layout = createDarkDialogContent(
            "Set the default duration of each action.\n\nFor clicks, this is how long the click will be held down.\n\nFor swipes, this controls how long the swipe will take to complete."
        )

        var selectedUnit = cfg.durationUnit

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 18, 0, 10)
        }
        val etVal = createDarkEditText(cfg.durationValue.toString()).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 20
            }
        }
        val unitSelector = createDarkUnitSelector(selectedUnit) { chosen ->
            selectedUnit = chosen
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
        }

        row.addView(etVal)
        row.addView(unitSelector)
        layout.addView(row)

        showStyledDialog("Click/Swipe Duration", layout, "SAVE", "CANCEL") {
            val v = (etVal.text.toString().toLongOrNull() ?: 25L).coerceAtLeast(1L)
            cfg.durationValue = v
            cfg.durationUnit = selectedUnit
            cfg.durationMs = v * selectedUnit.multiplierMs
            renderSettings()
        }
    }

    private fun showRadiusDialog(current: Long, onSave: (Long) -> Unit) {
        val layout = createDarkDialogContent(
            "Set the radius in pixels around the target that the click/swipe coordinate will be randomly chosen from.\n\n(0 = No Randomization)"
        )
        val et = createDarkEditText(current.toString()).apply {
            setPadding(12, 20, 12, 12)
        }
        layout.addView(et)

        showStyledDialog("Randomization Radius", layout, "SAVE", "CANCEL") {
            onSave(et.text.toString().toLongOrNull() ?: 0L)
        }
    }

    private fun showManageConfigurationsDialog() {
        val prefs = getSharedPreferences("quickclick_presets", Context.MODE_PRIVATE)
        val layout = createDarkDialogContent("Save current targets & settings as a preset, or load an existing preset.")

        val saveRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 20)
        }
        val etName = EditText(this).apply {
            hint = "Configuration name..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 14f
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = 16 }
        }
        val btnSavePreset = TextView(this).apply {
            text = "SAVE NEW"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(24, 18, 24, 18)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = 16f
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
                    setPadding(0, 12, 0, 12)
                })
                return
            }
            for (i in 0 until arr.length()) {
                val jsonStr = arr.getString(i)
                val parsed = runCatching { ClickConfig.fromJson(jsonStr) }.getOrNull() ?: continue
                val itemCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(24, 20, 20, 20)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#12151C"))
                        cornerRadius = 18f
                        setStroke(2, Color.parseColor("#282C37"))
                    }
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 }
                }
                val tvTitle = TextView(this).apply {
                    text = "${parsed.name} (${parsed.targets.size} targets)"
                    textSize = 14f
                    setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val btnLoad = TextView(this).apply {
                    text = "LOAD"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#34D399"))
                    setPadding(16, 8, 16, 8)
                    setOnClickListener {
                        ConfigStore.activeConfig = ClickConfig.fromJson(jsonStr)
                        renderSettings()
                        Toast.makeText(this@SettingsActivity, "Loaded: ${parsed.name}", Toast.LENGTH_SHORT).show()
                    }
                }
                val btnDel = TextView(this).apply {
                    text = "✕"
                    textSize = 14f
                    setTextColor(Color.parseColor("#F87171"))
                    setPadding(16, 8, 8, 8)
                    setOnClickListener {
                        val updated = JSONArray()
                        for (j in 0 until arr.length()) if (j != i) updated.put(arr.getString(j))
                        prefs.edit().putString("presets_list", updated.toString()).apply()
                        refreshPresets()
                    }
                }
                itemCard.addView(tvTitle)
                itemCard.addView(btnLoad)
                itemCard.addView(btnDel)
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
        showStyledDialog("Manage Configurations", layout, "DONE", "CLOSE") {}
    }

    // =========================================================================
    // CUSTOM DARK DROPDOWN & DIALOG BUILDERS (ZERO WHITE BACKGROUNDS)
    // =========================================================================

    private fun createDarkUnitSelector(
        initialUnit: TimeUnitType,
        onSelected: (TimeUnitType) -> Unit
    ): TextView {
        var current = initialUnit
        return TextView(this).apply {
            text = "${current.label}   ▼"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 22, 24, 22)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = 18f
                setStroke(2, Color.parseColor("#2E3444"))
            }
            setOnClickListener {
                val unitLayout = LinearLayout(this@SettingsActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(36, 12, 36, 12)
                }
                val rg = RadioGroup(this@SettingsActivity)
                TimeUnitType.values().forEachIndexed { index, unit ->
                    rg.addView(RadioButton(this@SettingsActivity).apply {
                        id = index
                        text = unit.label
                        textSize = 15f
                        setTextColor(Color.parseColor("#F1F3F6"))
                        buttonTintList = accentTint
                        setPadding(12, 18, 12, 18)
                    })
                }
                rg.check(current.ordinal)
                unitLayout.addView(rg)

                showStyledDialog("Select Time Unit", unitLayout, "SELECT", "CANCEL") {
                    current = TimeUnitType.values()[rg.checkedRadioButtonId.coerceAtLeast(0)]
                    text = "${current.label}   ▼"
                    onSelected(current)
                }
            }
        }
    }

    private fun format12Hour(hour24: Int, minute: Int): String {
        val period = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%d:%02d %s".format(h12, minute, period)
    }

    private fun format12HourWithSeconds(hour24: Int, minute: Int, second: Int): String {
        val period = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%d:%02d:%02d %s".format(h12, minute, second, period)
    }

    private fun createDarkDialogContent(description: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 12)
            addView(TextView(context).apply {
                text = description
                textSize = 13.5f
                setTextColor(Color.parseColor("#B8C0D0"))
                setPadding(0, 0, 0, 12)
            })
        }
    }

    private fun createDarkEditText(initialValue: String): EditText {
        return EditText(this).apply {
            setText(initialValue)
            setTextColor(Color.WHITE)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#60A5FA"))
        }
    }

    private fun showStyledDialog(
        title: String,
        contentView: View,
        positiveText: String,
        negativeText: String,
        onPositive: () -> Unit
    ) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 28f
                setStroke(2, Color.parseColor("#2E3444"))
            }
            setPadding(8, 36, 8, 20)
        }

        wrapper.addView(TextView(this).apply {
            text = title
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(48, 0, 48, 8)
        })

        wrapper.addView(contentView)

        val actionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(36, 16, 36, 8)
        }

        val btnNeg = TextView(this).apply {
            text = negativeText
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(28, 18, 28, 18)
        }

        val btnPos = TextView(this).apply {
            text = positiveText
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(28, 18, 28, 18)
        }

        actionBar.addView(btnNeg)
        actionBar.addView(btnPos)
        wrapper.addView(actionBar)

        val dialog = AlertDialog.Builder(this)
            .setView(wrapper)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnNeg.setOnClickListener { dialog.dismiss() }
        btnPos.setOnClickListener {
            dialog.dismiss()
            onPositive()
        }
        dialog.show()
    }

    private fun addSectionHeader(title: String) {
        container.addView(TextView(this).apply {
            text = title
            textSize = 11f
            letterSpacing = 0.1f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(4, 24, 0, 12)
        })
    }

    private fun addSettingCard(title: String, description: String, valueBadge: String, onClick: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(36, 28, 32, 28)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 24f
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
                cornerRadius = 24f
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
            thumbTintList = accentTint
            setOnCheckedChangeListener { _, isChecked -> onToggle(isChecked) }
        }

        card.addView(textCol)
        card.addView(sw)
        container.addView(card)
    }

    // =========================================================================
    // INTERACTIVE CIRCULAR CLOCK FACE (HOURS, MINUTES & SECONDS)
    // =========================================================================

    private class CircularClockView(context: Context) : View(context) {

        enum class DialMode { HOUR, MINUTE, SECOND }

        var onValueChanged: ((Int, Boolean) -> Unit)? = null
        private var mode = DialMode.HOUR
        private var selectedValue = 12

        private val dialBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12151C")
            style = Paint.Style.FILL
        }
        private val dialStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#282C37")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2979FF")
            style = Paint.Style.FILL
        }
        private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2979FF")
            strokeWidth = 5f
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun setModeAndValue(newMode: DialMode, value: Int) {
            mode = newMode
            selectedValue = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = min(cx, cy) - 16f
            val labelRadius = radius * 0.76f

            canvas.drawCircle(cx, cy, radius, dialBgPaint)
            canvas.drawCircle(cx, cy, radius, dialStrokePaint)

            val angleDeg = if (mode == DialMode.HOUR) {
                (selectedValue % 12) * 30f
            } else {
                selectedValue * 6f
            }
            val angleRad = Math.toRadians((angleDeg - 90f).toDouble())
            val selX = cx + (labelRadius * cos(angleRad)).toFloat()
            val selY = cy + (labelRadius * sin(angleRad)).toFloat()

            canvas.drawCircle(cx, cy, 10f, accentPaint)
            canvas.drawLine(cx, cy, selX, selY, handPaint)
            canvas.drawCircle(selX, selY, 42f, accentPaint)

            if (mode != DialMode.HOUR && selectedValue % 5 != 0) {
                canvas.drawCircle(selX, selY, 7f, dotPaint)
            }

            val fm = textPaint.fontMetrics
            val textOffset = (fm.descent - fm.ascent) / 2f - fm.descent

            for (i in 0 until 12) {
                val deg = i * 30f
                val rad = Math.toRadians((deg - 90f).toDouble())
                val lx = cx + (labelRadius * cos(rad)).toFloat()
                val ly = cy + (labelRadius * sin(rad)).toFloat() + textOffset

                val labelVal = if (mode == DialMode.HOUR) {
                    if (i == 0) 12 else i
                } else {
                    i * 5
                }
                val labelText = if (mode == DialMode.HOUR) "$labelVal" else "%02d".format(labelVal)
                val isSelected = if (mode == DialMode.HOUR) {
                    (selectedValue % 12) == (labelVal % 12)
                } else {
                    selectedValue == labelVal
                }

                textPaint.color = if (isSelected) Color.WHITE else Color.parseColor("#CBD5E1")
                canvas.drawText(labelText, lx, ly, textPaint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val cx = width / 2f
            val cy = height / 2f
            var deg = Math.toDegrees(atan2((event.y - cy).toDouble(), (event.x - cx).toDouble())) + 90.0
            if (deg < 0) deg += 360.0

            val newVal = if (mode == DialMode.HOUR) {
                val step = ((deg + 15.0) / 30.0).toInt() % 12
                if (step == 0) 12 else step
            } else {
                ((deg + 3.0) / 6.0).toInt() % 60
            }

            selectedValue = newVal
            invalidate()

            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    onValueChanged?.invoke(newVal, false)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    onValueChanged?.invoke(newVal, true)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}

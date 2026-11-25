package com.app.autoclicker.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.app.autoclicker.R
import com.app.autoclicker.models.TimeUnitType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

object DialogHelper {

    val accentTint: ColorStateList = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(Color.parseColor("#2979FF"), Color.parseColor("#6B7280"))
    )

    private fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun Context.dpF(v: Float): Float = v * resources.displayMetrics.density

    fun format12Hour(hour24: Int, minute: Int): String {
        val period = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%d:%02d %s".format(h12, minute, period)
    }

    fun format12HourWithSeconds(hour24: Int, minute: Int, second: Int): String {
        val period = if (hour24 >= 12) "PM" else "AM"
        val h12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return "%d:%02d:%02d %s".format(h12, minute, second, period)
    }

    fun createDarkDialogContent(ctx: Context, description: String): LinearLayout {
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(20), ctx.dp(6), ctx.dp(20), ctx.dp(6))
            if (description.isNotEmpty()) {
                addView(TextView(ctx).apply {
                    text = description
                    textSize = 13.5f
                    setLineSpacing(3f, 1.15f)
                    setTextColor(Color.parseColor("#B8C0D0"))
                    setPadding(0, 0, 0, ctx.dp(12))
                })
            }
        }
    }

    fun createDarkEditText(ctx: Context, initialValue: String, centerText: Boolean = false): EditText {
        return EditText(ctx).apply {
            setText(initialValue)
            setTextColor(Color.WHITE)
            textSize = 16f
            if (centerText) gravity = Gravity.CENTER
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = ctx.dpF(10f)
                setStroke(ctx.dp(1), Color.parseColor("#3B4254"))
            }
        }
    }

    fun createDarkUnitSelector(
        ctx: Context,
        initialUnit: TimeUnitType,
        isOverlay: Boolean = false,
        onSelected: (TimeUnitType) -> Unit
    ): LinearLayout {
        var current = initialUnit
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(12), ctx.dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#12151C"))
                cornerRadius = ctx.dpF(10f)
                setStroke(ctx.dp(1), Color.parseColor("#3B4254"))
            }
        }

        val tvLabel = TextView(ctx).apply {
            text = current.label
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val ivArrow = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_chevron_right)
            rotation = 90f
            imageTintList = ColorStateList.valueOf(Color.parseColor("#8A8F9E"))
            layoutParams = LinearLayout.LayoutParams(ctx.dp(18), ctx.dp(18))
        }

        box.addView(tvLabel)
        box.addView(ivArrow)

        box.setOnClickListener {
            val unitLayout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(ctx.dp(18), ctx.dp(6), ctx.dp(18), ctx.dp(6))
            }
            val rg = RadioGroup(ctx)
            TimeUnitType.values().forEachIndexed { index, unit ->
                rg.addView(RadioButton(ctx).apply {
                    id = index
                    text = unit.label
                    textSize = 15f
                    setTextColor(Color.parseColor("#F1F3F6"))
                    buttonTintList = accentTint
                    setPadding(ctx.dp(8), ctx.dp(10), ctx.dp(8), ctx.dp(10))
                })
            }
            rg.check(current.ordinal)
            unitLayout.addView(rg)

            showStyledDialog(ctx, "Select Time Unit", unitLayout, "SELECT", "CANCEL", isOverlay) {
                current = TimeUnitType.values()[rg.checkedRadioButtonId.coerceAtLeast(0)]
                tvLabel.text = current.label
                onSelected(current)
            }
        }
        return box
    }

    fun showStyledDialog(
        ctx: Context,
        title: String,
        contentView: View,
        positiveText: String?,
        negativeText: String,
        isOverlay: Boolean = false,
        onPositive: (() -> Unit)? = null
    ) {
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = ctx.dpF(18f)
                setStroke(ctx.dp(1), Color.parseColor("#2E3444"))
            }
            setPadding(ctx.dp(4), ctx.dp(18), ctx.dp(4), ctx.dp(10))
        }

        wrapper.addView(TextView(ctx).apply {
            text = title
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(ctx.dp(20), 0, ctx.dp(20), ctx.dp(6))
        })

        wrapper.addView(contentView)

        val actionBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(4))
        }

        val btnNeg = TextView(ctx).apply {
            text = negativeText
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#9CA3AF"))
            setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(10))
        }
        actionBar.addView(btnNeg)

        var btnPos: TextView? = null
        if (positiveText != null) {
            btnPos = TextView(ctx).apply {
                text = positiveText
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#60A5FA"))
                setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(10))
            }
            actionBar.addView(btnPos)
        }

        wrapper.addView(actionBar)

        val dialog = AlertDialog.Builder(ctx)
            .setView(wrapper)
            .create()

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            if (isOverlay) {
                setType(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                )
            }
        }

        btnNeg.setOnClickListener { dialog.dismiss() }
        btnPos?.setOnClickListener {
            dialog.dismiss()
            onPositive?.invoke()
        }
        dialog.show()
    }

    fun showNumberDialog(
        ctx: Context,
        title: String,
        description: String,
        initialValue: Long,
        isOverlay: Boolean = false,
        onSave: (Long) -> Unit
    ) {
        val layout = createDarkDialogContent(ctx, description)
        val et = createDarkEditText(ctx, initialValue.toString()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = ctx.dp(6)
                bottomMargin = ctx.dp(6)
            }
        }
        layout.addView(et)

        showStyledDialog(ctx, title, layout, "SAVE", "CANCEL", isOverlay) {
            onSave(et.text.toString().toLongOrNull() ?: initialValue)
        }
    }

    fun showDelayDialog(
        ctx: Context,
        title: String,
        description: String,
        currentDelay: Long,
        currentRand: Long,
        isOverlay: Boolean = false,
        onSave: (Long, Long) -> Unit
    ) {
        val layout = createDarkDialogContent(ctx, description)

        layout.addView(TextView(ctx).apply {
            text = "Delay (Millisecond(s))"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, ctx.dp(6), 0, ctx.dp(6))
        })
        val etDelay = createDarkEditText(ctx, currentDelay.toString())
        layout.addView(etDelay)

        layout.addView(TextView(ctx).apply {
            text = "Randomization Range (Millisecond(s))"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(0, ctx.dp(12), 0, ctx.dp(6))
        })
        val etRand = createDarkEditText(ctx, currentRand.toString())
        layout.addView(etRand)

        showStyledDialog(ctx, title, layout, "SAVE", "CLOSE", isOverlay) {
            onSave(
                etDelay.text.toString().toLongOrNull() ?: 0L,
                etRand.text.toString().toLongOrNull() ?: 0L
            )
        }
    }

    fun showDurationDialog(
        ctx: Context,
        description: String,
        currentVal: Long,
        currentUnit: TimeUnitType,
        isOverlay: Boolean = false,
        onSave: (Long, TimeUnitType) -> Unit
    ) {
        val layout = createDarkDialogContent(ctx, description)
        var selectedUnit = currentUnit

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(8), 0, ctx.dp(6))
        }
        val etVal = createDarkEditText(ctx, currentVal.toString(), centerText = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = ctx.dp(10)
            }
        }
        val unitSelector = createDarkUnitSelector(ctx, selectedUnit, isOverlay) { chosen ->
            selectedUnit = chosen
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.3f)
        }

        row.addView(etVal)
        row.addView(unitSelector)
        layout.addView(row)

        showStyledDialog(ctx, "Duration", layout, "SAVE", "CANCEL", isOverlay) {
            val v = (etVal.text.toString().toLongOrNull() ?: 25L).coerceAtLeast(1L)
            onSave(v, selectedUnit)
        }
    }

    fun showScheduleClockDialog(
        ctx: Context,
        title: String,
        description: String,
        immediateLabel: String,
        specificLabel: String,
        isImmediateInitial: Boolean,
        initHour24: Int,
        initMinute: Int,
        initSecond: Int,
        includeSeconds: Boolean = true,
        isOverlay: Boolean = false,
        onSave: (isImmediate: Boolean, hour24: Int, minute: Int, second: Int) -> Unit
    ) {
        val layout = createDarkDialogContent(ctx, description)

        val rg = RadioGroup(ctx).apply { setPadding(0, 0, 0, ctx.dp(10)) }
        val rbNow = RadioButton(ctx).apply {
            id = 1
            text = immediateLabel
            textSize = 14.5f
            setTextColor(Color.parseColor("#F1F3F6"))
            buttonTintList = accentTint
        }
        val rbSpecific = RadioButton(ctx).apply {
            id = 2
            text = specificLabel
            textSize = 14.5f
            setTextColor(Color.parseColor("#F1F3F6"))
            buttonTintList = accentTint
        }
        rg.addView(rbNow)
        rg.addView(rbSpecific)
        rg.check(if (isImmediateInitial) 1 else 2)
        layout.addView(rg)

        var hour24 = initHour24
        var minuteVal = initMinute
        var secondVal = initSecond
        var activeDialMode = CircularClockView.DialMode.HOUR

        val banner = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = ctx.dpF(12f)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = ctx.dp(12) }
        }

        val tvHour = TextView(ctx).apply { textSize = if (includeSeconds) 30f else 34f; setTypeface(null, Typeface.BOLD) }
        val tvColon1 = TextView(ctx).apply { text = ":"; textSize = 28f; setTextColor(Color.parseColor("#BBD6FF")); setPadding(ctx.dp(4), 0, ctx.dp(4), 0) }
        val tvMin = TextView(ctx).apply { textSize = if (includeSeconds) 30f else 34f; setTypeface(null, Typeface.BOLD) }
        val tvColon2 = TextView(ctx).apply { text = ":"; textSize = 28f; setTextColor(Color.parseColor("#BBD6FF")); setPadding(ctx.dp(4), 0, ctx.dp(4), 0) }
        val tvSec = TextView(ctx).apply { textSize = 30f; setTypeface(null, Typeface.BOLD) }

        val amPmCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ctx.dp(12), 0, 0, 0)
        }
        val tvAm = TextView(ctx).apply { text = "AM"; textSize = 12f; setTypeface(null, Typeface.BOLD); setPadding(ctx.dp(4), ctx.dp(2), ctx.dp(4), ctx.dp(2)) }
        val tvPm = TextView(ctx).apply { text = "PM"; textSize = 12f; setTypeface(null, Typeface.BOLD); setPadding(ctx.dp(4), ctx.dp(2), ctx.dp(4), ctx.dp(2)) }
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

        clockView = CircularClockView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(220), ctx.dp(220)).apply {
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
                        if (isRelease && includeSeconds) {
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
        tvAm.setOnClickListener { if (hour24 >= 12) hour24 -= 12; refreshBanner() }
        tvPm.setOnClickListener { if (hour24 < 12) hour24 += 12; refreshBanner() }

        refreshBanner()

        banner.addView(tvHour)
        banner.addView(tvColon1)
        banner.addView(tvMin)
        if (includeSeconds) {
            banner.addView(tvColon2)
            banner.addView(tvSec)
        }
        banner.addView(amPmCol)

        val clockSection = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        clockSection.addView(banner)
        clockSection.addView(clockView)

        val manualRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(0, ctx.dp(12), 0, ctx.dp(12))
        }
        val etH = createDarkEditText(ctx, "%02d".format(hour24), centerText = true).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(66), -2)
        }
        val etM = createDarkEditText(ctx, "%02d".format(minuteVal), centerText = true).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(66), -2).apply { leftMargin = ctx.dp(10) }
        }
        val etS = createDarkEditText(ctx, "%02d".format(secondVal), centerText = true).apply {
            layoutParams = LinearLayout.LayoutParams(ctx.dp(66), -2).apply { leftMargin = ctx.dp(10) }
        }
        manualRow.addView(etH)
        manualRow.addView(etM)
        if (includeSeconds) manualRow.addView(etS)
        clockSection.addView(manualRow)

        val toggleRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, -2)
            setPadding(ctx.dp(4), ctx.dp(8), ctx.dp(4), 0)
        }
        val ivToggleMode = ImageView(ctx).apply {
            setImageResource(R.drawable.ic_keyboard)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#8A8F9E"))
            setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            layoutParams = LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(38))
            var isManual = false
            setOnClickListener {
                isManual = !isManual
                if (isManual) {
                    clockView.visibility = View.GONE
                    manualRow.visibility = View.VISIBLE
                    etH.setText("%02d".format(hour24))
                    etM.setText("%02d".format(minuteVal))
                    etS.setText("%02d".format(secondVal))
                    setImageResource(R.drawable.ic_clock)
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
                    setImageResource(R.drawable.ic_keyboard)
                }
            }
        }
        toggleRow.addView(ivToggleMode)
        clockSection.addView(toggleRow)
        layout.addView(clockSection)

        fun setChildrenEnabled(view: View, enabled: Boolean) {
            view.isEnabled = enabled
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    setChildrenEnabled(view.getChildAt(i), enabled)
                }
            }
        }

        val updateEnabledState = { isSpecific: Boolean ->
            clockSection.alpha = if (isSpecific) 1.0f else 0.35f
            setChildrenEnabled(clockSection, isSpecific)
        }
        updateEnabledState(!isImmediateInitial)
        rg.setOnCheckedChangeListener { _, checkedId ->
            updateEnabledState(checkedId == 2)
        }

        showStyledDialog(ctx, title, layout, "SAVE", "CANCEL", isOverlay) {
            if (manualRow.visibility == View.VISIBLE) {
                hour24 = (etH.text.toString().toIntOrNull() ?: hour24).coerceIn(0, 23)
                minuteVal = (etM.text.toString().toIntOrNull() ?: minuteVal).coerceIn(0, 59)
                secondVal = (etS.text.toString().toIntOrNull() ?: secondVal).coerceIn(0, 59)
            }
            onSave(rg.checkedRadioButtonId == 1, hour24, minuteVal, secondVal)
        }
    }

    class CircularClockView(context: Context) : View(context) {

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
            strokeWidth = 4.5f
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f * context.resources.displayMetrics.density
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
            val radius = min(cx, cy) - 10f
            val labelRadius = radius * 0.75f

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

            canvas.drawCircle(cx, cy, 8f, accentPaint)
            canvas.drawLine(cx, cy, selX, selY, handPaint)
            canvas.drawCircle(selX, selY, 17f * resources.displayMetrics.density, accentPaint)

            if (mode != DialMode.HOUR && selectedValue % 5 != 0) {
                canvas.drawCircle(selX, selY, 5f, dotPaint)
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
            if (!isEnabled) return false
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

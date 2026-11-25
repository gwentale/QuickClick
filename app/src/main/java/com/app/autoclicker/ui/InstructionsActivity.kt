package com.app.autoclicker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1115"))
        }

        // Top Bar
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
        val tvTopTitle = TextView(this).apply {
            text = "QuickClick - Instructions"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        topBar.addView(btnBack)
        topBar.addView(tvTopTitle)
        root.addView(topBar)

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 8, 40, 56)
        }

        // Header Card
        val headerCard = createCard().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = "Instructions"
                textSize = 24f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Multi Target Mode"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#60A5FA"))
                gravity = Gravity.CENTER
                setPadding(0, 4, 0, 14)
            })
            addView(TextView(context).apply {
                text = "Please take a moment to read these instructions and learn how to use this application."
                textSize = 13f
                setTextColor(Color.parseColor("#9CA3AF"))
                gravity = Gravity.CENTER
            })
        }
        content.addView(headerCard)

        // Using The Overlay Card with Visual Diagram
        val overlayCard = createCard().apply {
            addView(TextView(context).apply {
                text = "Using The Overlay"
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 8)
            })
            addView(TextView(context).apply {
                text = "The multi target mode overlay has six buttons that vary in function depending on whether you tap them or hold them."
                textSize = 13f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, 0, 0, 24)
            })
            addView(OverlayDiagramView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    820
                )
            })
        }
        content.addView(overlayCard)

        // Instructional Sections
        content.addView(
            createInfoSectionCard(
                title = "Adding Targets",
                body = "Tapping the plus sign will create a new target. Each target is numbered and will run in sequence. All clicks/swipes originate from the target's center. After the last target runs the sequence will loop back to the first."
            )
        )

        content.addView(
            createInfoSectionCard(
                title = "Configuring A Target",
                body = "Tapping a target will allow you to configure its settings. Each target can be individually configured with a variety of settings."
            )
        )

        content.addView(
            createInfoSectionCard(
                title = "Setting A Swipe Target",
                body = "Holding the plus sign will add a new swipe target to your screen. These will swipe from the center of the circle labeled \"S\" (start) towards the center of the circle labeled \"E\" (end)."
            )
        )

        content.addView(
            createInfoSectionCard(
                title = "Proper Settings",
                body = "Your phone and its various apps have limitations on how they can be clicked and at what speed.\n\nIt is important to note that clicking faster than 50 milliseconds may cause an app not to register the click or possibly lock up your phone.\n\nIf either of these issues occur please lock your phone or power off your screen to stop the application and then change the configuration."
            )
        )

        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 30, 36, 30)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 24f
                setStroke(2, Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 18 }
        }
    }

    private fun createInfoSectionCard(title: String, body: String): LinearLayout {
        return createCard().apply {
            addView(TextView(context).apply {
                text = title
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 10)
            })
            addView(TextView(context).apply {
                text = body
                textSize = 13f
                lineHeight = 42
                setTextColor(Color.parseColor("#9CA3AF"))
            })
        }
    }

    private class OverlayDiagramView(context: Context) : View(context) {

        private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12151C")
            style = Paint.Style.FILL
        }
        private val barStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B4254")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60A5FA")
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val descPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 31f
            textAlign = Paint.Align.LEFT
        }

        private data class DiagramItem(
            val symbol: String,
            val symbolColor: Int,
            val lines: List<String>
        )

        private val items = listOf(
            DiagramItem("▶", Color.WHITE, listOf("Tap: Start/stop clicking.")),
            DiagramItem("+", Color.WHITE, listOf("Tap: Add a click target.", "Hold: Add a swipe target.")),
            DiagramItem("−", Color.WHITE, listOf("Tap: Remove the last target added.", "Hold: Remove all targets.")),
            DiagramItem("⚙", Color.WHITE, listOf("Tap: Open options and settings.", "Hold: Open QuickClick home.")),
            DiagramItem(">", Color.WHITE, listOf("Tap: Collapse all overlays.", "Hold: Reposition the overlay.")),
            DiagramItem("✕", Color.parseColor("#F87171"), listOf("Tap: Close floating overlay."))
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barLeft = 12f
            val barWidth = 136f
            val barTop = 10f
            val barBottom = height - 10f
            val rowHeight = (barBottom - barTop) / items.size

            // Draw floating overlay bar illustration
            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barBottom, 24f, 24f, barFill)
            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barBottom, 24f, 24f, barStroke)

            val fmSym = symbolPaint.fontMetrics
            val symOffset = (fmSym.descent - fmSym.ascent) / 2f - fmSym.descent

            val fmDesc = descPaint.fontMetrics
            val descOffset = (fmDesc.descent - fmDesc.ascent) / 2f - fmDesc.descent

            items.forEachIndexed { idx, item ->
                val centerY = barTop + (idx + 0.5f) * rowHeight

                // Symbol inside bar
                symbolPaint.color = item.symbolColor
                canvas.drawText(item.symbol, barLeft + barWidth / 2f, centerY + symOffset, symbolPaint)

                val lineStartX = barLeft + barWidth + 16f
                if (item.lines.size == 1) {
                    val lineEndX = lineStartX + 44f
                    canvas.drawLine(lineStartX, centerY, lineEndX, centerY, linePaint)
                    canvas.drawText(item.lines[0], lineEndX + 16f, centerY + descOffset, descPaint)
                } else {
                    val midX = lineStartX + 24f
                    val branchEndX = midX + 22f
                    val topY = centerY - 22f
                    val botY = centerY + 22f

                    // Bracket connector
                    canvas.drawLine(lineStartX, centerY, midX, centerY, linePaint)
                    canvas.drawLine(midX, topY, midX, botY, linePaint)
                    canvas.drawLine(midX, topY, branchEndX, topY, linePaint)
                    canvas.drawLine(midX, botY, branchEndX, botY, linePaint)

                    canvas.drawText(item.lines[0], branchEndX + 16f, topY + descOffset, descPaint)
                    canvas.drawText(item.lines[1], branchEndX + 16f, botY + descOffset, descPaint)
                }
            }
        }
    }
}

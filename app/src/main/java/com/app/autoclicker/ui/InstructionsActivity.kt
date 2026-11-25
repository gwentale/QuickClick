package com.app.autoclicker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Int.dpF(): Float = this * resources.displayMetrics.density
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

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
            setPadding(16.dp(), 18.dp(), 16.dp(), 12.dp())
        }
        val btnBack = TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 14.dp(), 0)
            setOnClickListener { finish() }
        }
        val tvTopTitle = TextView(this).apply {
            text = "QuickClick - Instructions"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        topBar.addView(btnBack)
        topBar.addView(tvTopTitle)
        root.addView(topBar)

        // Body Container: Scrollable instructions on the left + Floating preview anchored on the right
        val bodyFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Right padding (76dp) reserves dedicated space for the right-side floating preview (48dp + 12dp margin + 16dp gap)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 4.dp(), 76.dp(), 28.dp())
        }

        // Header Card
        val headerCard = createCard().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(TextView(context).apply {
                text = "Instructions"
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Multi Target Mode"
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#60A5FA"))
                gravity = Gravity.CENTER
                setPadding(0, 2.dp(), 0, 8.dp())
            })
            addView(TextView(context).apply {
                text = "Please take a moment to read these instructions and learn how to use this application."
                textSize = 12.5f
                setTextColor(Color.parseColor("#9CA3AF"))
                gravity = Gravity.CENTER
            })
        }
        content.addView(headerCard)

        // Using The Overlay Card with Visual Diagram
        val overlayCard = createCard().apply {
            addView(TextView(context).apply {
                text = "Using The Overlay"
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 4.dp())
            })
            addView(TextView(context).apply {
                text = "The multi target mode overlay has six buttons that vary in function depending on whether you tap them or hold them."
                textSize = 12.5f
                setTextColor(Color.parseColor("#9CA3AF"))
                setPadding(0, 0, 0, 12.dp())
            })
            addView(OverlayDiagramView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    330.dp()
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
        bodyFrame.addView(scroll)

        // Right-Side Floating Overlay Preview (Non-overlapping with instruction text)
        val floatingPreviewContainer = buildRightFloatingPreview()
        bodyFrame.addView(floatingPreviewContainer)

        root.addView(bodyFrame)
        setContentView(root)
    }

    private fun buildRightFloatingPreview(): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                rightMargin = 12.dp()
            }
        }

        val badge = TextView(this).apply {
            text = "PREVIEW"
            textSize = 8.5f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#60A5FA"))
            setPadding(6.dp(), 2.dp(), 6.dp(), 2.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A2979FF"))
                cornerRadius = 8.dpF()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 6.dp()
            }
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E61A1D24"))
                setStroke(1.dp(), Color.parseColor("#383E4E"))
                cornerRadius = 14.dpF()
            }
            setPadding(4.dp(), 6.dp(), 4.dp(), 6.dp())
            layoutParams = LinearLayout.LayoutParams(
                48.dp(),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        var isPreviewPlaying = false
        var isPreviewCollapsed = false

        val btnPlay = createPreviewButton("▶", Color.WHITE)
        btnPlay.setOnClickListener {
            isPreviewPlaying = !isPreviewPlaying
            btnPlay.text = if (isPreviewPlaying) "⏸" else "▶"
            Toast.makeText(
                this,
                if (isPreviewPlaying) "Preview: Auto-clicker running" else "Preview: Auto-clicker paused",
                Toast.LENGTH_SHORT
            ).show()
        }

        val btnAdd = createPreviewButton("+", Color.WHITE).apply {
            setOnClickListener {
                Toast.makeText(this@InstructionsActivity, "Tap: Add click target | Hold: Add swipe target", Toast.LENGTH_SHORT).show()
            }
        }

        val btnRemove = createPreviewButton("−", Color.WHITE).apply {
            setOnClickListener {
                Toast.makeText(this@InstructionsActivity, "Tap: Remove last target | Hold: Clear all", Toast.LENGTH_SHORT).show()
            }
        }

        val btnSettings = createPreviewButton("⚙", Color.WHITE).apply {
            setOnClickListener {
                Toast.makeText(this@InstructionsActivity, "Tap: Open Settings | Hold: Open Home", Toast.LENGTH_SHORT).show()
            }
        }

        val btnClose = createPreviewButton("✕", Color.parseColor("#F87171")).apply {
            setOnClickListener {
                Toast.makeText(this@InstructionsActivity, "Tap: Close floating overlay", Toast.LENGTH_SHORT).show()
            }
        }

        val collapsibleButtons = listOf(btnPlay, btnAdd, btnRemove, btnSettings, btnClose)

        val btnCollapse = createPreviewButton(">", Color.WHITE).apply {
            setOnClickListener {
                isPreviewCollapsed = !isPreviewCollapsed
                val vis = if (isPreviewCollapsed) View.GONE else View.VISIBLE
                collapsibleButtons.forEach { it.visibility = vis }
                text = if (isPreviewCollapsed) "<" else ">"
            }
        }

        listOf(btnPlay, btnAdd, btnRemove, btnSettings, btnCollapse, btnClose).forEach {
            bar.addView(it)
        }

        wrapper.addView(badge)
        wrapper.addView(bar)
        return wrapper
    }

    private fun createPreviewButton(symbol: String, color: Int): TextView {
        return TextView(this).apply {
            text = symbol
            textSize = 17f
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 14.dp(), 16.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1D24"))
                cornerRadius = 14.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp() }
        }
    }

    private fun createInfoSectionCard(title: String, body: String): LinearLayout {
        return createCard().apply {
            addView(TextView(context).apply {
                text = title
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                setPadding(0, 0, 0, 5.dp())
            })
            addView(TextView(context).apply {
                text = body
                textSize = 12.5f
                setLineSpacing(4f, 1.15f)
                setTextColor(Color.parseColor("#9CA3AF"))
            })
        }
    }

    private class OverlayDiagramView(context: Context) : View(context) {

        private val density = context.resources.displayMetrics.density
        private fun Float.dp(): Float = this * density

        private val barFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#12151C")
            style = Paint.Style.FILL
        }
        private val barStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B4254")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f.dp()
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#60A5FA")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f.dp()
        }
        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 15f.dp()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val descTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 10.5f.dp()
        }

        private data class DiagramItem(
            val symbol: String,
            val symbolColor: Int,
            val lines: List<String>
        )

        private val items = listOf(
            DiagramItem("▶", Color.WHITE, listOf("Tap: Start/stop clicking.")),
            DiagramItem("+", Color.WHITE, listOf("Tap: Add a click target.", "Hold: Add a swipe target.")),
            DiagramItem("−", Color.WHITE, listOf("Tap: Remove last target.", "Hold: Remove all targets.")),
            DiagramItem("⚙", Color.WHITE, listOf("Tap: Open settings.", "Hold: Open QuickClick home.")),
            DiagramItem(">", Color.WHITE, listOf("Tap: Collapse overlays.", "Hold: Reposition overlay.")),
            DiagramItem("✕", Color.parseColor("#F87171"), listOf("Tap: Close floating overlay."))
        )

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val barLeft = 2f.dp()
            val barWidth = 42f.dp()
            val barTop = 4f.dp()
            val barBottom = height - 4f.dp()
            val rowHeight = (barBottom - barTop) / items.size

            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barBottom, 10f.dp(), 10f.dp(), barFill)
            canvas.drawRoundRect(barLeft, barTop, barLeft + barWidth, barBottom, 10f.dp(), 10f.dp(), barStroke)

            val fmSym = symbolPaint.fontMetrics
            val symOffset = (fmSym.descent - fmSym.ascent) / 2f - fmSym.descent

            items.forEachIndexed { idx, item ->
                val centerY = barTop + (idx + 0.5f) * rowHeight

                symbolPaint.color = item.symbolColor
                canvas.drawText(item.symbol, barLeft + barWidth / 2f, centerY + symOffset, symbolPaint)

                val lineStartX = barLeft + barWidth + 5f.dp()
                if (item.lines.size == 1) {
                    val lineEndX = lineStartX + 14f.dp()
                    canvas.drawLine(lineStartX, centerY, lineEndX, centerY, linePaint)
                    drawWrappedLabel(canvas, item.lines[0], lineEndX + 6f.dp(), centerY)
                } else {
                    val midX = lineStartX + 8f.dp()
                    val branchEndX = midX + 8f.dp()
                    val topY = centerY - 9f.dp()
                    val botY = centerY + 9f.dp()

                    canvas.drawLine(lineStartX, centerY, midX, centerY, linePaint)
                    canvas.drawLine(midX, topY, midX, botY, linePaint)
                    canvas.drawLine(midX, topY, branchEndX, topY, linePaint)
                    canvas.drawLine(midX, botY, branchEndX, botY, linePaint)

                    drawWrappedLabel(canvas, item.lines[0], branchEndX + 6f.dp(), topY)
                    drawWrappedLabel(canvas, item.lines[1], branchEndX + 6f.dp(), botY)
                }
            }
        }

        private fun drawWrappedLabel(canvas: Canvas, text: String, x: Float, centerY: Float) {
            val availableWidth = (width - x - 2f.dp()).toInt().coerceAtLeast(40)
            val staticLayout = StaticLayout.Builder
                .obtain(text, 0, text.length, descTextPaint, availableWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            canvas.save()
            canvas.translate(x, centerY - staticLayout.height / 2f)
            staticLayout.draw(canvas)
            canvas.restore()
        }
    }
}

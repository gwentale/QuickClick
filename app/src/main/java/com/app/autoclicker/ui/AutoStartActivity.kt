package com.app.autoclicker.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.app.autoclicker.R
import com.app.autoclicker.models.ConfigStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AutoStartActivity : AppCompatActivity() {

    private data class AppEntry(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val allApps = mutableListOf<AppEntry>()
    private val enabledOrder = mutableListOf<String>()

    private lateinit var scrollView: ScrollView
    private lateinit var listContainer: LinearLayout
    private lateinit var loadingBar: ProgressBar
    private lateinit var etSearch: EditText
    private lateinit var tvFloatingBubble: TextView
    private lateinit var alphabetBar: LinearLayout

    private val sectionHeaderViews = mutableMapOf<Char, View>()
    private val alphabetViews = mutableMapOf<Char, TextView>()
    private var currentQuery: String = ""

    private val alphabetChars: List<Char> = listOf('#') + ('A'..'Z').toList()

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
    private fun Float.dpF(): Float = this * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#0F1115")

        loadSavedEnabledPackages()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1115"))
        }

        // Top Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp(), 18.dp(), 16.dp(), 8.dp())
        }
        val btnBack = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_left)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(28.dp(), 28.dp()).apply { rightMargin = 12.dp() }
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

        // Subtitle
        root.addView(TextView(this).apply {
            text = "Automatically show the floating overlay when selected apps are opened."
            textSize = 13f
            setTextColor(Color.parseColor("#8A8F9E"))
            setPadding(16.dp(), 0, 16.dp(), 12.dp())
        })

        // Search Box
        val searchWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 2.dp(), 8.dp(), 2.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#161A22"))
                cornerRadius = 10f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = 16.dp()
                rightMargin = 16.dp()
                bottomMargin = 10.dp()
            }
        }

        etSearch = EditText(this).apply {
            hint = "Search apps..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 14.5f
            isSingleLine = true
            background = null
            setPadding(4.dp(), 10.dp(), 8.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClearSearch = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(Color.parseColor("#8A8F9E"))
            setPadding(6.dp(), 6.dp(), 6.dp(), 6.dp())
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(28.dp(), 28.dp())
            setOnClickListener { etSearch.setText("") }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim().orEmpty()
                btnClearSearch.visibility = if (currentQuery.isEmpty()) View.GONE else View.VISIBLE
                renderAppLists()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchWrapper.addView(etSearch)
        searchWrapper.addView(btnClearSearch)
        root.addView(searchWrapper)

        // Content Frame: ScrollView + Floating Letter Bubble + Right A-Z Sidebar
        val contentFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isVerticalScrollBarEnabled = false
        }

        // Right padding (36dp) keeps cards clear of the right A-Z quick scroll bar
        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 4.dp(), 36.dp(), 28.dp())
        }
        scrollView.addView(listContainer)
        contentFrame.addView(scrollView)

        loadingBar = ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        contentFrame.addView(loadingBar)

        // Floating Letter Indicator Bubble (shown during A-Z quick scroll)
        tvFloatingBubble = TextView(this).apply {
            text = "A"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E62979FF"))
                setStroke(2.dp(), Color.parseColor("#93C5FD"))
            }
            layoutParams = FrameLayout.LayoutParams(56.dp(), 56.dp(), Gravity.END or Gravity.CENTER_VERTICAL).apply {
                rightMargin = 44.dp()
            }
        }
        contentFrame.addView(tvFloatingBubble)

        // Right A-Z Quick Scroll Sidebar
        alphabetBar = buildAlphabetQuickScrollBar()
        contentFrame.addView(alphabetBar)

        root.addView(contentFrame)
        setContentView(root)

        loadInstalledApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
    }

    private fun loadSavedEnabledPackages() {
        val prefs = getSharedPreferences("quickclick_autostart", Context.MODE_PRIVATE)
        val raw = prefs.getString("enabled_packages_order", null)
        enabledOrder.clear()
        if (raw != null) {
            runCatching {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val pkg = arr.getString(i)
                    if (pkg.isNotBlank() && !enabledOrder.contains(pkg)) {
                        enabledOrder.add(pkg)
                    }
                }
            }
        } else {
            enabledOrder.addAll(ConfigStore.autoStartPackages)
        }
        ConfigStore.autoStartPackages.clear()
        ConfigStore.autoStartPackages.addAll(enabledOrder)
    }

    private fun saveEnabledPackages() {
        ConfigStore.autoStartPackages.clear()
        ConfigStore.autoStartPackages.addAll(enabledOrder)
        val arr = JSONArray().apply {
            enabledOrder.forEach { put(it) }
        }
        getSharedPreferences("quickclick_autostart", Context.MODE_PRIVATE)
            .edit()
            .putString("enabled_packages_order", arr.toString())
            .apply()
    }

    private fun loadInstalledApps() {
        loadingBar.visibility = View.VISIBLE
        activityScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val pm = packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }

                val map = LinkedHashMap<String, AppEntry>()
                for (ri in resolveInfos) {
                    val pkg = ri.activityInfo?.packageName ?: continue
                    if (pkg == packageName || map.containsKey(pkg)) continue
                    val appLabel = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                    val appIcon = runCatching { ri.loadIcon(pm) }.getOrNull() ?: continue
                    map[pkg] = AppEntry(label = appLabel, packageName = pkg, icon = appIcon)
                }
                map.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            }

            allApps.clear()
            allApps.addAll(loaded)
            loadingBar.visibility = View.GONE
            renderAppLists()
        }
    }

    private fun renderAppLists() {
        listContainer.removeAllViews()
        sectionHeaderViews.clear()

        val filtered = if (currentQuery.isEmpty()) {
            allApps
        } else {
            allApps.filter {
                it.label.contains(currentQuery, ignoreCase = true) ||
                        it.packageName.contains(currentQuery, ignoreCase = true)
            }
        }

        val appByPkg = filtered.associateBy { it.packageName }

        // 1. Top Column/Section: Enabled Apps (most recently enabled at index 0 / top)
        val enabledApps = enabledOrder.mapNotNull { appByPkg[it] }

        // 2. Bottom Column/Section: Disabled Apps (sorted alphabetically A-Z)
        val enabledSet = enabledOrder.toHashSet()
        val disabledApps = filtered
            .filter { !enabledSet.contains(it.packageName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        // Render Section 1: ENABLED APPS
        listContainer.addView(createGroupHeader("ENABLED APPS (${enabledApps.size})", "#34D399"))
        if (enabledApps.isEmpty()) {
            listContainer.addView(createEmptyNotice(
                if (currentQuery.isEmpty()) "No apps enabled yet. Toggle an app below to move it to the top."
                else "No matching enabled apps."
            ))
        } else {
            enabledApps.forEach { entry ->
                listContainer.addView(createAppCard(entry, isEnabled = true))
            }
        }

        // Render Section 2: OTHER APPS (A-Z)
        val bottomHeader = createGroupHeader("AVAILABLE APPS (${disabledApps.size})", "#8A8F9E").apply {
            setPadding(2.dp(), 16.dp(), 0, 8.dp())
        }
        listContainer.addView(bottomHeader)

        if (disabledApps.isEmpty()) {
            listContainer.addView(createEmptyNotice(
                if (currentQuery.isEmpty()) "All installed apps are currently enabled."
                else "No matching apps found."
            ))
        } else {
            disabledApps.forEach { entry ->
                val card = createAppCard(entry, isEnabled = false)
                val firstChar = normalizeInitialChar(entry.label)
                if (!sectionHeaderViews.containsKey(firstChar)) {
                    sectionHeaderViews[firstChar] = card
                }
                listContainer.addView(card)
            }
        }

        updateAlphabetHighlightAvailability()
    }

    private fun normalizeInitialChar(label: String): Char {
        val first = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first else '#'
    }

    private fun createGroupHeader(title: String, colorHex: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11.5f
            letterSpacing = 0.08f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor(colorHex))
            setPadding(2.dp(), 6.dp(), 0, 8.dp())
        }
    }

    private fun createEmptyNotice(message: String): TextView {
        return TextView(this).apply {
            text = message
            textSize = 12.5f
            setTextColor(Color.parseColor("#6B7280"))
            setPadding(14.dp(), 14.dp(), 14.dp(), 14.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#14171E"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#222632"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
        }
    }

    private fun createAppCard(entry: AppEntry, isEnabled: Boolean): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12.dp(), 11.dp(), 12.dp(), 11.dp())
            background = GradientDrawable().apply {
                setColor(if (isEnabled) Color.parseColor("#18202C") else Color.parseColor("#161A22"))
                cornerRadius = 12f.dpF()
                setStroke(
                    1.dp(),
                    if (isEnabled) Color.parseColor("#2979FF") else Color.parseColor("#252A36")
                )
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp() }
        }

        val ivIcon = ImageView(this).apply {
            setImageDrawable(entry.icon)
            layoutParams = LinearLayout.LayoutParams(36.dp(), 36.dp()).apply { rightMargin = 12.dp() }
        }

        val tvName = TextView(this).apply {
            text = entry.label
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#F1F3F6"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 8.dp()
            }
        }

        val sw = SwitchCompat(this).apply {
            isChecked = isEnabled
            thumbTintList = DialogHelper.accentTint
        }

        val toggleAction = { targetChecked: Boolean ->
            if (targetChecked) {
                // Move newly enabled app to the very top of Enabled section
                enabledOrder.remove(entry.packageName)
                enabledOrder.add(0, entry.packageName)
            } else {
                // Remove from Enabled so it returns to the alphabetical list below
                enabledOrder.remove(entry.packageName)
            }
            saveEnabledPackages()
            renderAppLists()
        }

        sw.setOnCheckedChangeListener { _, checked ->
            if (checked != isEnabled) {
                toggleAction(checked)
            }
        }

        card.setOnClickListener {
            toggleAction(!isEnabled)
        }

        card.addView(ivIcon)
        card.addView(tvName)
        card.addView(sw)
        return card
    }

    // =========================================================================
    // A-Z QUICK SCROLL SIDEBAR (RIGHT EDGE)
    // =========================================================================

    private fun buildAlphabetQuickScrollBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(3.dp(), 6.dp(), 3.dp(), 6.dp())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC161A22"))
                cornerRadius = 12f.dpF()
                setStroke(1.dp(), Color.parseColor("#282C37"))
            }
            layoutParams = FrameLayout.LayoutParams(
                24.dp(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply {
                rightMargin = 6.dp()
            }
        }

        alphabetViews.clear()
        alphabetChars.forEach { ch ->
            val tvChar = TextView(this).apply {
                text = ch.toString()
                textSize = 9.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#8A8F9E"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    topMargin = 1.dp()
                    bottomMargin = 1.dp()
                }
            }
            alphabetViews[ch] = tvChar
            bar.addView(tvChar)
        }

        bar.setOnTouchListener { v, event ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val idx = ((event.y / v.height.coerceAtLeast(1)) * alphabetChars.size)
                        .toInt()
                        .coerceIn(0, alphabetChars.lastIndex)
                    val selectedChar = alphabetChars[idx]
                    highlightActiveLetter(selectedChar)
                    tvFloatingBubble.text = selectedChar.toString()
                    tvFloatingBubble.visibility = View.VISIBLE
                    scrollToAlphabetLetter(selectedChar)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tvFloatingBubble.visibility = View.GONE
                    updateAlphabetHighlightAvailability()
                    true
                }
                else -> false
            }
        }

        return bar
    }

    private fun updateAlphabetHighlightAvailability() {
        alphabetViews.forEach { (ch, tv) ->
            val hasItems = sectionHeaderViews.containsKey(ch)
            tv.setTextColor(
                if (hasItems) Color.parseColor("#CBD5E1") else Color.parseColor("#4B5563")
            )
        }
    }

    private fun highlightActiveLetter(activeChar: Char) {
        alphabetViews.forEach { (ch, tv) ->
            when {
                ch == activeChar -> tv.setTextColor(Color.parseColor("#60A5FA"))
                sectionHeaderViews.containsKey(ch) -> tv.setTextColor(Color.parseColor("#CBD5E1"))
                else -> tv.setTextColor(Color.parseColor("#4B5563"))
            }
        }
    }

    private fun scrollToAlphabetLetter(targetChar: Char) {
        if (sectionHeaderViews.isEmpty()) return

        // Exact match first, otherwise find nearest available letter
        val exactView = sectionHeaderViews[targetChar]
        val destinationView = exactView ?: run {
            val targetIdx = alphabetChars.indexOf(targetChar)
            var nearest: View? = null
            var bestDist = Int.MAX_VALUE
            for ((ch, view) in sectionHeaderViews) {
                val dist = kotlin.math.abs(alphabetChars.indexOf(ch) - targetIdx)
                if (dist < bestDist) {
                    bestDist = dist
                    nearest = view
                }
            }
            nearest
        }

        destinationView?.let { targetView ->
            val targetY = (targetView.top - 8.dp()).coerceAtLeast(0)
            scrollView.scrollTo(0, targetY)
        }
    }
}

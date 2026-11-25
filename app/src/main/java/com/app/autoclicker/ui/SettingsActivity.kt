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
    

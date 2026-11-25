package com.app.autoclicker.models

import org.json.JSONArray
import org.json.JSONObject

enum class TimeUnitType(val label: String, val multiplierMs: Long) {
    MILLISECONDS("Millisecond(s)", 1L),
    SECONDS("Second(s)", 1_000L),
    MINUTES("Minute(s)", 60_000L)
}

enum class StartMode {
    IMMEDIATE,
    START_AT_SPECIFIC_TIME
}

enum class StopMode {
    INDEFINITE,
    AFTER_AMOUNT_OF_TIME,
    AT_SPECIFIC_TIME,
    AFTER_RUN_COUNT
}

data class TargetPoint(
    var id: Int,
    var x: Float,
    var y: Float,
    var isSwipe: Boolean = false,
    var endX: Float = 0f,
    var endY: Float = 0f,

    // Target Enable / Disable State
    var enabled: Boolean = true,

    // Per-Target Start At Specific Time (Hour, Minute, Second)
    var startAtEnabled: Boolean = false,
    var startAtHour: Int = 12,
    var startAtMinute: Int = 0,
    var startAtSecond: Int = 0,

    // Per-Target Basic Settings
    var customStartDelayMs: Long? = null,
    var customStartDelayRandomMs: Long? = null,
    var customEndDelayMs: Long? = null,
    var customEndDelayRandomMs: Long? = null,
    var customDurationValue: Long? = null,
    var customDurationUnit: TimeUnitType? = null,
    var customDurationMs: Long? = null,

    // Per-Target Advanced Settings
    var repeatCount: Int = 0,          // 0 = Do Not Repeat
    var disableAfterRuns: Int = 0,     // 0 = Do Not Disable

    // Per-Target Randomization
    var customRandomizationRadiusPx: Int? = null
)

data class ClickConfig(
    var name: String = "Default",
    var startDelayMs: Long = 0L,
    var startDelayRandomMs: Long = 0L,
    var endDelayMs: Long = 25L,
    var endDelayRandomMs: Long = 0L,
    var durationValue: Long = 25L,
    var durationMs: Long = 25L,
    var durationUnit: TimeUnitType = TimeUnitType.MILLISECONDS,

    // Global Start Schedule
    var startMode: StartMode = StartMode.IMMEDIATE,
    var startAtHour: Int = 12,
    var startAtMinute: Int = 0,
    var startAtSecond: Int = 0,
    var startAtMillis: Int = 0,

    // Stop After
    var stopMode: StopMode = StopMode.INDEFINITE,
    var stopAfterTimeValue: Long = 2L,
    var stopAfterTimeUnit: TimeUnitType = TimeUnitType.MINUTES,
    var stopAfterTimeMs: Long = 120_000L,
    var stopAtHour: Int = 12,
    var stopAtMinute: Int = 0,
    var stopAtSecond: Int = 0,
    var stopAfterRunCount: Int = 50,

    // Randomization, Stealth & Advanced
    var ghostAntiDetect: Boolean = false,
    var singleBurstMode: Boolean = false,
    var randomizationRadiusPx: Int = 0,
    var showActiveTargets: Boolean = true,
    var targetProgrammability: Boolean = true,
    var pauseOnAppChange: Boolean = false,

    val targets: MutableList<TargetPoint> = mutableListOf()
) {
    fun toJson(): String {
        val obj = JSONObject().apply {
            put("name", name)
            put("startDelayMs", startDelayMs)
            put("startDelayRandomMs", startDelayRandomMs)
            put("endDelayMs", endDelayMs)
            put("endDelayRandomMs", endDelayRandomMs)
            put("durationValue", durationValue)
            put("durationMs", durationMs)
            put("durationUnit", durationUnit.name)
            put("startMode", startMode.name)
            put("startAtHour", startAtHour)
            put("startAtMinute", startAtMinute)
            put("startAtSecond", startAtSecond)
            put("startAtMillis", startAtMillis)
            put("stopMode", stopMode.name)
            put("stopAfterTimeValue", stopAfterTimeValue)
            put("stopAfterTimeUnit", stopAfterTimeUnit.name)
            put("stopAfterTimeMs", stopAfterTimeMs)
            put("stopAtHour", stopAtHour)
            put("stopAtMinute", stopAtMinute)
            put("stopAtSecond", stopAtSecond)
            put("stopAfterRunCount", stopAfterRunCount)
            put("ghostAntiDetect", ghostAntiDetect)
            put("singleBurstMode", singleBurstMode)
            put("randomizationRadiusPx", randomizationRadiusPx)
            put("showActiveTargets", showActiveTargets)
            put("targetProgrammability", targetProgrammability)
            put("pauseOnAppChange", pauseOnAppChange)
            val arr = JSONArray()
            targets.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id)
                    put("x", t.x.toDouble())
                    put("y", t.y.toDouble())
                    put("isSwipe", t.isSwipe)
                    put("endX", t.endX.toDouble())
                    put("endY", t.endY.toDouble())
                    put("enabled", t.enabled)
                    put("startAtEnabled", t.startAtEnabled)
                    put("startAtHour", t.startAtHour)
                    put("startAtMinute", t.startAtMinute)
                    put("startAtSecond", t.startAtSecond)
                    t.customStartDelayMs?.let { put("customStartDelayMs", it) }
                    t.customStartDelayRandomMs?.let { put("customStartDelayRandomMs", it) }
                    t.customEndDelayMs?.let { put("customEndDelayMs", it) }
                    t.customEndDelayRandomMs?.let { put("customEndDelayRandomMs", it) }
                    t.customDurationValue?.let { put("customDurationValue", it) }
                    t.customDurationUnit?.let { put("customDurationUnit", it.name) }
                    t.customDurationMs?.let { put("customDurationMs", it) }
                    put("repeatCount", t.repeatCount)
                    put("disableAfterRuns", t.disableAfterRuns)
                    t.customRandomizationRadiusPx?.let { put("customRandomizationRadiusPx", it) }
                })
            }
            put("targets", arr)
        }
        return obj.toString()
    }

    companion object {
        fun fromJson(raw: String): ClickConfig {
            val obj = JSONObject(raw)
            val cfg = ClickConfig(
                name = obj.optString("name", "Preset"),
                startDelayMs = obj.optLong("startDelayMs", 0L),
                startDelayRandomMs = obj.optLong("startDelayRandomMs", 0L),
                endDelayMs = obj.optLong("endDelayMs", 25L),
                endDelayRandomMs = obj.optLong("endDelayRandomMs", 0L),
                durationValue = obj.optLong("durationValue", 25L),
                durationMs = obj.optLong("durationMs", 25L),
                durationUnit = runCatching {
                    TimeUnitType.valueOf(obj.optString("durationUnit", TimeUnitType.MILLISECONDS.name))
                }.getOrDefault(TimeUnitType.MILLISECONDS),
                startMode = runCatching {
                    StartMode.valueOf(obj.optString("startMode", StartMode.IMMEDIATE.name))
                }.getOrDefault(StartMode.IMMEDIATE),
                startAtHour = obj.optInt("startAtHour", 12),
                startAtMinute = obj.optInt("startAtMinute", 0),
                startAtSecond = obj.optInt("startAtSecond", 0),
                startAtMillis = obj.optInt("startAtMillis", 0),
                stopMode = runCatching {
                    StopMode.valueOf(obj.optString("stopMode", StopMode.INDEFINITE.name))
                }.getOrDefault(StopMode.INDEFINITE),
                stopAfterTimeValue = obj.optLong("stopAfterTimeValue", 2L),
                stopAfterTimeUnit = runCatching {
                    TimeUnitType.valueOf(obj.optString("stopAfterTimeUnit", TimeUnitType.MINUTES.name))
                }.getOrDefault(TimeUnitType.MINUTES),
                stopAfterTimeMs = obj.optLong("stopAfterTimeMs", 120_000L),
                stopAtHour = obj.optInt("stopAtHour", 12),
                stopAtMinute = obj.optInt("stopAtMinute", 0),
                stopAtSecond = obj.optInt("stopAtSecond", 0),
                stopAfterRunCount = obj.optInt("stopAfterRunCount", 50),
                ghostAntiDetect = obj.optBoolean("ghostAntiDetect", false),
                singleBurstMode = obj.optBoolean("singleBurstMode", false),
                randomizationRadiusPx = obj.optInt("randomizationRadiusPx", 0),
                showActiveTargets = obj.optBoolean("showActiveTargets", true),
                targetProgrammability = obj.optBoolean("targetProgrammability", true),
                pauseOnAppChange = obj.optBoolean("pauseOnAppChange", false)
            )
            val arr = obj.optJSONArray("targets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    cfg.targets.add(
                        TargetPoint(
                            id = item.optInt("id", i + 1),
                            x = item.optDouble("x", 400.0).toFloat(),
                            y = item.optDouble("y", 700.0).toFloat(),
                            isSwipe = item.optBoolean("isSwipe", false),
                            endX = item.optDouble("endX", 400.0).toFloat(),
                            endY = item.optDouble("endY", 450.0).toFloat(),
                            enabled = item.optBoolean("enabled", true),
                            startAtEnabled = item.optBoolean("startAtEnabled", false),
                            startAtHour = item.optInt("startAtHour", 12),
                            startAtMinute = item.optInt("startAtMinute", 0),
                            startAtSecond = item.optInt("startAtSecond", 0),
                            customStartDelayMs = if (item.has("customStartDelayMs")) item.getLong("customStartDelayMs") else null,
                            customStartDelayRandomMs = if (item.has("customStartDelayRandomMs")) item.getLong("customStartDelayRandomMs") else null,
                            customEndDelayMs = if (item.has("customEndDelayMs")) item.getLong("customEndDelayMs") else null,
                            customEndDelayRandomMs = if (item.has("customEndDelayRandomMs")) item.getLong("customEndDelayRandomMs") else null,
                            customDurationValue = if (item.has("customDurationValue")) item.getLong("customDurationValue") else null,
                            customDurationUnit = if (item.has("customDurationUnit")) {
                                runCatching { TimeUnitType.valueOf(item.getString("customDurationUnit")) }.getOrNull()
                            } else null,
                            customDurationMs = if (item.has("customDurationMs")) item.getLong("customDurationMs") else null,
                            repeatCount = item.optInt("repeatCount", 0),
                            disableAfterRuns = item.optInt("disableAfterRuns", 0),
                            customRandomizationRadiusPx = if (item.has("customRandomizationRadiusPx")) item.getInt("customRandomizationRadiusPx") else null
                        )
                    )
                }
            }
            return cfg
        }
    }
}

object ConfigStore {
    var activeConfig: ClickConfig = ClickConfig()
    val autoStartPackages: MutableSet<String> = mutableSetOf()
}

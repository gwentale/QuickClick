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
    val id: Int,
    var x: Float,
    var y: Float,
    var isSwipe: Boolean = false,
    var endX: Float = 0f,
    var endY: Float = 0f,
    var customStartDelayMs: Long? = null,
    var customEndDelayMs: Long? = null,
    var customDurationMs: Long? = null
)

data class ClickConfig(
    var name: String = "Default",
    var startDelayMs: Long = 0L,
    var startDelayRandomMs: Long = 0L,
    var endDelayMs: Long = 25L,
    var endDelayRandomMs: Long = 0L,
    var durationMs: Long = 25L,
    var durationUnit: TimeUnitType = TimeUnitType.MILLISECONDS,

    // Start Schedule
    var startMode: StartMode = StartMode.IMMEDIATE,
    var startAtHour: Int = 12,
    var startAtMinute: Int = 0,
    var startAtSecond: Int = 0,
    var startAtMillis: Int = 0,

    // Stop After
    var stopMode: StopMode = StopMode.INDEFINITE,
    var stopAfterTimeMs: Long = 120_000L,
    var stopAtHour: Int = 12,
    var stopAtMinute: Int = 0,
    var stopAfterRunCount: Int = 50,

    // Randomization, Stealth & Advanced
    var ghostAntiDetect: Boolean = false,
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
            put("durationMs", durationMs)
            put("startMode", startMode.name)
            put("startAtHour", startAtHour)
            put("startAtMinute", startAtMinute)
            put("startAtSecond", startAtSecond)
            put("startAtMillis", startAtMillis)
            put("stopMode", stopMode.name)
            put("stopAfterTimeMs", stopAfterTimeMs)
            put("stopAtHour", stopAtHour)
            put("stopAtMinute", stopAtMinute)
            put("stopAfterRunCount", stopAfterRunCount)
            put("ghostAntiDetect", ghostAntiDetect)
            put("randomizationRadiusPx", randomizationRadiusPx)
            put("showActiveTargets", showActiveTargets)
            put("targetProgrammability", targetProgrammability)
            put("pauseOnAppChange", pauseOnAppChange)
            val arr = JSONArray()
            targets.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id); put("x", t.x); put("y", t.y)
                    put("isSwipe", t.isSwipe); put("endX", t.endX); put("endY", t.endY)
                })
            }
            put("targets", arr)
        }
        return obj.toString()
    }
}

object ConfigStore {
    var activeConfig: ClickConfig = ClickConfig()
    val autoStartPackages: MutableSet<String> = mutableSetOf()
}

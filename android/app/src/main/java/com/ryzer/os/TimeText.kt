package com.ryzer.os

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Clock and date text for the UI. Formatters are made once per pattern and locale, not every frame. */
object TimeText {
    private val formatters = HashMap<String, SimpleDateFormat>()

    fun formatter(pattern: String): SimpleDateFormat {
        val locale = Locale.getDefault()
        return formatters.getOrPut("$pattern|$locale") { SimpleDateFormat(pattern, locale) }
            .also { it.timeZone = java.util.TimeZone.getDefault() }   // follows a time zone change while running
    }

    fun format(ms: Long, pattern: String): String = formatter(pattern).format(Date(ms))

    fun clock(ms: Long = System.currentTimeMillis(), h24: Boolean, amPm: Boolean = true): String =
        format(ms, if (h24) "HH:mm" else if (amPm) "h:mm a" else "h:mm")
}

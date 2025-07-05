// File: ./app/src/main/java/com/example/autoresponder/utils/DayOfWeekUtil.kt
package com.example.autoresponder.utils

import java.util.Calendar

object DayOfWeekUtil {
    // Bitmask values for each day
    const val SUNDAY = 1
    const val MONDAY = 2
    const val TUESDAY = 4
    const val WEDNESDAY = 8
    const val THURSDAY = 16
    const val FRIDAY = 32
    const val SATURDAY = 64
    const val EVERY_DAY = 127

    // Map Calendar.DAY_OF_WEEK constants to our bitmask
    fun calendarDayToBitmask(calendarDay: Int): Int {
        return when (calendarDay) {
            Calendar.SUNDAY -> SUNDAY
            Calendar.MONDAY -> MONDAY
            Calendar.TUESDAY -> TUESDAY
            Calendar.WEDNESDAY -> WEDNESDAY
            Calendar.THURSDAY -> THURSDAY
            Calendar.FRIDAY -> FRIDAY
            Calendar.SATURDAY -> SATURDAY
            else -> 0
        }
    }

    // Convert the stored bitmask into a human-readable string
    fun bitmaskToSimpleString(days: Int): String {
        if (days == EVERY_DAY) return "Every Day"
        if (days == 0) return "Never"

        val dayStrings = mutableListOf<String>()
        if (days and MONDAY != 0) dayStrings.add("M")
        if (days and TUESDAY != 0) dayStrings.add("Tu")
        if (days and WEDNESDAY != 0) dayStrings.add("W")
        if (days and THURSDAY != 0) dayStrings.add("Th")
        if (days and FRIDAY != 0) dayStrings.add("F")
        if (days and SATURDAY != 0) dayStrings.add("Sa")
        if (days and SUNDAY != 0) dayStrings.add("Su")

        return dayStrings.joinToString(", ")
    }
}
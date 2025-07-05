// File: ./app/src/main/java/com/example/autoresponder/utils/TimeUtil.kt
package com.example.autoresponder.utils

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TimeUtil {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTime(time: String): LocalTime? {
        return try {
            LocalTime.parse(time, formatter)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun isCurrentTimeInSchedule(startTimeStr: String, endTimeStr: String): Boolean {
        val start = parseTime(startTimeStr) ?: return false
        val end = parseTime(endTimeStr) ?: return false
        val now = LocalTime.now()

        // Check if the schedule spans across midnight (e.g., 22:00 to 02:00)
        return if (start.isAfter(end)) {
            now.isAfter(start) || now.isBefore(end)
        } else {
            // Schedule is on the same day (e.g., 09:00 to 17:00)
            !now.isBefore(start) && now.isBefore(end)
        }
    }

    fun doSchedulesOverlap(
        start1Str: String, end1Str: String,
        start2Str: String, end2Str: String
    ): Boolean {
        val start1 = parseTime(start1Str) ?: return false
        val end1 = parseTime(end1Str) ?: return false
        val start2 = parseTime(start2Str) ?: return false
        val end2 = parseTime(end2Str) ?: return false

        // Normalize intervals that cross midnight into two separate, non-crossing intervals
        val interval1 = if (start1.isAfter(end1)) listOf(start1 to LocalTime.MAX, LocalTime.MIN to end1) else listOf(start1 to end1)
        val interval2 = if (start2.isAfter(end2)) listOf(start2 to LocalTime.MAX, LocalTime.MIN to end2) else listOf(start2 to end2)

        // Check for overlap between any combination of the sub-intervals
        for ((s1, e1) in interval1) {
            for ((s2, e2) in interval2) {
                if (s1.isBefore(e2) && s2.isBefore(e1)) {
                    return true
                }
            }
        }
        return false
    }
}
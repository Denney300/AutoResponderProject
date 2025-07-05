// File: ./app/src/main/java/com/example/autoresponder/database/Schedule.kt
package com.example.autoresponder.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_table")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val scheduleType: String,
    val startTimestamp: Long?,
    val endTimestamp: Long?,
    val repeatingStartTime: String?,
    val repeatingEndTime: String?,
    val repeatingDays: Int?,
    val message: String,
    val simSlot: Int,
    val isActive: Boolean
)
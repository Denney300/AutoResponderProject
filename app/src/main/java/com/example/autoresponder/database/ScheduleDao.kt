// File: ./app/src/main/java/com/example/autoresponder/database/ScheduleDao.kt
package com.example.autoresponder.database

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(schedule: Schedule)

    @Update
    suspend fun update(schedule: Schedule)

    @Query("SELECT * FROM schedule_table WHERE id = :id")
    fun getScheduleById(id: Int): LiveData<Schedule>

    @Query("SELECT * FROM schedule_table ORDER BY id DESC")
    fun getAllSchedules(): LiveData<List<Schedule>>

    @Query("SELECT * FROM schedule_table WHERE isActive = 1")
    suspend fun getActiveSchedulesOnce(): List<Schedule>

    @Delete
    suspend fun delete(schedule: Schedule)

    @Transaction
    @Query("SELECT * FROM schedule_table WHERE id = :id")
    suspend fun getScheduleByIdNonLive(id: Int): Schedule?
}
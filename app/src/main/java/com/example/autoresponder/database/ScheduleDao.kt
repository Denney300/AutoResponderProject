// File: ./app/src/main/java/com/example/autoresponder/database/ScheduleDao.kt
package com.example.autoresponder.database

import androidx.lifecycle.LiveData
import androidx.room.*

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

    @Query("SELECT COUNT(*) FROM schedule_table WHERE isActive = 1")
    suspend fun getActiveScheduleCount(): Int

    @Delete
    suspend fun delete(schedule: Schedule)

    @Transaction
    @Query("SELECT * FROM schedule_table WHERE id = :id")
    suspend fun getScheduleByIdNonLive(id: Int): Schedule?
}
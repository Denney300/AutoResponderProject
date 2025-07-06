// File: ./app/src/main/java/com/example/autoresponder/repository/ScheduleRepository.kt
package com.example.autoresponder.repository

import androidx.lifecycle.LiveData
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.database.ScheduleDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleRepository @Inject constructor(private val scheduleDao: ScheduleDao) {

    val allSchedules: LiveData<List<Schedule>> = scheduleDao.getAllSchedules()

    suspend fun insert(schedule: Schedule) {
        scheduleDao.insert(schedule)
    }

    suspend fun update(schedule: Schedule) {
        scheduleDao.update(schedule)
    }

    suspend fun getActiveSchedulesOnce(): List<Schedule> {
        return scheduleDao.getActiveSchedulesOnce()
    }

    suspend fun getActiveScheduleCount(): Int {
        return scheduleDao.getActiveScheduleCount()
    }

    suspend fun delete(schedule: Schedule) {
        scheduleDao.delete(schedule)
    }

    fun getScheduleById(id: Int): LiveData<Schedule> {
        return scheduleDao.getScheduleById(id)
    }
}
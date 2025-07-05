// File: ./app/src/main/java/com/example/autoresponder/ui/schedule/AddScheduleViewModel.kt
package com.example.autoresponder.ui.schedule

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AddScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository
) : ViewModel() {

    fun getScheduleById(id: Int): LiveData<Schedule> {
        return repository.getScheduleById(id)
    }

    suspend fun isScheduleOverlapping(schedule: Schedule): Boolean = withContext(Dispatchers.IO) {
        val allSchedules = repository.allSchedules.value ?: return@withContext false
        allSchedules.any { existingSchedule ->
            if (existingSchedule.id == schedule.id) return@any false

            if (schedule.scheduleType == "DATE_RANGE" && existingSchedule.scheduleType == "DATE_RANGE") {
                // Check for nulls before comparing
                val s1 = schedule.startTimestamp
                val e1 = schedule.endTimestamp
                val s2 = existingSchedule.startTimestamp
                val e2 = existingSchedule.endTimestamp
                if (s1 != null && e1 != null && s2 != null && e2 != null) {
                    s1 < e2 && s2 < e1
                } else {
                    false
                }
            } else {
                // Note: Overlap logic for repeating schedules is not implemented in this example.
                false
            }
        }
    }

    fun saveSchedule(schedule: Schedule) = viewModelScope.launch {
        if (schedule.id == 0) {
            repository.insert(schedule)
        } else {
            repository.update(schedule)
        }
    }
}
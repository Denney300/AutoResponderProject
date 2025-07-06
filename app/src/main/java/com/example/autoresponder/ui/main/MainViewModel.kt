// File: ./app/src/main/java/com/example/autoresponder/ui/main/MainViewModel.kt
package com.example.autoresponder.ui.main

import android.app.Application
import android.content.Intent
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.repository.ScheduleRepository
import com.example.autoresponder.service.SmsObserverService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val application: Application
) : ViewModel() {

    val allSchedules: LiveData<List<Schedule>> = repository.allSchedules

    fun updateSchedule(schedule: Schedule) = viewModelScope.launch {
        repository.update(schedule)
        checkAndManageServiceState()
    }

    fun deleteSchedule(schedule: Schedule) = viewModelScope.launch {
        repository.delete(schedule)
        checkAndManageServiceState()
    }

    private suspend fun checkAndManageServiceState() {
        if (repository.getActiveScheduleCount() == 0) {
            val serviceIntent = Intent(application, SmsObserverService::class.java)
            application.stopService(serviceIntent)
        }
    }
}
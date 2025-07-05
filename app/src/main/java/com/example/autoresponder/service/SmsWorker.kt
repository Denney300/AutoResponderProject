// File: ./app/src/main/java/com/example/autoresponder/service/SmsWorker.kt
package com.example.autoresponder.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.repository.ScheduleRepository
import com.example.autoresponder.utils.DayOfWeekUtil
import com.example.autoresponder.utils.TimeUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.Calendar

@HiltWorker
class SmsWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScheduleRepository
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SENDER = "key_sender"
        const val TAG = "SmsWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            val sender = inputData.getString(KEY_SENDER) ?: return Result.failure()

            val activeSchedules = repository.getActiveSchedulesOnce()

            val scheduleToSend = activeSchedules.find { schedule ->
                isScheduleActiveNow(schedule)
            }

            if (scheduleToSend != null) {
                Log.d(TAG, "Active schedule found. Sending reply.")
                val sent = sendAutoReply(sender, scheduleToSend.message, scheduleToSend.simSlot)
                if (sent) Result.success() else Result.failure()
            } else {
                Log.d(TAG, "No active schedules for the current time.")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SmsWorker", e)
            if (shouldRetry(e)) Result.retry() else Result.failure()
        }
    }

    private fun isScheduleActiveNow(schedule: Schedule): Boolean {
        if (!schedule.isActive) return false

        return when (schedule.scheduleType) {
            "DATE_RANGE" -> {
                val now = System.currentTimeMillis()
                if (schedule.startTimestamp != null && schedule.endTimestamp != null) {
                    now >= schedule.startTimestamp && now < schedule.endTimestamp
                } else {
                    false
                }
            }
            "REPEATING" -> {
                val now = Calendar.getInstance()
                val todayBit = DayOfWeekUtil.calendarDayToBitmask(now.get(Calendar.DAY_OF_WEEK))
                val repeatsToday = (schedule.repeatingDays ?: 0) and todayBit != 0

                if (repeatsToday && schedule.repeatingStartTime != null && schedule.repeatingEndTime != null) {
                    TimeUtil.isCurrentTimeInSchedule(schedule.repeatingStartTime, schedule.repeatingEndTime)
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun shouldRetry(exception: Exception): Boolean {
        return exception !is SecurityException && exception !is IllegalStateException
    }

    private fun sendAutoReply(recipient: String, message: String, simSlot: Int): Boolean {
        try {
            if (ActivityCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "READ_PHONE_STATE permission not granted.")
                return false
            }

            val subManager = appContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subInfoList = subManager.activeSubscriptionInfoList
            var subscriptionId = SubscriptionManager.getDefaultSmsSubscriptionId()

            subInfoList?.find { it.simSlotIndex == simSlot }?.let {
                subscriptionId = it.subscriptionId
            }

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }

            smsManager.sendTextMessage(recipient, null, message, null, null)
            Log.d(TAG, "Auto-reply sent to $recipient via SIM slot $simSlot (Sub ID: $subscriptionId)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-reply", e)
            return false
        }
    }
}
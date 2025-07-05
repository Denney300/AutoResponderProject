// File: ./app/src/main/java/com/example/autoresponder/service/SmsObserverService.kt
package com.example.autoresponder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.autoresponder.R
import com.example.autoresponder.database.Schedule
import com.example.autoresponder.repository.ScheduleRepository
import com.example.autoresponder.ui.main.MainActivity
import com.example.autoresponder.utils.DayOfWeekUtil
import com.example.autoresponder.utils.TimeUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class SmsObserverService : Service() {

    @Inject
    lateinit var workManager: WorkManager
    @Inject
    lateinit var repository: ScheduleRepository

    private val handler = Handler(Looper.getMainLooper())
    private var lastProcessedId: Long = -1L

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AutoResponderServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "SmsPollingService"
        private const val POLLING_INTERVAL_MS = 60000L // 1 minute
    }

    private val pollingRunnable = object : Runnable {
        override fun run() {
            Log.d(TAG, "Polling for new messages...")
            CoroutineScope(Dispatchers.IO).launch {
                val activeSchedules = repository.getActiveSchedulesOnce()
                val isAnyScheduleActive = activeSchedules.any { isScheduleActiveNow(it) }

                if (isAnyScheduleActive) {
                    checkForNewSms()
                    // CORRECTED: Referencing the object by its variable name.
                    handler.postDelayed(this@pollingRunnable, POLLING_INTERVAL_MS)
                } else {
                    Log.d(TAG, "No active schedules found. Stopping service.")
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting...")
        startForeground(NOTIFICATION_ID, createNotification())
        initializeLastProcessedId()
        handler.post(pollingRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(pollingRunnable)
        Log.d(TAG, "Service destroyed.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun initializeLastProcessedId() {
        if (lastProcessedId != -1L) return
        try {
            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                "date DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    lastProcessedId = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                    Log.d(TAG, "Observer initialized. Last seen SMS ID: $lastProcessedId")
                } else {
                    lastProcessedId = 0L
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing last processed ID.", e)
            lastProcessedId = 0L
        }
    }

    private fun checkForNewSms() {
        if (lastProcessedId == -1L) return

        try {
            val selection = "${Telephony.Sms._ID} > ?"
            val selectionArgs = arrayOf(lastProcessedId.toString())
            val sortOrder = "${Telephony.Sms._ID} ASC"

            val cursor = contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS),
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    do {
                        val id = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms._ID))
                        val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))

                        Log.d(TAG, "New SMS detected via Polling (ID: $id) from: $address. Enqueuing worker.")
                        enqueueWorker(address, id)
                        lastProcessedId = id

                    } while (it.moveToNext())
                } else {
                    Log.d(TAG, "No new messages found since last poll.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error polling SMS content provider.", e)
        }
    }

    private fun enqueueWorker(sender: String, messageId: Long) {
        val inputData = Data.Builder()
            .putString(SmsWorker.KEY_SENDER, sender)
            .build()
        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(inputData)
            .build()
        workManager.enqueueUniqueWork(
            "sms-reply-to-$sender-$messageId",
            ExistingWorkPolicy.KEEP,
            workRequest
        )
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

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Auto-Responder Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(notificationChannel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auto-Responder Active")
            .setContentText("Monitoring for incoming SMS messages.")
            .setSmallIcon(R.drawable.ic_active_circle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
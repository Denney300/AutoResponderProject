// File: ./app/src/main/java/com/example/autoresponder/service/SmsObserverService.kt
package com.example.autoresponder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class SmsObserverService : Service() {

    @Inject
    lateinit var workManager: WorkManager
    @Inject
    lateinit var repository: ScheduleRepository

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var lastProcessedId: Long = -1L

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "AutoResponderServiceChannel"
        const val NOTIFICATION_ID = 1
        const val TAG = "SmsPollingService"
        private const val POLLING_INTERVAL_MS = 60000L // 1 minute
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service starting...")
        startForeground(NOTIFICATION_ID, createNotification())

        serviceScope.launch {
            initializeLastProcessedId()
            pollingLoop()
        }

        return START_STICKY
    }

    private suspend fun pollingLoop() {
        while (true) {
            Log.d(TAG, "Polling...")
            try {
                if (repository.getActiveScheduleCount() > 0) {
                    checkForNewSms()
                } else {
                    Log.d(TAG, "No active schedules. Stopping service.")
                    stopSelf()
                    break // Exit the loop
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in polling loop", e)
            }
            delay(POLLING_INTERVAL_MS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
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
                } else {
                    lastProcessedId = 0L
                }
            } ?: run { lastProcessedId = 0L }
            Log.d(TAG, "Polling initialized. Last seen SMS ID: $lastProcessedId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing last processed ID.", e)
            lastProcessedId = 0L
        }
    }

    private fun checkForNewSms() {
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
                        Log.d(TAG, "New SMS detected (ID: $id). Enqueuing worker for $address.")
                        enqueueWorker(address, id)
                        lastProcessedId = id
                    } while (it.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error polling SMS content provider.", e)
        }
    }

    private fun enqueueWorker(sender: String, messageId: Long) {
        val workRequest = OneTimeWorkRequestBuilder<SmsWorker>()
            .setInputData(Data.Builder().putString(SmsWorker.KEY_SENDER, sender).build())
            .build()
        workManager.enqueueUniqueWork("sms-reply-$messageId", ExistingWorkPolicy.KEEP, workRequest)
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Auto-Responder Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Auto-Responder Active")
            .setContentText("Monitoring for new messages...")
            .setSmallIcon(R.drawable.ic_active_circle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
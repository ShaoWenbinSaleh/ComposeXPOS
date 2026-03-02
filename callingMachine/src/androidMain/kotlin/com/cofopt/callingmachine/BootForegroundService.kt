package com.cofopt.callingmachine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return START_NOT_STICKY
        }

        CoroutineScope(Dispatchers.Default).launch {
            try {
                delay(3000)

                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                    ?: Intent(this@BootForegroundService, MainActivity::class.java)

                launchIntent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                launchIntent.putExtra("FROM_BOOT", true)

                startActivity(launchIntent)
                Log.d(TAG, "Requested start of CallingMachine from BootForegroundService")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start CallingMachine from BootForegroundService", e)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        createNotificationChannelIfNeeded()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Starting...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Boot",
            NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "BootFgService"
        private const val CHANNEL_ID = "boot"
        private const val NOTIFICATION_ID = 1001
    }
}

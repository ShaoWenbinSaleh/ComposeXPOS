package com.cofopt.callingmachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot completed received: ${intent.action}")

        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                try {
                    val serviceIntent = Intent(context, BootForegroundService::class.java)
                    serviceIntent.putExtra("FROM_BOOT", true)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                    Log.d("BootReceiver", "Requested start of BootForegroundService on boot: $action")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start BootForegroundService on boot: $action", e)
                }
            }
        }
    }
}

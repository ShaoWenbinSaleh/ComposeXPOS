package com.cofopt.cashregister

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot completed received: ${intent.action}")
        
        when (intent.action) {
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                try {
                    val launchIntent = Intent(context, MainActivity::class.java)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    launchIntent.putExtra("FROM_BOOT", true)
                    context.startActivity(launchIntent)
                    Log.d("BootReceiver", "Successfully started CashRegister on boot")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start CashRegister on boot", e)
                }
            }
        }
    }
}

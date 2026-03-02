package com.cofopt.orderingmachine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "Boot received: ${intent.action}")
        
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("BootReceiver", "Starting OrderingMachine app on boot")
                
                // Start the main activity
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                
                // Add a small delay to ensure system is ready
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // For Android 12+, use proper delay
                    launchIntent.putExtra("boot_start", true)
                }
                
                try {
                    context.startActivity(launchIntent)
                    Log.d("BootReceiver", "OrderingMachine app started successfully")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to start OrderingMachine app: ${e.message}")
                }
            }
        }
    }
}

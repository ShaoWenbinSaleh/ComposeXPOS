package com.cofopt.orderingmachine.network

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File

object SyncedMenuImageStore {
    private const val DIR_NAME = "menu_sync"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) {
            d.mkdirs()
        }
        return d
    }

    fun saveBase64(context: Context, menuId: String, imageBase64: String?): String? {
        val raw = imageBase64?.trim().orEmpty()
        if (raw.isBlank()) {
            return existingPath(context, menuId)
        }

        val payload = raw.substringAfter("base64,", raw)
        return try {
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            val safeId = menuId.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
            val file = File(dir(context), "$safeId.jpg")
            file.writeBytes(bytes)
            "menu_sync/${file.name}"
        } catch (e: Exception) {
            Log.w("SyncedMenuImageStore", "Failed to decode image for id=$menuId", e)
            existingPath(context, menuId)
        }
    }

    fun existingPath(context: Context, menuId: String): String? {
        val safeId = menuId.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val file = File(dir(context), "$safeId.jpg")
        return if (file.exists()) "menu_sync/${file.name}" else null
    }

    fun resolveFile(context: Context, relativePath: String): File {
        return File(context.filesDir, relativePath)
    }
}

package com.cofopt.orderingmachine.printer

import android.content.Context
import android.graphics.Bitmap
import com.cofopt.shared.mock.MockFeatureNotice

/**
 * Open-source mock Sunmi helper.
 *
 * Real implementation should bind vendor service and send validated commands.
 */
object SunmiPrinterHelper {
    private var appContext: Context? = null
    private var connected: Boolean = false

    fun bindService(context: Context): Boolean {
        appContext = context.applicationContext
        connected = true
        return true
    }

    fun unbindService(context: Context) {
        connected = false
        appContext = null
    }

    suspend fun printerInit(): Result<Unit> = Result.success(Unit)

    suspend fun printBitmapAndText(
        logoBitmap: Bitmap?,
        text: String,
        feedLines: Int = 5,
        cut: Boolean = true
    ): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    suspend fun printText(text: String): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    suspend fun printOriginalText(text: String): Result<Unit> = printText(text)

    suspend fun lineWrap(lines: Int): Result<Unit> = Result.success(Unit)

    suspend fun setAlignment(alignment: Int): Result<Unit> = Result.success(Unit)

    suspend fun setFontSize(size: Float): Result<Unit> = Result.success(Unit)

    suspend fun enterPrinterBuffer(clean: Boolean): Result<Unit> = Result.success(Unit)

    suspend fun exitPrinterBuffer(commit: Boolean): Result<Unit> = Result.success(Unit)

    suspend fun cutPaper(): Result<Unit> = Result.success(Unit)

    suspend fun sendRawData(data: ByteArray): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    suspend fun printWithRawCommands(text: String): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    suspend fun printLogoAndText(
        logoEscPosBytes: ByteArray,
        text: String,
        feedLines: Int = 5,
        cut: Boolean = true
    ): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    suspend fun printBitmapAndStyledText(
        logoBitmap: Bitmap?,
        headerBitmap: Bitmap? = null,
        lines: List<String>,
        styles: List<Any>,
        feedLines: Int = 3,
        cut: Boolean = false
    ): Result<Unit> {
        notifyMock()
        return Result.success(Unit)
    }

    fun isServiceConnected(): Boolean = connected

    fun getPrinterInfo(): String? {
        return if (connected) "MOCK_SUNMI_PRINTER (open-source mode)" else null
    }

    private fun notifyMock() {
        appContext?.let { MockFeatureNotice.showPrint(it, "OrderingMachine Sunmi") }
    }
}

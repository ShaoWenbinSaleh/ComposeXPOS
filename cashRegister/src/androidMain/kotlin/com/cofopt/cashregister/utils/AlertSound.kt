package com.cofopt.cashregister.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import com.cofopt.cashregister.CashRegisterDebugConfig
import java.util.concurrent.atomic.AtomicLong

enum class AlertSoundOption(
    val id: String,
    val toneType: Int?
) {
    OFF("OFF", null),
    BEEP("BEEP", ToneGenerator.TONE_PROP_BEEP),
    BEEP2("BEEP2", ToneGenerator.TONE_PROP_BEEP2),
    ACK("ACK", ToneGenerator.TONE_PROP_ACK),
    NACK("NACK", ToneGenerator.TONE_PROP_NACK);

    companion object {
        fun fromId(id: String?): AlertSoundOption {
            if (id.isNullOrBlank()) return BEEP
            return entries.firstOrNull { it.id == id } ?: BEEP
        }
    }
}

object AlertSoundPlayer {
    private val lastPlayedAtMillis = AtomicLong(0L)
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)

    fun play(context: Context) {
        val tone = AlertSoundOption.fromId(CashRegisterDebugConfig.alertSoundId(context)).toneType ?: return

        val now = System.currentTimeMillis()
        val last = lastPlayedAtMillis.get()
        if (now - last < 900) return
        lastPlayedAtMillis.set(now)

        try {
            toneGenerator.startTone(tone, 180)
        } catch (_: Throwable) {
        }
    }
}

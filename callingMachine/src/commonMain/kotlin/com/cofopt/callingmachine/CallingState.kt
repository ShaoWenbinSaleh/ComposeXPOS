package com.cofopt.callingmachine

import kotlin.time.TimeSource

object CallingState {
    interface Listener {
        fun onSnapshot(preparing: List<Int>, ready: List<Int>, preparingLabel: String, readyLabel: String)
        fun onConnectionCountChanged(count: Int) {}
        fun onAlertNumber(number: Int) {}
    }

    private var preparing: List<Int> = emptyList()
    private var ready: List<Int> = emptyList()

    // number -> expireAtMillis, used by UI to animate newly moved preparing numbers.
    private var preparingNumberExpiry: Map<Int, Long> = emptyMap()

    private var connectionCount: Int = 0

    var lastCloseInfo: String? = null
        private set

    private var displayLanguage: CallingLanguage = CallingLanguage.ZH
    private var voiceLanguage: CallingLanguage = CallingLanguage.ZH

    private val listeners = LinkedHashSet<Listener>()

    private val appStart = TimeSource.Monotonic.markNow()

    private fun nowMillis(): Long = appStart.elapsedNow().inWholeMilliseconds

    private fun pruneExpiredPreparingNumbers(now: Long = nowMillis()) {
        if (preparingNumberExpiry.isEmpty()) return
        preparingNumberExpiry = preparingNumberExpiry.filterValues { it > now }
    }

    private fun notifyLabelsChanged() {
        pruneExpiredPreparingNumbers()
        val preparingLabel = displayLanguage.preparingLabel
        val readyLabel = displayLanguage.readyLabel
        for (listener in listeners) {
            try {
                listener.onSnapshot(preparing, ready, preparingLabel, readyLabel)
            } catch (_: Throwable) {
            }
        }
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
        pruneExpiredPreparingNumbers()
        try {
            listener.onSnapshot(preparing, ready, displayLanguage.preparingLabel, displayLanguage.readyLabel)
        } catch (_: Throwable) {
        }
        try {
            listener.onConnectionCountChanged(connectionCount)
        } catch (_: Throwable) {
        }
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun updateSnapshot(preparing: List<Int>, ready: List<Int>): Boolean {
        val previousPreparing = this.preparing
        val previousReady = this.ready
        if (previousPreparing == preparing && previousReady == ready) {
            return false
        }

        val now = nowMillis()
        pruneExpiredPreparingNumbers(now)

        this.preparing = preparing
        this.ready = ready

        val newlyMovedToPreparing = preparing.filter { it !in previousPreparing && it in previousReady }
        if (newlyMovedToPreparing.isNotEmpty()) {
            val next = preparingNumberExpiry.toMutableMap()
            for (number in newlyMovedToPreparing) {
                next[number] = now + 5_000L
            }
            preparingNumberExpiry = next
        }

        for (listener in listeners) {
            try {
                listener.onSnapshot(preparing, ready, displayLanguage.preparingLabel, displayLanguage.readyLabel)
            } catch (_: Throwable) {
            }
        }
        return true
    }

    fun updateLanguages(displayLanguageWire: String?, voiceLanguageWire: String?) {
        val newDisplay = CallingLanguage.fromWireValue(displayLanguageWire) ?: displayLanguage
        val newVoice = CallingLanguage.fromWireValue(voiceLanguageWire) ?: voiceLanguage
        val changed = newDisplay != displayLanguage || newVoice != voiceLanguage
        if (!changed) return

        displayLanguage = newDisplay
        voiceLanguage = newVoice
        notifyLabelsChanged()
    }

    fun voiceLanguage(): CallingLanguage = voiceLanguage

    fun alertNumber(number: Int) {
        for (listener in listeners) {
            try {
                listener.onAlertNumber(number)
            } catch (_: Throwable) {
            }
        }
    }

    fun updateConnectionCount(count: Int) {
        connectionCount = count
        for (listener in listeners) {
            try {
                listener.onConnectionCountChanged(count)
            } catch (_: Throwable) {
            }
        }
    }

    fun updateLastCloseInfo(info: String?) {
        lastCloseInfo = info
        for (listener in listeners) {
            try {
                listener.onConnectionCountChanged(connectionCount)
            } catch (_: Throwable) {
            }
        }
    }

    fun isNewPreparingNumber(number: Int): Boolean {
        val now = nowMillis()
        pruneExpiredPreparingNumbers(now)
        return (preparingNumberExpiry[number] ?: 0L) > now
    }
}

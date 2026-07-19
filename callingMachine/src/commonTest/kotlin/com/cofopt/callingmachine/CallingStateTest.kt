package com.cofopt.callingmachine

import kotlin.test.Test
import kotlin.test.assertEquals

class CallingStateTest {
    @Test
    fun currentSnapshotContainsStateNeededByLateViewer() {
        CallingState.updateLanguages("nl", "ja")
        CallingState.updateSnapshot(preparing = listOf(12, 14), ready = listOf(9))

        val snapshot = CallingState.currentSnapshot()

        assertEquals(listOf(12, 14), snapshot.preparing)
        assertEquals(listOf(9), snapshot.ready)
        assertEquals(CallingLanguage.NL, snapshot.displayLanguage)
        assertEquals(CallingLanguage.JA, snapshot.voiceLanguage)
    }

    @Test
    fun listenerAddedAfterUpdateImmediatelyReceivesCompleteState() {
        CallingState.updateLanguages("en", "tr")
        CallingState.updateSnapshot(preparing = listOf(21), ready = listOf(18, 19))
        var receivedPreparing = emptyList<Int>()
        var receivedReady = emptyList<Int>()
        var receivedPreparingLabel = ""
        var receivedReadyLabel = ""
        val listener = object : CallingState.Listener {
            override fun onSnapshot(
                preparing: List<Int>,
                ready: List<Int>,
                preparingLabel: String,
                readyLabel: String,
            ) {
                receivedPreparing = preparing
                receivedReady = ready
                receivedPreparingLabel = preparingLabel
                receivedReadyLabel = readyLabel
            }
        }

        CallingState.addListener(listener)
        CallingState.removeListener(listener)

        assertEquals(listOf(21), receivedPreparing)
        assertEquals(listOf(18, 19), receivedReady)
        assertEquals("Preparing", receivedPreparingLabel)
        assertEquals("Ready", receivedReadyLabel)
    }
}

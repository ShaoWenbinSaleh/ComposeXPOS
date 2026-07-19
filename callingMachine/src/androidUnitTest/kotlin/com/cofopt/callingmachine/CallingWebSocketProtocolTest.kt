package com.cofopt.callingmachine

import java.net.URLEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallingWebSocketProtocolTest {
    @Test
    fun encodedDeploymentKeyAuthenticates() {
        val key = "strong+key/with&reserved=?chars"
        val encodedKey = URLEncoder.encode(key, Charsets.UTF_8.name())

        val role = resolveCallingConnectionRole(
            resourceDescriptor = "/?mode=source&key=$encodedKey",
            sharedKey = key,
            nowMillis = 1_000_000L,
        )

        assertEquals(ConnectionRole.SOURCE, role)
    }

    @Test
    fun signedSourceRequiresFreshMatchingSignature() {
        val key = "test-key"
        val now = 2_000_000L
        val signature = callingHandshakeDigest(now, key)

        assertEquals(
            ConnectionRole.SOURCE,
            resolveCallingConnectionRole(
                resourceDescriptor = "/?mode=source&key=$key&ts=$now&sig=$signature",
                sharedKey = key,
                nowMillis = now + 30_000L,
            )
        )
        assertNull(
            resolveCallingConnectionRole(
                resourceDescriptor = "/?mode=source&key=$key&ts=$now&sig=$signature",
                sharedKey = key,
                nowMillis = now + 60_001L,
            )
        )
        assertNull(
            resolveCallingConnectionRole(
                resourceDescriptor = "/?mode=source&key=$key&ts=$now&sig=bad",
                sharedKey = key,
                nowMillis = now,
            )
        )
    }

    @Test
    fun viewerDoesNotGainSourcePrivilegesFromDuplicateMode() {
        assertEquals(
            ConnectionRole.VIEWER,
            resolveCallingConnectionRole(
                resourceDescriptor = "/?mode=source&mode=viewer&key=anything",
                sharedKey = "anything",
                nowMillis = 0L,
            )
        )
        assertNull(
            resolveCallingConnectionRole(
                resourceDescriptor = "/?mode=viewer&mode=source&key=wrong",
                sharedKey = "expected",
                nowMillis = 0L,
            )
        )
    }

    @Test
    fun sourceIdUsesValidatedStableValueAndSafeLegacyFallback() {
        assertEquals(
            "cash-ios-01",
            resolveCallingSourceId(
                resourceDescriptor = "/?mode=source&sourceId=cash-ios-01",
                fallbackHost = "192.168.1.20",
            )
        )
        assertEquals(
            "legacy:192.168.1.20",
            resolveCallingSourceId(
                resourceDescriptor = "/?mode=source&sourceId=invalid%2Fid",
                fallbackHost = "192.168.1.20",
            )
        )
    }

    @Test
    fun independentSourceSnapshotsAreUnionedAndReadyWinsConflicts() {
        val aggregate = aggregateCallingSourceSnapshots(
            listOf(
                CallingSourceSnapshot(
                    preparing = listOf(11, 12),
                    ready = listOf(13),
                    displayLanguage = CallingLanguage.EN,
                    voiceLanguage = CallingLanguage.EN,
                    sequence = 1,
                ),
                CallingSourceSnapshot(
                    preparing = listOf(21, 13),
                    ready = listOf(12),
                    displayLanguage = CallingLanguage.ZH,
                    voiceLanguage = CallingLanguage.ZH,
                    sequence = 2,
                ),
            )
        )

        assertEquals(listOf(11, 21), aggregate.preparing)
        assertEquals(listOf(13, 12), aggregate.ready)
        assertEquals(CallingLanguage.ZH, aggregate.displayLanguage)
    }

    @Test
    fun emptySnapshotOnlyClearsItsOwnSourceContribution() {
        val aggregate = aggregateCallingSourceSnapshots(
            listOf(
                CallingSourceSnapshot(
                    preparing = emptyList(),
                    ready = emptyList(),
                    displayLanguage = CallingLanguage.EN,
                    voiceLanguage = CallingLanguage.EN,
                    sequence = 2,
                ),
                CallingSourceSnapshot(
                    preparing = listOf(31),
                    ready = listOf(32),
                    displayLanguage = CallingLanguage.NL,
                    voiceLanguage = CallingLanguage.NL,
                    sequence = 1,
                ),
            )
        )

        assertEquals(listOf(31), aggregate.preparing)
        assertEquals(listOf(32), aggregate.ready)
    }
}

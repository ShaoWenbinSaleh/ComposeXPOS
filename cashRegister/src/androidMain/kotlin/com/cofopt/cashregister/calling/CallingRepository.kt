package com.cofopt.cashregister.calling

import android.content.Context
import com.cofopt.cashregister.CashRegisterDebugConfig
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

object CallingRepository {
    private val _preparing = MutableStateFlow<List<Int>>(emptyList())
    val preparing: StateFlow<List<Int>> = _preparing

    private val _ready = MutableStateFlow<List<Int>>(emptyList())
    val ready: StateFlow<List<Int>> = _ready

    private val _reserved = MutableStateFlow<List<Int>>(emptyList())
    val reserved: StateFlow<List<Int>> = _reserved

    private val _nextSequential = MutableStateFlow(1)
    private var lastAssignTime = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    enum class ManualReadyAddResult {
        Added,
        Duplicate,
        OutOfRange
    }

    data class AssignResult(
        val number: Int,
        val overtakenNumber: Int? = null
    )

    @Serializable
    private data class CallingSnapshot(
        val preparing: List<Int> = emptyList(),
        val ready: List<Int> = emptyList(),
        val reserved: List<Int> = emptyList(),
        val nextSequential: Int = 1,
        val dayKey: String = "",
        val lastAssignTime: Long = 0L
    )

    private var initialized = false
    private lateinit var appContext: Context

    @Volatile
    private var currentDayKey: String = ""

    private val Context.callingDataStore by preferencesDataStore(name = "calling_store")
    private val KEY_SNAPSHOT = stringPreferencesKey("calling_snapshot_json")

    private fun todayDayKey(): String {
        return try {
            LocalDate.now(ZoneId.systemDefault()).toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun resetForNewDayIfNeeded(snapshot: CallingSnapshot): CallingSnapshot {
        val today = todayDayKey()
        if (today.isBlank()) return snapshot

        if (snapshot.dayKey.isNotBlank() && snapshot.dayKey == today) {
            currentDayKey = today
            return snapshot
        }

        // New day: clear expired numbers and restart allocation from the beginning of the range.
        if (snapshot.dayKey.isBlank() || snapshot.dayKey != today) {
            currentDayKey = today
            val range = callingNumberRange()
            return CallingSnapshot(
                preparing = emptyList(),
                ready = emptyList(),
                reserved = emptyList(),
                nextSequential = range.first,
                dayKey = today,
                lastAssignTime = 0L
            )
        }

        currentDayKey = today
        return snapshot
    }

    private fun ensureDayUpToDate() {
        val today = todayDayKey()
        if (today.isBlank()) return
        if (today == currentDayKey) {
            return
        }

        currentDayKey = today
        val range = callingNumberRange()
        _preparing.value = emptyList()
        _ready.value = emptyList()
        _reserved.value = emptyList()
        _nextSequential.value = range.first
        lastAssignTime = 0L
        persistAsyncSnapshot()
    }

    suspend fun ensureLoaded(context: Context) {
        if (!initialized) {
            appContext = context.applicationContext
            initialized = true
        }

        val inMemoryPreparing = _preparing.value
        val inMemoryReady = _ready.value
        val inMemoryReserved = _reserved.value
        val inMemoryNext = _nextSequential.value

        val prefs = appContext.callingDataStore.data.first()
        val raw = prefs[KEY_SNAPSHOT]
        val loadedSnapshot = raw?.let {
            try {
                json.decodeFromString(CallingSnapshot.serializer(), it)
            } catch (_: Exception) {
                CallingSnapshot()
            }
        } ?: CallingSnapshot()

        val snapshot = resetForNewDayIfNeeded(loadedSnapshot)

        // If we're on a new day, snapshot will be cleared and we should not merge yesterday's in-memory state.
        val shouldMergeInMemory = snapshot.dayKey.isNotBlank() && snapshot.dayKey == todayDayKey()
        val mergedPreparing = if (shouldMergeInMemory) mergeUnique(snapshot.preparing, inMemoryPreparing) else snapshot.preparing
        val mergedReady = if (shouldMergeInMemory) mergeUnique(snapshot.ready, inMemoryReady) else snapshot.ready
        val mergedReserved = if (shouldMergeInMemory) mergeUnique(snapshot.reserved, inMemoryReserved) else snapshot.reserved
        val mergedNext = if (shouldMergeInMemory) maxOf(snapshot.nextSequential, inMemoryNext) else snapshot.nextSequential

        // Load the last assign time from snapshot
        lastAssignTime = snapshot.lastAssignTime

        _preparing.value = mergedPreparing
        _ready.value = mergedReady
        _reserved.value = mergedReserved
        _nextSequential.value = mergedNext

        if (
            mergedPreparing != snapshot.preparing ||
            mergedReady != snapshot.ready ||
            mergedReserved != snapshot.reserved ||
            mergedNext != snapshot.nextSequential
        ) {
            persistAsync(
                CallingSnapshot(
                    preparing = mergedPreparing,
                    ready = mergedReady,
                    reserved = mergedReserved,
                    nextSequential = mergedNext,
                    dayKey = currentDayKey.ifBlank { snapshot.dayKey },
                    lastAssignTime = lastAssignTime
                )
            )
        }
    }

    @Synchronized
    fun assignNext(): AssignResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        val number = normalizeToRange(_nextSequential.value, range)
        val overtaken = if (activeNumbers().contains(number)) number else null
        if (overtaken != null) {
            clearNumberInternal(overtaken)
        }
        _nextSequential.value = incrementAndWrap(number, range)

        // Record the assignment time
        lastAssignTime = System.currentTimeMillis()

        // Ensure we don't keep the same number in reserved if it was previously reserved.
        _reserved.update { it.filterNot { n -> n == number } }
        _preparing.update { (it + number).distinct() }
        persistAsyncSnapshot()
        return AssignResult(number = number, overtakenNumber = overtaken)
    }

    @Synchronized
    fun reserveNext(): AssignResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        val number = normalizeToRange(_nextSequential.value, range)
        val overtaken = if (activeNumbers().contains(number)) number else null
        if (overtaken != null) {
            clearNumberInternal(overtaken)
        }
        _nextSequential.value = incrementAndWrap(number, range)

        _reserved.update { (it + number).distinct() }
        persistAsyncSnapshot()
        return AssignResult(number = number, overtakenNumber = overtaken)
    }

    fun activateReserved(number: Int) {
        var changed = false
        _reserved.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }
        _preparing.update { list ->
            if (!list.contains(number) && !_ready.value.contains(number)) {
                changed = true
                (list + number).distinct()
            } else {
                list
            }
        }
        if (changed) persistAsyncSnapshot()
    }

    fun markReady(number: Int) {
        var changed = false
        _preparing.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }
        _ready.update { list ->
            if (!list.contains(number)) {
                changed = true
                (list + number)
            } else {
                list
            }
        }
        if (changed) persistAsyncSnapshot()
    }

    fun addManualReady(number: Int): ManualReadyAddResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        if (number !in range) return ManualReadyAddResult.OutOfRange

        if (_ready.value.contains(number)) return ManualReadyAddResult.Duplicate

        var changed = false

        _preparing.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }

        _reserved.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }

        _ready.update { list ->
            if (!list.contains(number)) {
                changed = true
                (list + number)
            } else {
                list
            }
        }

        if (changed) persistAsyncSnapshot()
        return if (changed) ManualReadyAddResult.Added else ManualReadyAddResult.Duplicate
    }

    fun addManualPreparing(number: Int): ManualReadyAddResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        if (number !in range) return ManualReadyAddResult.OutOfRange

        if (_preparing.value.contains(number)) return ManualReadyAddResult.Duplicate

        var changed = false

        _ready.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }

        _reserved.update { list ->
            if (list.contains(number)) {
                changed = true
                list.filterNot { it == number }
            } else {
                list
            }
        }

        _preparing.update { list ->
            if (!list.contains(number)) {
                changed = true
                (list + number)
            } else {
                list
            }
        }

        if (changed) persistAsyncSnapshot()
        return if (changed) ManualReadyAddResult.Added else ManualReadyAddResult.Duplicate
    }

    fun complete(number: Int) {
        val before = _ready.value
        if (!before.contains(number)) return
        _ready.value = before.filterNot { it == number }
        persistAsyncSnapshot()
    }

    private fun clearNumberInternal(number: Int) {
        _preparing.update { list -> list.filterNot { it == number } }
        _ready.update { list -> list.filterNot { it == number } }
        _reserved.update { list -> list.filterNot { it == number } }
    }

    fun clearAll() {
        ensureDayUpToDate()
        _preparing.value = emptyList()
        _ready.value = emptyList()
        _reserved.value = emptyList()
        val range = callingNumberRange()
        _nextSequential.value = range.first
        persistAsyncSnapshot()
    }

    fun clearPreparing() {
        ensureDayUpToDate()
        _preparing.value = emptyList()
        persistAsyncSnapshot()
    }

    fun clearReady() {
        ensureDayUpToDate()
        _ready.value = emptyList()
        persistAsyncSnapshot()
    }

    private fun activeNumbers(): Set<Int> {
        return (_preparing.value + _ready.value + _reserved.value).toHashSet()
    }

    private fun callingNumberRange(): IntRange {
        if (!initialized) return 1..99
        val minRaw = runCatching { CashRegisterDebugConfig.callingNumberMin(appContext) }.getOrDefault(1)
        val maxRaw = runCatching { CashRegisterDebugConfig.callingNumberMax(appContext) }.getOrDefault(99)

        val min = minRaw.coerceAtLeast(1)
        val max = maxRaw.coerceAtMost(999)
        return if (max >= min) {
            min..max
        } else {
            1..99
        }
    }

    private fun normalizeToRange(value: Int, range: IntRange): Int {
        return if (value in range) value else range.first
    }

    private fun incrementAndWrap(value: Int, range: IntRange): Int {
        return if (value >= range.last) range.first else value + 1
    }

    private fun mergeUnique(primary: List<Int>, secondary: List<Int>): List<Int> {
        if (secondary.isEmpty()) return primary
        val set = primary.toMutableSet()
        set.addAll(secondary)
        return set.toList()
    }

    private fun persistAsyncSnapshot() {
        persistAsync(
            CallingSnapshot(
                preparing = _preparing.value,
                ready = _ready.value,
                reserved = _reserved.value,
                nextSequential = _nextSequential.value,
                dayKey = currentDayKey,
                lastAssignTime = lastAssignTime
            )
        )
    }

    private fun persistAsync(snapshot: CallingSnapshot) {
        if (!initialized) return
        scope.launch {
            try {
                val raw = json.encodeToString(snapshot)
                appContext.callingDataStore.edit { it[KEY_SNAPSHOT] = raw }
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}

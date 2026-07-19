package com.cofopt.cashregister.calling

import android.content.Context
import android.util.Log
import com.cofopt.cashregister.CashRegisterDebugConfig
import com.cofopt.cashregister.persistence.RepositoryPersistenceStatus
import com.cofopt.cashregister.persistence.persistenceRetryDelayMillis
import com.cofopt.shared.network.OrderPayload
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicLong

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
        val lastAssignTime: Long = 0L,
        val orderBackedNumbers: List<Int> = emptyList(),
        val suppressedNumbers: List<Int> = emptyList(),
    )

    @Volatile
    private var initialized = false
    @Volatile
    private var loaded = false
    private lateinit var appContext: Context

    private val loadMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val persistenceGeneration = AtomicLong(0L)
    private val orderBackedNumbers = linkedSetOf<Int>()
    private val suppressedNumbers = linkedSetOf<Int>()

    private val _persistenceStatus = MutableStateFlow(RepositoryPersistenceStatus())
    val persistenceStatus: StateFlow<RepositoryPersistenceStatus> = _persistenceStatus

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
        orderBackedNumbers.clear()
        suppressedNumbers.clear()
        _nextSequential.value = range.first
        lastAssignTime = 0L
        persistAsyncSnapshot()
    }

    suspend fun ensureLoaded(context: Context) {
        loadMutex.withLock {
            if (loaded) return

            if (!initialized) {
                appContext = context.applicationContext
                initialized = true
            }

            val prefs = appContext.callingDataStore.data.first()
            val raw = prefs[KEY_SNAPSHOT]
            val loadedSnapshot = raw?.let {
                try {
                    json.decodeFromString(CallingSnapshot.serializer(), it)
                } catch (error: Exception) {
                    Log.e(TAG, "Refusing to replace an unreadable calling snapshot", error)
                    throw IllegalStateException("calling_snapshot_unreadable", error)
                }
            } ?: CallingSnapshot()

            synchronized(this@CallingRepository) {
                val snapshot = resetForNewDayIfNeeded(loadedSnapshot)

                // Capture live state after the suspending DataStore read. Live transitions win
                // over persisted state, and every number is kept in exactly one bucket.
                val liveReady = _ready.value.distinct()
                val livePreparing = _preparing.value.filterNot { it in liveReady }.distinct()
                val liveReserved = _reserved.value
                    .filterNot { it in liveReady || it in livePreparing }
                    .distinct()
                val liveActive = (liveReady + livePreparing + liveReserved).toHashSet()
                val liveOrderBacked = orderBackedNumbers.filterTo(linkedSetOf()) { it in liveActive }
                val liveSuppressed = suppressedNumbers.toSet()

                val persistedReady = snapshot.ready.filterNot { it in liveActive }.distinct()
                val persistedPreparing = snapshot.preparing
                    .filterNot { it in liveActive || it in persistedReady }
                    .distinct()
                val persistedReserved = snapshot.reserved
                    .filterNot { it in liveActive || it in persistedReady || it in persistedPreparing }
                    .distinct()

                val mergedReady = liveReady + persistedReady
                val mergedPreparing = livePreparing + persistedPreparing
                val mergedReserved = liveReserved + persistedReserved
                val mergedActive = (mergedReady + mergedPreparing + mergedReserved).toHashSet()
                val mergedOrderBacked = linkedSetOf<Int>().apply {
                    addAll(liveOrderBacked)
                    addAll(snapshot.orderBackedNumbers.filter { it in mergedActive })
                }
                val mergedSuppressed = linkedSetOf<Int>().apply {
                    addAll(liveSuppressed)
                    // Any live state transition for a number is newer than the disk snapshot.
                    addAll(snapshot.suppressedNumbers.filterNot { it in liveActive })
                }
                val liveNext = _nextSequential.value
                val hasLiveAllocation = liveActive.isNotEmpty() || liveNext != 1 || lastAssignTime != 0L
                val mergedNext = if (hasLiveAllocation) liveNext else snapshot.nextSequential
                val mergedLastAssignTime = if (hasLiveAllocation) {
                    maxOf(lastAssignTime, snapshot.lastAssignTime)
                } else {
                    snapshot.lastAssignTime
                }

                _preparing.value = mergedPreparing
                _ready.value = mergedReady
                _reserved.value = mergedReserved
                orderBackedNumbers.clear()
                orderBackedNumbers.addAll(mergedOrderBacked)
                suppressedNumbers.clear()
                suppressedNumbers.addAll(mergedSuppressed)
                _nextSequential.value = mergedNext
                lastAssignTime = mergedLastAssignTime
                loaded = true

                val mergedSnapshot = CallingSnapshot(
                    preparing = mergedPreparing,
                    ready = mergedReady,
                    reserved = mergedReserved,
                    nextSequential = mergedNext,
                    dayKey = currentDayKey.ifBlank { snapshot.dayKey },
                    lastAssignTime = mergedLastAssignTime,
                    orderBackedNumbers = mergedOrderBacked.toList(),
                    suppressedNumbers = mergedSuppressed.toList(),
                )
                if (mergedSnapshot != loadedSnapshot) {
                    // Schedule under the same monitor as mutations so this load write is older
                    // than every state change that follows it.
                    persistAsync(mergedSnapshot)
                }
            }
        }
    }

    /** Repairs one-sided order/calling persistence after an interrupted commit. */
    @Synchronized
    fun reconcileWithOrders(orders: List<OrderPayload>): Boolean {
        ensureDayUpToDate()
        val current = CallingReconcileState(
            preparing = _preparing.value,
            ready = _ready.value,
            reserved = _reserved.value,
            orderBackedNumbers = orderBackedNumbers,
            suppressedNumbers = suppressedNumbers,
        )
        val reconciled = reconcileCallingState(current, orders)
        if (reconciled == current) return false

        _preparing.value = reconciled.preparing
        _ready.value = reconciled.ready
        _reserved.value = reconciled.reserved
        orderBackedNumbers.clear()
        orderBackedNumbers.addAll(reconciled.orderBackedNumbers)
        suppressedNumbers.clear()
        suppressedNumbers.addAll(reconciled.suppressedNumbers)
        persistAsyncSnapshot()
        return true
    }

    /** Immediately releases numbers whose owning orders were removed from the order store. */
    @Synchronized
    fun releaseOrderBackedNumbers(numbers: Set<Int>): Boolean {
        if (numbers.isEmpty()) return false
        ensureDayUpToDate()
        var changed = false
        numbers.forEach { number ->
            if (
                number in _preparing.value ||
                number in _ready.value ||
                number in _reserved.value ||
                number in orderBackedNumbers ||
                number in suppressedNumbers
            ) {
                clearNumberInternal(number)
                suppressedNumbers.remove(number)
                changed = true
            }
        }
        if (changed) persistAsyncSnapshot()
        return changed
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
        suppressedNumbers.remove(number)
        orderBackedNumbers.add(number)
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
        suppressedNumbers.remove(number)
        orderBackedNumbers.add(number)
        persistAsyncSnapshot()
        return AssignResult(number = number, overtakenNumber = overtaken)
    }

    @Synchronized
    fun activateReserved(number: Int) {
        ensureDayUpToDate()
        var changed = suppressedNumbers.remove(number)
        changed = orderBackedNumbers.add(number) || changed
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

    @Synchronized
    fun markReady(number: Int) {
        ensureDayUpToDate()
        var changed = suppressedNumbers.remove(number)
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

    @Synchronized
    fun addManualReady(number: Int): ManualReadyAddResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        if (number !in range) return ManualReadyAddResult.OutOfRange

        if (_ready.value.contains(number)) return ManualReadyAddResult.Duplicate

        var changed = suppressedNumbers.remove(number)

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

    @Synchronized
    fun addManualPreparing(number: Int): ManualReadyAddResult {
        ensureDayUpToDate()
        val range = callingNumberRange()
        if (number !in range) return ManualReadyAddResult.OutOfRange

        if (_preparing.value.contains(number)) return ManualReadyAddResult.Duplicate

        var changed = suppressedNumbers.remove(number)

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

    @Synchronized
    fun complete(number: Int) {
        ensureDayUpToDate()
        val before = _ready.value
        if (!before.contains(number)) return
        _ready.value = before.filterNot { it == number }
        orderBackedNumbers.remove(number)
        // Persist a tombstone until the owning order itself becomes terminal.
        // This closes the crash window between the Calling and Orders stores:
        // reconciliation must not resurrect a number completed by the operator.
        suppressedNumbers.add(number)
        persistAsyncSnapshot()
    }

    private fun clearNumberInternal(number: Int) {
        _preparing.update { list -> list.filterNot { it == number } }
        _ready.update { list -> list.filterNot { it == number } }
        _reserved.update { list -> list.filterNot { it == number } }
        orderBackedNumbers.remove(number)
    }

    @Synchronized
    fun clearAll() {
        ensureDayUpToDate()
        // Suppress every cleared number, not only numbers carrying the new ownership marker. This
        // also covers migration/startup races where an older snapshot has not been reconciled yet.
        // Reconciliation drops suppression automatically when no order owns the number.
        suppressedNumbers.addAll(activeNumbers())
        _preparing.value = emptyList()
        _ready.value = emptyList()
        _reserved.value = emptyList()
        orderBackedNumbers.clear()
        val range = callingNumberRange()
        _nextSequential.value = range.first
        persistAsyncSnapshot()
    }

    @Synchronized
    fun clearPreparing() {
        ensureDayUpToDate()
        suppressedNumbers.addAll(_preparing.value)
        _preparing.value = emptyList()
        orderBackedNumbers.retainAll(activeNumbers())
        persistAsyncSnapshot()
    }

    @Synchronized
    fun clearReady() {
        ensureDayUpToDate()
        suppressedNumbers.addAll(_ready.value)
        _ready.value = emptyList()
        orderBackedNumbers.retainAll(activeNumbers())
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

    private fun persistAsyncSnapshot() {
        persistAsync(currentSnapshot())
    }

    private fun currentSnapshot(): CallingSnapshot {
        return CallingSnapshot(
            preparing = _preparing.value,
            ready = _ready.value,
            reserved = _reserved.value,
            nextSequential = _nextSequential.value,
            dayKey = currentDayKey,
            lastAssignTime = lastAssignTime,
            orderBackedNumbers = orderBackedNumbers.filter { it in activeNumbers() },
            suppressedNumbers = suppressedNumbers.toList(),
        )
    }

    private fun persistAsync(snapshot: CallingSnapshot) {
        if (!loaded) return
        val generation = persistenceGeneration.incrementAndGet()
        markPersistencePending()
        scope.launch {
            var failedAttempt = 0
            while (generation == persistenceGeneration.get()) {
                val result: Result<Unit>? = persistenceMutex.withLock {
                    if (generation != persistenceGeneration.get()) {
                        null
                    } else {
                        writeSnapshot(snapshot)
                    }
                }
                if (result == null) return@launch
                if (result.isSuccess) {
                    if (generation == persistenceGeneration.get()) markPersistenceSucceeded()
                    return@launch
                }

                failedAttempt += 1
                val failure = requireNotNull(result.exceptionOrNull())
                if (generation != persistenceGeneration.get()) return@launch
                markPersistenceFailed(failedAttempt, failure)
                delay(persistenceRetryDelayMillis(failedAttempt))
            }
        }
    }

    /** Waits until a current calling snapshot is durably committed. */
    suspend fun persistNow(): Boolean {
        if (!loaded) return false
        val persisted = persistenceMutex.withLock {
            var currentWriteCompleted = false
            while (!currentWriteCompleted) {
                val (snapshot, generation) = synchronized(this@CallingRepository) {
                    currentSnapshot() to persistenceGeneration.incrementAndGet()
                }
                markPersistencePending()
                var result: Result<Unit>
                var failedAttempt = 0
                do {
                    result = writeSnapshot(snapshot)
                    if (result.isFailure) {
                        failedAttempt += 1
                        markPersistenceFailed(failedAttempt, requireNotNull(result.exceptionOrNull()))
                        if (failedAttempt < IMMEDIATE_PERSIST_ATTEMPTS) {
                            delay(persistenceRetryDelayMillis(failedAttempt))
                        }
                    }
                } while (result.isFailure && failedAttempt < IMMEDIATE_PERSIST_ATTEMPTS)

                if (result.isFailure) return@withLock false
                currentWriteCompleted = generation == persistenceGeneration.get()
            }
            markPersistenceSucceeded()
            true
        }
        if (!persisted) {
            val latest = synchronized(this@CallingRepository) { currentSnapshot() }
            persistAsync(latest)
        }
        return persisted
    }

    private suspend fun writeSnapshot(snapshot: CallingSnapshot): Result<Unit> {
        return try {
            val raw = json.encodeToString(snapshot)
            appContext.callingDataStore.edit { it[KEY_SNAPSHOT] = raw }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun markPersistencePending() {
        _persistenceStatus.value = _persistenceStatus.value.copy(
            pending = true,
            retryAttempt = 0,
        )
    }

    private fun markPersistenceSucceeded() {
        _persistenceStatus.value = RepositoryPersistenceStatus()
    }

    private fun markPersistenceFailed(attempt: Int, error: Throwable) {
        val message = "${error::class.simpleName}: ${error.message ?: "unknown persistence error"}"
        _persistenceStatus.value = RepositoryPersistenceStatus(
            pending = true,
            retryAttempt = attempt,
            lastError = message,
        )
        Log.e(TAG, "Calling snapshot persistence failed; retry attempt=$attempt", error)
    }

    private const val TAG = "CallingRepository"
    private const val IMMEDIATE_PERSIST_ATTEMPTS = 3
}

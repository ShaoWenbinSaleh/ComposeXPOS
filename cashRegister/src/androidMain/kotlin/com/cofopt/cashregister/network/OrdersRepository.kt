package com.cofopt.cashregister.network

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.menu.DishesRepository
import com.cofopt.cashregister.persistence.RepositoryPersistenceStatus
import com.cofopt.cashregister.persistence.persistenceRetryDelayMillis
import com.cofopt.cashregister.printer.PrintUtils

object OrdersRepository {
    private const val COMPACT_TO_FILE_AFTER_DAYS = 30L

    private val _todayOrders = MutableStateFlow<List<OrderPayload>>(emptyList())
    val todayOrders: StateFlow<List<OrderPayload>> = _todayOrders

    private val _archivedOrders = MutableStateFlow<List<OrderPayload>>(emptyList())
    val archivedOrders: StateFlow<List<OrderPayload>> = _archivedOrders

    private val _orders = MutableStateFlow<List<OrderPayload>>(emptyList())
    val orders: StateFlow<List<OrderPayload>> = _orders

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class OrdersSnapshot(
        val active: List<OrderPayload> = emptyList(),
        val archived: List<OrderPayload> = emptyList(),
        val idempotencyLedger: List<OrderIdempotencyLedgerEntry> = emptyList(),
    )

    private data class AddMutation(
        val persistence: PersistenceRequest,
        val shouldKitchenPrint: Boolean,
        val shouldOrderPrint: Boolean,
    )

    private data class OrderMutation(
        val persistence: PersistenceRequest,
        val paidOrderToPrint: OrderPayload? = null,
    )

    private data class RemovalMutation(
        val persistence: PersistenceRequest,
        val callingNumbersToRelease: Set<Int>,
    )

    /**
     * A snapshot and its generation must be captured under [stateLock] as one operation.
     * Otherwise an older mutation can resume later, obtain a newer generation and overwrite a
     * snapshot that already contains a newer order.
     */
    private data class PersistenceRequest(
        val snapshot: OrdersSnapshot,
        val generation: Long,
    )

    @Volatile
    private var initialized = false
    @Volatile
    private var loaded = false
    private lateinit var appContext: Context

    private val stateLock = Any()
    private var idempotencyLedger = OrderIdempotencyLedger()
    private val loadMutex = Mutex()
    private val archiveMutex = Mutex()
    private val persistenceMutex = Mutex()
    private val persistenceGeneration = AtomicLong(0L)

    private val _persistenceStatus = MutableStateFlow(RepositoryPersistenceStatus())
    val persistenceStatus: StateFlow<RepositoryPersistenceStatus> = _persistenceStatus

    private val Context.ordersDataStore by preferencesDataStore(name = "orders_store")
    private val KEY_SNAPSHOT = stringPreferencesKey("orders_snapshot_json")

    suspend fun ensureLoaded(context: Context) {
        loadMutex.withLock {
            if (loaded) return

            if (!initialized) {
                appContext = context.applicationContext
                initialized = true
            }

            val prefs = appContext.ordersDataStore.data.first()
            val raw = prefs[KEY_SNAPSHOT]
            val snapshot = raw?.let {
                try {
                    json.decodeFromString(OrdersSnapshot.serializer(), it)
                } catch (error: Exception) {
                    Log.e(TAG, "Refusing to replace an unreadable order snapshot", error)
                    throw IllegalStateException("orders_snapshot_unreadable", error)
                }
            } ?: OrdersSnapshot()

            val now = System.currentTimeMillis()
            val archivedResult = archiveFromList(
                list = snapshot.active,
                archived = snapshot.archived,
                now = now
            )
            val compactedPersistedArchived = compactOldArchivedOrdersOnLoad(archivedResult.archived, now)
            val restoredLedger = OrderIdempotencyLedger().also { ledger ->
                ledger.restore(snapshot.idempotencyLedger, now)
            }

            synchronized(stateLock) {
                // Read live state only after the suspending load/compaction work. Otherwise an
                // order received while DataStore is loading can be overwritten by the old snapshot.
                val liveOrders = _todayOrders.value + _archivedOrders.value
                val persistedOrders = archivedResult.today + compactedPersistedArchived
                val merged = mergeByOrderId(liveOrders, persistedOrders)
                val (mergedToday, mergedArchived) = merged.partition { isToday(it, now) }

                // Preserve identities accepted before loading completed, then migrate LAN orders
                // written by versions that predate the independent ledger. Any contradictory
                // identity is treated as corrupt state and prevents the server from starting.
                idempotencyLedger.snapshot(now).forEach { entry ->
                    restoreLedgerEntry(restoredLedger, entry, now)
                }
                merged.forEach { order ->
                    val fingerprint = order.submissionFingerprint ?: return@forEach
                    when (restoredLedger.match(order.orderId, fingerprint, now)) {
                        is OrderIdempotencyMatch.Duplicate -> Unit
                        OrderIdempotencyMatch.Conflict -> {
                            throw IllegalStateException("orders_idempotency_ledger_conflict")
                        }
                        OrderIdempotencyMatch.Missing -> {
                            restoreLedgerEntry(
                                ledger = restoredLedger,
                                entry = OrderIdempotencyLedgerEntry(
                                    orderId = order.orderId,
                                    payloadFingerprint = fingerprint,
                                    originalCallNumber = order.callNumber,
                                    recordedAtMillis = order.acceptedAtMillis ?: order.createdAtMillis,
                                ),
                                nowMillis = now,
                            )
                        }
                    }
                }
                val normalizedLedger = restoredLedger.snapshot(now)

                _todayOrders.value = mergedToday
                _archivedOrders.value = mergedArchived
                idempotencyLedger = restoredLedger
                updateCombined()
                loaded = true

                // Schedule this while holding stateLock so any later mutation receives a newer
                // persistence generation and cannot be overwritten by the load snapshot.
                if (
                    mergedToday != snapshot.active ||
                    mergedArchived != snapshot.archived ||
                    normalizedLedger != snapshot.idempotencyLedger
                ) {
                    persistAsync(newPersistenceRequestLocked())
                }
            }
        }
    }

    suspend fun getOrdersForDateWindow(
        date: LocalDate,
        startMinute: Int,
        endMinute: Int
    ): List<OrderPayload> {
        val zone = ZoneId.systemDefault()
        val dayStartMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val safeStart = startMinute.coerceIn(0, 1439)
        val safeEnd = endMinute.coerceIn(0, 1439)
        val start = minOf(safeStart, safeEnd)
        val end = maxOf(safeStart, safeEnd)

        val startMillis = dayStartMillis + start * 60_000L
        val endExclusiveMillis = if (end >= 1439) {
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            dayStartMillis + (end + 1) * 60_000L
        }

        val inMemorySnapshot = synchronized(stateLock) {
            _todayOrders.value + _archivedOrders.value
        }
        val inMemory = inMemorySnapshot
            .asSequence()
            .filter {
                val timestamp = it.storageTimestampMillis()
                timestamp >= startMillis && timestamp < endExclusiveMillis
            }
            .toList()

        val fromCompressed = loadCompressedOrdersForDate(date)
            .asSequence()
            .filter {
                val timestamp = it.storageTimestampMillis()
                timestamp >= startMillis && timestamp < endExclusiveMillis
            }
            .toList()

        return mergeByOrderId(inMemory, fromCompressed)
            .sortedByDescending { it.storageTimestampMillis() }
    }

    fun add(order: OrderPayload) {
        val mutation = synchronized(stateLock) {
            upsertOrderLocked(order, forcePersistence = false)
        } ?: return

        finishAdd(order, mutation)
    }

    /** Looks up a previously accepted LAN identity independently of visible order history. */
    internal fun matchAcceptedLanOrder(
        orderId: String,
        payloadFingerprint: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): OrderIdempotencyMatch = synchronized(stateLock) {
        idempotencyLedger.match(orderId, payloadFingerprint, nowMillis)
    }

    /**
     * Migrates an already-visible LAN order into the ledger. The ledger mutation is persisted even
     * when the order itself is byte-for-byte unchanged.
     */
    internal fun rememberAcceptedLanOrderIdentity(
        order: OrderPayload,
        payloadFingerprint: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): OrderIdempotencyRecordResult {
        var persistence: PersistenceRequest? = null
        val result = synchronized(stateLock) {
            idempotencyLedger.record(
                orderId = order.orderId,
                payloadFingerprint = payloadFingerprint,
                originalCallNumber = order.callNumber,
                recordedAtMillis = order.acceptedAtMillis ?: order.createdAtMillis,
                nowMillis = nowMillis,
            ).also { recordResult ->
                if (recordResult is OrderIdempotencyRecordResult.Recorded) {
                    persistence = newPersistenceRequestLocked()
                }
            }
        }
        persistence?.let(::persistAsync)
        return result
    }

    /** Atomically records a LAN identity and inserts its operator-facing order. */
    internal fun addAcceptedLanOrder(
        order: OrderPayload,
        payloadFingerprint: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): OrderIdempotencyRecordResult {
        var mutation: AddMutation? = null
        val result = synchronized(stateLock) {
            idempotencyLedger.record(
                orderId = order.orderId,
                payloadFingerprint = payloadFingerprint,
                originalCallNumber = order.callNumber,
                recordedAtMillis = order.acceptedAtMillis ?: nowMillis,
                nowMillis = nowMillis,
            ).also { recordResult ->
                if (recordResult is OrderIdempotencyRecordResult.Recorded) {
                    mutation = checkNotNull(upsertOrderLocked(order, forcePersistence = true))
                }
            }
        }
        mutation?.let { finishAdd(order, it) }
        return result
    }

    fun findByOrderId(orderId: String): OrderPayload? = synchronized(stateLock) {
        (_todayOrders.value + _archivedOrders.value).firstOrNull { it.orderId == orderId }
    }

    fun updatePaymentMethod(orderId: String, paymentMethod: String) {
        val snapshot = synchronized(stateLock) {
            archiveIfNeeded(System.currentTimeMillis())
            val existing = (_todayOrders.value + _archivedOrders.value)
                .firstOrNull { it.orderId == orderId }
                ?: return@synchronized null
            if (existing.paymentMethod == paymentMethod) return@synchronized null

            _todayOrders.value = _todayOrders.value.map { order ->
                if (order.orderId == orderId) order.copy(paymentMethod = paymentMethod) else order
            }
            _archivedOrders.value = _archivedOrders.value.map { order ->
                if (order.orderId == orderId) order.copy(paymentMethod = paymentMethod) else order
            }
            updateCombined()
            newPersistenceRequestLocked()
        } ?: return

        persistAsync(snapshot)
    }

    fun updatePaymentStatus(orderId: String, status: String) {
        val mutation = synchronized(stateLock) {
            archiveIfNeeded(System.currentTimeMillis())

            val existing = (_todayOrders.value + _archivedOrders.value)
                .firstOrNull { it.orderId == orderId }
                ?: return@synchronized null
            if (existing.status == status) return@synchronized null

            val isTransitionToPaid = status == "PAID" && existing.status != "PAID"
            val ownsCallingNumber = existing.source.uppercase() in setOf("KIOSK", "CHECKOUT")
            val assignResult = if (isTransitionToPaid && existing.callNumber == null && ownsCallingNumber) {
                CallingRepository.assignNext()
            } else {
                null
            }

            assignResult?.overtakenNumber?.let { overtaken ->
                _todayOrders.value = _todayOrders.value.map { order ->
                    if (order.callNumber == overtaken && order.orderId != orderId) {
                        order.copy(status = "COMPLETED")
                    } else {
                        order
                    }
                }
                _archivedOrders.value = _archivedOrders.value.map { order ->
                    if (order.callNumber == overtaken && order.orderId != orderId) {
                        order.copy(status = "COMPLETED")
                    } else {
                        order
                    }
                }
            }

            val effectiveCallNumber = assignResult?.number ?: existing.callNumber
            if (isTransitionToPaid && ownsCallingNumber && effectiveCallNumber != null) {
                // Also repairs a missing reservation after a partial persistence restore.
                CallingRepository.activateReserved(effectiveCallNumber)
            }

            val updatedOrder = existing.copy(
                status = status,
                callNumber = effectiveCallNumber,
            )
            _todayOrders.value = _todayOrders.value.map { order ->
                if (order.orderId == orderId) updatedOrder else order
            }
            _archivedOrders.value = _archivedOrders.value.map { order ->
                if (order.orderId == orderId) updatedOrder else order
            }
            updateCombined()
            OrderMutation(
                persistence = newPersistenceRequestLocked(),
                paidOrderToPrint = updatedOrder.takeIf { isTransitionToPaid },
            )
        } ?: return

        persistAsync(mutation.persistence)

        mutation.paidOrderToPrint?.takeIf { initialized }?.let { paidOrder ->
            maybePrintKitchenForPaidOrder(paidOrder)
            maybePrintOrderForPaidOrder(paidOrder)
        }
    }

    private fun maybePrintOrderForPaidOrder(order: OrderPayload) {
        if (order.status != "PAID") return

        scope.launch {
            try {
                PrintUtils.printOrder(appContext, order, callNumber = order.callNumber?.toString())
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    private fun maybePrintKitchenForPaidOrder(order: OrderPayload) {
        if (order.status != "PAID") return

        val dishes = DishesRepository.dishes.value
        if (dishes.isEmpty()) return

        val kitchenIds = dishes.filter { it.kitchenPrint }.map { it.id }.toHashSet()
        if (kitchenIds.isEmpty()) return

        val kitchenItems = order.items.filter { kitchenIds.contains(it.menuItemId) }
        if (kitchenItems.isEmpty()) return

        val kitchenTotal = kitchenItems.fold(0.0) { acc, item -> acc + item.unitPrice * item.quantity }
        val kitchenOrder = order.copy(items = kitchenItems, total = kitchenTotal)

        scope.launch {
            try {
                PrintUtils.printKitchen(appContext, kitchenOrder, callNumber = kitchenOrder.callNumber?.toString())
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    fun updateOrderStatusByCallNumber(callNumber: Int, status: String) {
        val snapshot = synchronized(stateLock) {
            archiveIfNeeded(System.currentTimeMillis())
            val target = selectActiveOrderForCallNumber(
                orders = _todayOrders.value + _archivedOrders.value,
                callNumber = callNumber,
            ) ?: return@synchronized null
            if (target.status == status) return@synchronized null

            _todayOrders.value = _todayOrders.value.map { order ->
                if (order.orderId == target.orderId) order.copy(status = status) else order
            }
            _archivedOrders.value = _archivedOrders.value.map { order ->
                if (order.orderId == target.orderId) order.copy(status = status) else order
            }
            updateCombined()
            newPersistenceRequestLocked()
        } ?: return

        persistAsync(snapshot)
    }

    fun clear() {
        val mutation = synchronized(stateLock) {
            val removedOrders = _todayOrders.value + _archivedOrders.value
            if (removedOrders.isEmpty()) {
                return@synchronized null
            }
            _todayOrders.value = emptyList()
            _archivedOrders.value = emptyList()
            updateCombined()
            RemovalMutation(
                persistence = newPersistenceRequestLocked(),
                callingNumbersToRelease = orderBackedCallNumbersToRelease(
                    removedOrders = removedOrders,
                    remainingOrders = emptyList(),
                ),
            )
        }
        mutation?.let {
            persistAsync(it.persistence)
            CallingRepository.releaseOrderBackedNumbers(it.callingNumbersToRelease)
        }
        // Reconcile immediately instead of leaving order-backed preparing/ready/reserved numbers
        // alive until the next process restart.
        CallingRepository.reconcileWithOrders(orders.value)
    }

    fun remove(orderId: String) {
        val mutation = synchronized(stateLock) {
            archiveIfNeeded(System.currentTimeMillis())
            val removedOrder = (_todayOrders.value + _archivedOrders.value)
                .firstOrNull { it.orderId == orderId }
                ?: return@synchronized null
            _todayOrders.value = _todayOrders.value.filterNot { it.orderId == orderId }
            _archivedOrders.value = _archivedOrders.value.filterNot { it.orderId == orderId }
            updateCombined()
            val remainingOrders = _todayOrders.value + _archivedOrders.value
            RemovalMutation(
                persistence = newPersistenceRequestLocked(),
                callingNumbersToRelease = orderBackedCallNumbersToRelease(
                    removedOrders = listOf(removedOrder),
                    remainingOrders = remainingOrders,
                ),
            )
        } ?: return
        persistAsync(mutation.persistence)
        CallingRepository.releaseOrderBackedNumbers(mutation.callingNumbersToRelease)
        CallingRepository.reconcileWithOrders(orders.value)
    }

    fun printReceipt(order: OrderPayload) {
        if (!initialized) return
        scope.launch {
            runCatching {
                PrintUtils.printReceipt(appContext, order, order.callNumber?.toString())
            }
        }
    }

    fun printOrder(order: OrderPayload) {
        if (!initialized) return
        scope.launch {
            runCatching {
                PrintUtils.printOrder(appContext, order, order.callNumber?.toString())
            }
        }
    }

    fun printKitchen(order: OrderPayload) {
        if (!initialized) return
        scope.launch {
            runCatching {
                PrintUtils.printKitchen(appContext, order, order.callNumber?.toString())
            }
        }
    }

    private fun updateCombined() {
        _orders.value = (_todayOrders.value + _archivedOrders.value)
            .sortedByDescending { it.storageTimestampMillis() }
    }

    private fun currentSnapshot(): OrdersSnapshot {
        return OrdersSnapshot(
            active = _todayOrders.value,
            archived = _archivedOrders.value,
            idempotencyLedger = idempotencyLedger.snapshot(System.currentTimeMillis()),
        )
    }

    /** Must be called while holding [stateLock]. */
    private fun upsertOrderLocked(order: OrderPayload, forcePersistence: Boolean): AddMutation? {
        val now = System.currentTimeMillis()
        archiveIfNeeded(now)

        val existing = (_todayOrders.value + _archivedOrders.value)
            .firstOrNull { it.orderId == order.orderId }
        val orderChanged = existing != order
        if (!orderChanged && !forcePersistence) return null

        if (orderChanged) {
            // Upsert by stable order id. HTTP retries and repeated UI events must never leave
            // duplicate rows in either the active or archived collection.
            _todayOrders.update { list -> list.filterNot { it.orderId == order.orderId } }
            _archivedOrders.update { list -> list.filterNot { it.orderId == order.orderId } }
            if (isToday(order, now)) {
                _todayOrders.update { listOf(order) + it }
            } else {
                _archivedOrders.update { listOf(order) + it }
            }
            updateCombined()
        }

        return AddMutation(
            persistence = newPersistenceRequestLocked(),
            shouldKitchenPrint = orderChanged && order.status == "PAID" && existing?.status != "PAID",
            shouldOrderPrint = orderChanged && order.status == "PAID" && existing?.status != "PAID",
        )
    }

    private fun finishAdd(order: OrderPayload, mutation: AddMutation) {
        persistAsync(mutation.persistence)

        if (mutation.shouldKitchenPrint && initialized) {
            maybePrintKitchenForPaidOrder(order)
        }

        if (mutation.shouldOrderPrint && initialized) {
            maybePrintOrderForPaidOrder(order)
        }
    }

    private fun restoreLedgerEntry(
        ledger: OrderIdempotencyLedger,
        entry: OrderIdempotencyLedgerEntry,
        nowMillis: Long,
    ) {
        when (
            ledger.record(
                orderId = entry.orderId,
                payloadFingerprint = entry.payloadFingerprint,
                originalCallNumber = entry.originalCallNumber,
                recordedAtMillis = entry.recordedAtMillis,
                nowMillis = nowMillis,
            )
        ) {
            is OrderIdempotencyRecordResult.Recorded,
            is OrderIdempotencyRecordResult.Duplicate -> Unit
            OrderIdempotencyRecordResult.Conflict -> {
                throw IllegalStateException("orders_idempotency_ledger_conflict")
            }
        }
    }

    /** Must be called while holding [stateLock]. */
    private fun newPersistenceRequestLocked(): PersistenceRequest {
        return PersistenceRequest(
            snapshot = currentSnapshot(),
            generation = persistenceGeneration.incrementAndGet(),
        )
    }

    private fun persistAsync(request: PersistenceRequest) {
        if (!loaded) return
        val generation = request.generation
        val snapshot = request.snapshot
        markPersistencePending()
        scope.launch {
            var failedAttempt = 0
            while (generation == persistenceGeneration.get()) {
                val result: Result<Unit>? = persistenceMutex.withLock {
                    // A slow older write must not land after a newer snapshot.
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

    /** Waits until a current snapshot is durably committed before acknowledging a LAN order. */
    suspend fun persistNow(): Boolean {
        if (!loaded) return false
        val persisted = persistenceMutex.withLock {
            var currentWriteCompleted = false
            while (!currentWriteCompleted) {
                val (snapshot, generation) = synchronized(stateLock) {
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
            val latest = synchronized(stateLock) { newPersistenceRequestLocked() }
            persistAsync(latest)
        }
        return persisted
    }

    private suspend fun writeSnapshot(snapshot: OrdersSnapshot): Result<Unit> {
        return try {
            val raw = json.encodeToString(snapshot)
            appContext.ordersDataStore.edit { it[KEY_SNAPSHOT] = raw }
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
        Log.e(TAG, "Order snapshot persistence failed; retry attempt=$attempt", error)
    }

    private fun archiveIfNeeded(now: Long) {
        val today = _todayOrders.value
        if (today.isEmpty()) return

        val result = archiveFromList(
            list = today,
            archived = _archivedOrders.value,
            now = now
        )

        if (result.today != today || result.archived != _archivedOrders.value) {
            _todayOrders.value = result.today
            _archivedOrders.value = result.archived
            if (initialized) {
                compactOldArchivedOrdersAsync(now)
            }
        }
    }

    private data class ArchiveResult(
        val today: List<OrderPayload>,
        val archived: List<OrderPayload>
    )

    private fun archiveFromList(
        list: List<OrderPayload>,
        archived: List<OrderPayload>,
        now: Long
    ): ArchiveResult {
        val (keepToday, moveToArchive) = list.partition { isToday(it, now) }

        if (moveToArchive.isEmpty()) return ArchiveResult(today = keepToday, archived = archived)

        return ArchiveResult(
            today = keepToday,
            archived = mergeByOrderId(moveToArchive, archived)
        )
    }

    private fun compactOldArchivedOrdersAsync(now: Long) {
        scope.launch {
            compactOldArchivedOrdersInBackground(now)
        }
    }

    private suspend fun compactOldArchivedOrdersOnLoad(archived: List<OrderPayload>, now: Long): List<OrderPayload> {
        return withContext(Dispatchers.IO) {
            archiveMutex.withLock {
                compactOldArchivedOrdersInternal(archived, now)
            }
        }
    }

    private suspend fun compactOldArchivedOrdersInBackground(now: Long) {
        archiveMutex.withLock {
            val current = synchronized(stateLock) { _archivedOrders.value }
            val cutoffMillis = now - COMPACT_TO_FILE_AFTER_DAYS * 24L * 60L * 60L * 1000L
            val move = current.filter { it.storageTimestampMillis() < cutoffMillis }
            if (move.isEmpty() || !writeOrdersToCompressedStore(move)) return@withLock

            val movedById = move.associateBy { it.orderId }
            val snapshot = synchronized(stateLock) {
                // Only remove entries that are still byte-for-byte the version written above.
                // Concurrent inserts, edits and deletes therefore cannot be overwritten by a
                // stale compaction snapshot after the slow file I/O completes.
                val latest = _archivedOrders.value
                val compacted = latest.filterNot { order -> movedById[order.orderId] == order }
                if (compacted == latest) {
                    null
                } else {
                    _archivedOrders.value = compacted
                    updateCombined()
                    newPersistenceRequestLocked()
                }
            }
            snapshot?.let(::persistAsync)
        }
    }

    private suspend fun compactOldArchivedOrdersInternal(archived: List<OrderPayload>, now: Long): List<OrderPayload> {
        if (!initialized) return archived

        val cutoffMillis = now - COMPACT_TO_FILE_AFTER_DAYS * 24L * 60L * 60L * 1000L
        val (keep, move) = archived.partition { it.storageTimestampMillis() >= cutoffMillis }
        if (move.isEmpty()) return archived

        val wrote = writeOrdersToCompressedStore(move)
        return if (wrote) keep else archived
    }

    private fun archiveDir(): File {
        return File(appContext.filesDir, "orders_archive").apply { mkdirs() }
    }

    private fun archiveFileForDate(date: LocalDate): File {
        return File(archiveDir(), "${date}.json.gz")
    }

    private fun localDateFromMillis(millis: Long): LocalDate {
        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    private suspend fun writeOrdersToCompressedStore(orders: List<OrderPayload>): Boolean {
        return withContext(Dispatchers.IO) {
            val byDay = orders.groupBy { localDateFromMillis(it.storageTimestampMillis()) }
            byDay.all { (day, list) ->
                runCatching {
                    val file = archiveFileForDate(day)
                    val existing = readCompressedOrders(file)
                    val merged = mergeByOrderId(list, existing)
                        .sortedByDescending { it.storageTimestampMillis() }
                    writeCompressedOrders(file, merged)
                }.isSuccess
            }
        }
    }

    private fun readCompressedOrders(file: File): List<OrderPayload> {
        if (!file.exists()) return emptyList()
        return FileInputStream(file).use { fis ->
            GZIPInputStream(fis).use { gis ->
                InputStreamReader(gis).use { reader ->
                    val raw = reader.readText()
                    json.decodeFromString(ListSerializer(OrderPayload.serializer()), raw)
                }
            }
        }
    }

    private fun writeCompressedOrders(file: File, orders: List<OrderPayload>) {
        val raw = json.encodeToString(ListSerializer(OrderPayload.serializer()), orders)
        FileOutputStream(file).use { fos ->
            GZIPOutputStream(fos).use { gos ->
                OutputStreamWriter(gos).use { writer ->
                    writer.write(raw)
                }
            }
        }
    }

    private suspend fun loadCompressedOrdersForDate(date: LocalDate): List<OrderPayload> {
        if (!initialized) return emptyList()
        return withContext(Dispatchers.IO) {
            archiveMutex.withLock {
                runCatching { readCompressedOrders(archiveFileForDate(date)) }
                    .onFailure { error ->
                        Log.e(TAG, "Unable to read compressed order archive for $date", error)
                    }
                    .getOrDefault(emptyList())
            }
        }
    }

    private fun isToday(order: OrderPayload, now: Long): Boolean {
        return localDateFromMillis(order.storageTimestampMillis()) == localDateFromMillis(now)
    }

    private fun mergeByOrderId(primary: List<OrderPayload>, secondary: List<OrderPayload>): List<OrderPayload> {
        return mergeOrdersByIdPreferFirst(primary, secondary)
    }

    private const val TAG = "OrdersRepository"
    private const val IMMEDIATE_PERSIST_ATTEMPTS = 3
}

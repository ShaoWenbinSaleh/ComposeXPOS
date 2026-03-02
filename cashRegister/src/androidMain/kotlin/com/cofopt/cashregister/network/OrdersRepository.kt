package com.cofopt.cashregister.network

import android.content.Context
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
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import com.cofopt.cashregister.calling.CallingRepository
import com.cofopt.cashregister.cmp.utils.startOfTodayMillis
import com.cofopt.cashregister.menu.DishesRepository
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
        val archived: List<OrderPayload> = emptyList()
    )

    private var initialized = false
    private lateinit var appContext: Context

    private val archiveMutex = Mutex()

    private val Context.ordersDataStore by preferencesDataStore(name = "orders_store")
    private val KEY_SNAPSHOT = stringPreferencesKey("orders_snapshot_json")

    suspend fun ensureLoaded(context: Context) {
        if (!initialized) {
            appContext = context.applicationContext
            initialized = true
        }

        // Keep any orders that might have been received before persistence finished loading.
        val inMemoryToday = _todayOrders.value
        val inMemoryArchived = _archivedOrders.value

        val prefs = appContext.ordersDataStore.data.first()
        val raw = prefs[KEY_SNAPSHOT]
        val snapshot = raw?.let {
            try {
                json.decodeFromString(OrdersSnapshot.serializer(), it)
            } catch (_: Exception) {
                OrdersSnapshot()
            }
        } ?: OrdersSnapshot()

        val now = System.currentTimeMillis()
        val archivedResult = archiveFromList(
            list = snapshot.active,
            archived = snapshot.archived,
            now = now
        )

        val mergedToday = mergeByOrderId(archivedResult.today, inMemoryToday)
        val mergedArchivedAll = mergeByOrderId(archivedResult.archived, inMemoryArchived)
        val mergedArchived = compactOldArchivedOrdersOnLoad(mergedArchivedAll, now)

        _todayOrders.value = mergedToday
        _archivedOrders.value = mergedArchived
        updateCombined()

        // persist if we changed anything (e.g. auto-archived previous day or compacted old data)
        if (mergedToday != snapshot.active || mergedArchived != snapshot.archived) {
            persistAsync(OrdersSnapshot(active = mergedToday, archived = mergedArchived))
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

        val inMemory = (_todayOrders.value + _archivedOrders.value)
            .asSequence()
            .filter { it.createdAtMillis >= startMillis && it.createdAtMillis < endExclusiveMillis }
            .toList()

        val fromCompressed = loadCompressedOrdersForDate(date)
            .asSequence()
            .filter { it.createdAtMillis >= startMillis && it.createdAtMillis < endExclusiveMillis }
            .toList()

        return mergeByOrderId(inMemory, fromCompressed)
            .sortedByDescending { it.createdAtMillis }
    }

    fun add(order: OrderPayload) {
        archiveIfNeeded(System.currentTimeMillis())

        val existing = (_todayOrders.value + _archivedOrders.value).firstOrNull { it.orderId == order.orderId }
        val shouldKitchenPrint =
            order.status == "PAID" && existing?.status != "PAID"
        val shouldOrderPrint =
            order.status == "PAID" && existing?.status != "PAID"

        val now = System.currentTimeMillis()
        if (isToday(order.createdAtMillis, now)) {
            _todayOrders.update { listOf(order) + it }
        } else {
            _archivedOrders.update { listOf(order) + it }
        }
        updateCombined()
        persistAsyncSnapshot()

        if (shouldKitchenPrint && initialized) {
            maybePrintKitchenForPaidOrder(order)
        }

        if (shouldOrderPrint && initialized) {
            maybePrintOrderForPaidOrder(order)
        }
    }

    fun updatePaymentMethod(orderId: String, paymentMethod: String) {
        archiveIfNeeded(System.currentTimeMillis())

        _todayOrders.update { list ->
            list.map { o -> if (o.orderId == orderId) o.copy(paymentMethod = paymentMethod) else o }
        }
        _archivedOrders.update { list ->
            list.map { o -> if (o.orderId == orderId) o.copy(paymentMethod = paymentMethod) else o }
        }
        updateCombined()
        persistAsyncSnapshot()
    }

    fun updatePaymentStatus(orderId: String, status: String) {
        archiveIfNeeded(System.currentTimeMillis())

        val existing = (_todayOrders.value + _archivedOrders.value).firstOrNull { it.orderId == orderId }
        val isTransitionToPaid =
            status == "PAID" && existing?.status != "PAID"

        val shouldAssignCallNumber =
            isTransitionToPaid &&
                existing?.callNumber == null &&
                (existing?.source == "KIOSK" || existing?.source == "CHECKOUT")

        val assignResult = if (shouldAssignCallNumber) {
            CallingRepository.assignNext()
        } else {
            null
        }

        val overtaken = assignResult?.overtakenNumber
        if (overtaken != null) {
            _todayOrders.update { list ->
                list.map { o ->
                    if (o.callNumber == overtaken && o.orderId != orderId) {
                        o.copy(status = "COMPLETED")
                    } else {
                        o
                    }
                }
            }
            _archivedOrders.update { list ->
                list.map { o ->
                    if (o.callNumber == overtaken && o.orderId != orderId) {
                        o.copy(status = "COMPLETED")
                    } else {
                        o
                    }
                }
            }
        }

        val assignedCallNumber = assignResult?.number

        val effectiveCallNumber = assignedCallNumber ?: existing?.callNumber

        val shouldActivateCalling =
            isTransitionToPaid &&
                existing?.callNumber != null &&
                (existing?.source == "KIOSK" || existing?.source == "CHECKOUT") &&
                CallingRepository.reserved.value.contains(existing.callNumber!!)

        val shouldKitchenPrint = isTransitionToPaid

        val shouldOrderPrint = isTransitionToPaid

        _todayOrders.update { list ->
            list.map { o ->
                if (o.orderId == orderId) {
                    o.copy(status = status, callNumber = effectiveCallNumber ?: o.callNumber)
                } else {
                    o
                }
            }
        }
        _archivedOrders.update { list ->
            list.map { o ->
                if (o.orderId == orderId) {
                    o.copy(status = status, callNumber = effectiveCallNumber ?: o.callNumber)
                } else {
                    o
                }
            }
        }
        updateCombined()
        persistAsyncSnapshot()

        if (shouldActivateCalling) {
            CallingRepository.activateReserved(existing!!.callNumber!!)
        }

        val updatedExisting = existing?.copy(status = status, callNumber = effectiveCallNumber ?: existing.callNumber)

        if (shouldKitchenPrint && initialized && updatedExisting != null) {
            maybePrintKitchenForPaidOrder(updatedExisting)
        }

        if (shouldOrderPrint && initialized && updatedExisting != null) {
            maybePrintOrderForPaidOrder(updatedExisting)
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
        archiveIfNeeded(System.currentTimeMillis())

        _todayOrders.update { list ->
            list.map { o -> 
                if (o.callNumber == callNumber) {
                    o.copy(status = status)
                } else {
                    o
                }
            }
        }
        _archivedOrders.update { list ->
            list.map { o -> 
                if (o.callNumber == callNumber) {
                    o.copy(status = status)
                } else {
                    o
                }
            }
        }
        updateCombined()
        persistAsyncSnapshot()
    }

    fun clear() {
        _todayOrders.value = emptyList()
        _archivedOrders.value = emptyList()
        updateCombined()
        persistAsync(OrdersSnapshot())
    }

    fun remove(orderId: String) {
        archiveIfNeeded(System.currentTimeMillis())
        _todayOrders.update { list -> list.filterNot { it.orderId == orderId } }
        _archivedOrders.update { list -> list.filterNot { it.orderId == orderId } }
        updateCombined()
        persistAsyncSnapshot()
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
            .sortedByDescending { it.createdAtMillis }
    }

    private fun persistAsyncSnapshot() {
        val snapshot = OrdersSnapshot(active = _todayOrders.value, archived = _archivedOrders.value)
        persistAsync(snapshot)
    }

    private fun persistAsync(snapshot: OrdersSnapshot) {
        if (!initialized) return
        scope.launch {
            try {
                val raw = json.encodeToString(snapshot)
                appContext.ordersDataStore.edit { it[KEY_SNAPSHOT] = raw }
            } catch (_: Exception) {
                // ignore
            }
        }
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
        val (keepToday, moveToArchive) = list.partition { isToday(it.createdAtMillis, now) }

        if (moveToArchive.isEmpty()) return ArchiveResult(today = keepToday, archived = archived)

        val existingIds = archived.asSequence().map { it.orderId }.toHashSet()
        val toAdd = moveToArchive.filter { it.orderId !in existingIds }
        return ArchiveResult(
            today = keepToday,
            archived = (toAdd + archived).sortedByDescending { it.createdAtMillis }
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
            val current = _archivedOrders.value
            val compacted = compactOldArchivedOrdersInternal(current, now)
            if (compacted != current) {
                _archivedOrders.value = compacted
                updateCombined()
                persistAsync(OrdersSnapshot(active = _todayOrders.value, archived = compacted))
            }
        }
    }

    private suspend fun compactOldArchivedOrdersInternal(archived: List<OrderPayload>, now: Long): List<OrderPayload> {
        if (!initialized) return archived

        val cutoffMillis = now - COMPACT_TO_FILE_AFTER_DAYS * 24L * 60L * 60L * 1000L
        val (keep, move) = archived.partition { it.createdAtMillis >= cutoffMillis }
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
            val byDay = orders.groupBy { localDateFromMillis(it.createdAtMillis) }
            byDay.all { (day, list) ->
                runCatching {
                    val file = archiveFileForDate(day)
                    val existing = readCompressedOrders(file)
                    val merged = mergeByOrderId(existing, list)
                        .sortedByDescending { it.createdAtMillis }
                    writeCompressedOrders(file, merged)
                }.isSuccess
            }
        }
    }

    private fun readCompressedOrders(file: File): List<OrderPayload> {
        if (!file.exists()) return emptyList()
        return try {
            FileInputStream(file).use { fis ->
                GZIPInputStream(fis).use { gis ->
                    InputStreamReader(gis).use { reader ->
                        val raw = reader.readText()
                        json.decodeFromString(ListSerializer(OrderPayload.serializer()), raw)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
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
                readCompressedOrders(archiveFileForDate(date))
            }
        }
    }

    private fun isToday(createdAtMillis: Long, now: Long): Boolean {
        return createdAtMillis >= startOfTodayMillis(now)
    }

    private fun mergeByOrderId(primary: List<OrderPayload>, secondary: List<OrderPayload>): List<OrderPayload> {
        if (secondary.isEmpty()) return primary
        val existing = primary.asSequence().map { it.orderId }.toHashSet()
        val toAdd = secondary.filter { it.orderId !in existing }
        return (toAdd + primary).sortedByDescending { it.createdAtMillis }
    }
}

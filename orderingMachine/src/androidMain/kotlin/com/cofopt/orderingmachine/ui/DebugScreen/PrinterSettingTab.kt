package com.cofopt.orderingmachine.ui.DebugScreen

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.network.PrinterConfig
import com.cofopt.orderingmachine.network.CashRegisterOrderPayload
import com.cofopt.orderingmachine.network.CashRegisterOrderItemPayload
import com.cofopt.orderingmachine.ui.PaymentScreen.OrderPrint
import com.cofopt.orderingmachine.ui.common.components.BusyActionButton
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard
import com.cofopt.orderingmachine.ui.common.components.LabeledRadioOption
import com.cofopt.orderingmachine.printer.SunmiPrinterHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Socket

@Composable
fun PrinterSettingTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val usbManager = remember(context) { context.getSystemService(Context.USB_SERVICE) as UsbManager }

    var mode by remember { mutableStateOf(PrinterConfig.mode(context)) }
    var sunmiServiceConnected by remember { mutableStateOf(false) }
    var sunmiPrinterInfo by remember { mutableStateOf<String?>(null) }

    var printerIp by remember { mutableStateOf(PrinterConfig.ip(context)) }
    var printerPortText by remember { mutableStateOf(PrinterConfig.port(context).toString()) }

    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var selectedUsbDevice by remember { mutableStateOf<UsbDevice?>(null) }
    var lastStatus by remember { mutableStateOf<String?>(null) }
    var isPrinting by remember { mutableStateOf(false) }
    var usbPermissionGrantedFor by remember { mutableStateOf<String?>(null) }

    val actionUsbPermission = remember { "com.cofopt.orderingmachine.USB_PERMISSION" }

    fun refreshUsbDevices() {
        val list = usbManager.deviceList?.values?.toList().orEmpty()
        usbDevices = list

        if (selectedUsbDevice == null || list.none { it.deviceId == selectedUsbDevice?.deviceId }) {
            selectedUsbDevice = list.firstOrNull { isLikelyPrinter(it) } ?: list.firstOrNull()
        }
    }

    DisposableEffect(context) {
        if (mode == "SUNMI") {
            SunmiPrinterHelper.bindService(context)
            scope.launch {
                delay(1000)
                sunmiServiceConnected = SunmiPrinterHelper.isServiceConnected()
                if (sunmiServiceConnected) {
                    sunmiPrinterInfo = SunmiPrinterHelper.getPrinterInfo()
                }
            }
        }
        
        onDispose {
            if (mode == "SUNMI") {
                SunmiPrinterHelper.unbindService(context)
            }
        }
    }

    DisposableEffect(context, usbManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != actionUsbPermission) return
                val device = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice
                }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null) {
                    if (granted) {
                        usbPermissionGrantedFor = device.deviceName
                        lastStatus = "USB permission granted"
                    } else {
                        lastStatus = "USB permission denied"
                    }
                }
            }
        }

        context.registerReceiver(receiver, IntentFilter(actionUsbPermission))
        refreshUsbDevices()

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun connectSunmiPrinter() {
        scope.launch {
            try {
                val bound = SunmiPrinterHelper.bindService(context)
                if (bound) {
                    delay(1000)
                    sunmiServiceConnected = SunmiPrinterHelper.isServiceConnected()
                    if (sunmiServiceConnected) {
                        sunmiPrinterInfo = SunmiPrinterHelper.getPrinterInfo()
                        lastStatus = "Sunmi printer connected: $sunmiPrinterInfo"
                    } else {
                        lastStatus = "Sunmi printer service not responding"
                    }
                } else {
                    lastStatus = "Failed to bind Sunmi printer service"
                }
            } catch (e: Exception) {
                lastStatus = "Sunmi connection error: ${e.message}"
            }
        }
    }

    fun requestUsbPermission(device: UsbDevice) {
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(actionUsbPermission).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun runPrintJob(
        errorPrefix: String,
        action: suspend () -> String
    ) {
        scope.launch {
            isPrinting = true
            try {
                lastStatus = action()
            } catch (e: Exception) {
                lastStatus = "$errorPrefix: ${e.message}"
            } finally {
                isPrinting = false
            }
        }
    }

    fun currentTargetStatus(
        usbStatus: String,
        ipStatus: String,
        sunmiStatus: String? = null
    ): String {
        return when (mode) {
            "USB" -> usbStatus
            "SUNMI" -> sunmiStatus ?: ipStatus
            else -> ipStatus
        }
    }

    fun executeOrderPrintTest(
        printType: OrderPrint.PrintType,
        callNumber: String,
        errorPrefix: String,
        orderProvider: () -> CashRegisterOrderPayload,
        statusMessage: () -> String
    ) {
        runPrintJob(errorPrefix) {
            withContext(Dispatchers.IO) {
                val orderPrint = OrderPrint()
                val testOrder = orderProvider()
                var callbackError: String? = null

                orderPrint.printOrder(
                    order = testOrder,
                    printType = printType,
                    callNumber = callNumber,
                    context = context,
                    callback = object : OrderPrint.PrintCallback {
                        override fun onSuccess() {}
                        override fun onError(error: String) {
                            callbackError = "$errorPrefix: $error"
                        }
                    }
                )

                callbackError ?: statusMessage()
            }
        }
    }

    fun doPrintUsb(device: UsbDevice) {
        runPrintJob("USB print error") {
            withContext(Dispatchers.IO) {
                if (!usbManager.hasPermission(device)) {
                    "No USB permission. Click 'Request Permission' first."
                } else {
                    val connection = usbManager.openDevice(device) ?: return@withContext "Failed to open USB device"
                    try {
                        val iface = findPrintableInterface(device) ?: return@withContext "No printable USB interface found"
                        val endpoint = findBulkOutEndpoint(iface) ?: return@withContext "No bulk OUT endpoint found"

                        if (!connection.claimInterface(iface, true)) {
                            return@withContext "Failed to claim USB interface"
                        }

                        val testContent = "=== TEST PRINT ===\nUSB Printer Test\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n\nThis is a test print from the Ordering Machine.\n\nIf you can read this, the USB printer is working correctly!\n\n====================\n\n"
                        val payload = testContent.toByteArray(charset("GBK"))

                        val sent = connection.bulkTransfer(endpoint, payload, payload.size, 5000)
                        if (sent > 0) {
                            "Printed via USB (${sent} bytes)"
                        } else {
                            "USB print failed (bulkTransfer=$sent)"
                        }
                    } finally {
                        connection.close()
                    }
                }
            }
        }
    }

    fun doPrintIp(ip: String, port: Int) {
        runPrintJob("IP print error") {
            withContext(Dispatchers.IO) {
                if (ip.isBlank()) return@withContext "IP is empty"

                val testContent = "=== TEST PRINT ===\nIP Printer Test\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n\nThis is a test print from the Ordering Machine.\n\nIf you can read this, the IP printer is working correctly!\n\n====================\n\n"
                val payload = testContent.toByteArray(charset("GBK"))

                Socket(ip, port).use { socket ->
                    socket.getOutputStream().use { os ->
                        os.write(payload)
                        os.flush()
                    }
                }
                "Printed via IP ($ip:$port)"
            }
        }
    }

    fun createTestOrder(): CashRegisterOrderPayload {
        return CashRegisterOrderPayload(
            orderId = "TEST-001",
            createdAtMillis = System.currentTimeMillis(),
            source = "OrderingMachine",
            deviceName = "Test Device",
            dineIn = true,
            paymentMethod = "Card",
            total = 15.50,
            items = listOf(
                CashRegisterOrderItemPayload(
                    menuItemId = "1",
                    nameEn = "Test",
                    nameZh = "测试",
                    nameNl = "Test",
                    quantity = 2,
                    unitPrice = 7.75,
                    customizations = mapOf(
                        "special_vegan" to "meat",
                        "special_sauce" to "hot_sauce",
                        "special_drink" to "drink1"
                    )
                )
            )
        )
    }

    fun doPrintOrderTest() {
        executeOrderPrintTest(
            printType = OrderPrint.PrintType.ORDER,
            callNumber = "A123",
            errorPrefix = "Order test print error",
            orderProvider = { createTestOrder() },
            statusMessage = {
                currentTargetStatus(
                    usbStatus = "Order test queued via USB",
                    ipStatus = "Order test queued via IP ($printerIp:${printerPortText})"
                )
            }
        )
    }

    fun doPrintOrderUnpaidTest() {
        executeOrderPrintTest(
            printType = OrderPrint.PrintType.ORDER_UNPAID,
            callNumber = "B456",
            errorPrefix = "Unpaid order test print error",
            orderProvider = {
                createTestOrder().copy(
                    paymentMethod = "Cash",
                    total = 15.50
                )
            },
            statusMessage = {
                currentTargetStatus(
                    usbStatus = "Unpaid order test queued via USB",
                    ipStatus = "Unpaid order test queued via IP ($printerIp:${printerPortText})"
                )
            }
        )
    }

    fun doPrintOrderUnpaidSunmiTest() {
        executeOrderPrintTest(
            printType = OrderPrint.PrintType.ORDER_UNPAID_SUNMI,
            callNumber = "B456",
            errorPrefix = "Unpaid Sunmi order test print error",
            orderProvider = {
                createTestOrder().copy(
                    paymentMethod = "Cash",
                    total = 15.50
                )
            },
            statusMessage = {
                currentTargetStatus(
                    usbStatus = "Unpaid Sunmi order test queued via USB",
                    ipStatus = "Unpaid Sunmi order test queued via IP ($printerIp:${printerPortText})",
                    sunmiStatus = "Unpaid Sunmi order test queued via Sunmi"
                )
            }
        )
    }

    fun doPrintReceiptTest() {
        executeOrderPrintTest(
            printType = OrderPrint.PrintType.RECEIPT,
            callNumber = "C789",
            errorPrefix = "Receipt test print error",
            orderProvider = { createTestOrder() },
            statusMessage = {
                currentTargetStatus(
                    usbStatus = "Receipt test queued via USB",
                    ipStatus = "Receipt test queued via IP ($printerIp:${printerPortText})"
                )
            }
        )
    }

    fun doPrintReceiptSunmiTest() {
        executeOrderPrintTest(
            printType = OrderPrint.PrintType.RECEIPT_SUNMI,
            callNumber = "C789",
            errorPrefix = "Receipt Sunmi test print error",
            orderProvider = { createTestOrder() },
            statusMessage = {
                currentTargetStatus(
                    usbStatus = "Receipt Sunmi test queued via USB",
                    ipStatus = "Receipt Sunmi test queued via IP ($printerIp:${printerPortText})",
                    sunmiStatus = "Receipt Sunmi test queued via Sunmi"
                )
            }
        )
    }

    fun doPrintSunmi() {
        runPrintJob("Sunmi print error") {
            if (!SunmiPrinterHelper.isServiceConnected()) {
                "Sunmi printer not connected. Click 'Connect Sunmi Printer' first."
            } else {
                val testContent = "=== TEST PRINT ===\nSunmi Printer Test\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())}\n\nThis is a test print from the Ordering Machine.\n\nIf you can read this, the Sunmi printer is working correctly!\n\n====================\n\n"
                SunmiPrinterHelper.printWithRawCommands(testContent).getOrThrow()
                "Printed via Sunmi printer successfully"
            }
        }
    }

    fun doPrintKitchenTest() {
        runPrintJob("Kitchen test print error") {
            withContext(Dispatchers.IO) {
                val orderPrint = OrderPrint()
                val testOrder = createTestOrder()
                val payloadBytes = orderPrint.generatePrintPayload(
                    order = testOrder,
                    printType = OrderPrint.PrintType.KITCHEN,
                    callNumber = "K101",
                    context = context
                )

                if (mode == "USB") {
                    selectedUsbDevice?.let { device ->
                        if (!usbManager.hasPermission(device)) {
                            "No USB permission for kitchen test. Click 'Request Permission' first."
                        } else {
                            val connection = usbManager.openDevice(device) ?: return@withContext "Failed to open USB device for kitchen test"
                            try {
                                val iface = findPrintableInterface(device) ?: return@withContext "No printable USB interface found for kitchen test"
                                val endpoint = findBulkOutEndpoint(iface) ?: return@withContext "No bulk OUT endpoint found for kitchen test"

                                if (!connection.claimInterface(iface, true)) {
                                    return@withContext "Failed to claim USB interface for kitchen test"
                                }

                                val sent = connection.bulkTransfer(endpoint, payloadBytes, payloadBytes.size, 5000)
                                if (sent > 0) {
                                    "Kitchen test printed via USB (${sent} bytes)"
                                } else {
                                    "Kitchen test print failed via USB (bulkTransfer=$sent)"
                                }
                            } finally {
                                connection.close()
                            }
                        }
                    } ?: "No USB device selected for kitchen test"
                } else {
                    if (printerIp.isBlank()) return@withContext "IP is empty for kitchen test"
                    val port = printerPortText.toIntOrNull() ?: return@withContext "Invalid port for kitchen test"

                    Socket(printerIp.trim(), port).use { socket ->
                        socket.getOutputStream().use { os ->
                            os.write(payloadBytes)
                            os.flush()
                        }
                    }
                    "Kitchen test printed via IP ($printerIp:$port)"
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DebugSectionCard(title = "Printer Mode") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LabeledRadioOption(
                    label = "Sunmi",
                    selected = mode == "SUNMI",
                    onSelect = { mode = "SUNMI" }
                )
                LabeledRadioOption(
                    label = "USB",
                    selected = mode == "USB",
                    onSelect = { mode = "USB" }
                )
                LabeledRadioOption(
                    label = "IP",
                    selected = mode == "IP",
                    onSelect = { mode = "IP" }
                )
            }

            if (mode == "SUNMI") {
                Button(
                    onClick = { connectSunmiPrinter() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect Sunmi Printer")
                }

                if (sunmiServiceConnected) {
                    Text(
                        text = "✓ Sunmi printer connected",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                    sunmiPrinterInfo?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                } else {
                    Text(
                        text = "Sunmi printer not connected",
                        color = Color.Gray
                    )
                }
            }

            if (mode == "IP") {
                OutlinedTextField(
                    value = printerIp,
                    onValueChange = { printerIp = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PRINTER IP") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = printerPortText,
                    onValueChange = { printerPortText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("PRINTER PORT") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Button(
                onClick = {
                    PrinterConfig.saveMode(context, mode)
                    if (mode == "IP") {
                        val port = printerPortText.toIntOrNull() ?: return@Button
                        PrinterConfig.saveIpConfig(context, printerIp.trim(), port)
                    }
                    lastStatus = "Saved"
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Printer Config")
            }

            lastStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (mode == "USB") {
            DebugSectionCard(title = null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "USB Devices",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = { refreshUsbDevices() }) {
                            Text("Refresh")
                        }
                    }

                    if (usbDevices.isNotEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            usbDevices.forEach { dev ->
                                val selected = selectedUsbDevice?.deviceId == dev.deviceId
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = if (selected) Color(0xFFE8F5E9) else Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = buildUsbDeviceTitle(dev),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "VID: ${dev.vendorId}  PID: ${dev.productId}  ID: ${dev.deviceId}",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color(0xFF6A7078)
                                                )
                                            }
                                            OutlinedButton(onClick = { selectedUsbDevice = dev }) {
                                                Text(if (selected) "Selected" else "Select")
                                            }
                                        }

                                        val hasPerm = usbManager.hasPermission(dev)
                                        val printerHint = if (isLikelyPrinter(dev)) "疑似打印机" else "未知设备"
                                        Text(
                                            text = "$printerHint · Permission: ${if (hasPerm) "Granted" else "Not granted"}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color(0xFF4B4F55)
                                        )

                                        if (!hasPerm) {
                                            OutlinedButton(
                                                onClick = { requestUsbPermission(dev) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Request Permission")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No USB devices found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }

                    selectedUsbDevice?.let { device ->
                        Spacer(modifier = Modifier.height(16.dp))
                        BusyActionButton(
                            idleText = "Print Test via USB",
                            busyText = "Printing...",
                            isBusy = isPrinting,
                            onClick = { doPrintUsb(device) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
            }
        } else if (mode == "IP") {
            DebugSectionCard(title = "IP Printer Test") {
                    BusyActionButton(
                        idleText = "Print Test via IP",
                        busyText = "Printing...",
                        isBusy = isPrinting,
                        onClick = {
                            val port = printerPortText.toIntOrNull() ?: return@BusyActionButton
                            doPrintIp(printerIp.trim(), port)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
            }
        } else if (mode == "SUNMI") {
            DebugSectionCard(title = "Sunmi Printer Test") {
                    BusyActionButton(
                        idleText = "Print Test via Sunmi",
                        busyText = "Printing...",
                        isBusy = isPrinting,
                        onClick = { doPrintSunmi() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sunmiServiceConnected
                    )
            }
        }

    }
}

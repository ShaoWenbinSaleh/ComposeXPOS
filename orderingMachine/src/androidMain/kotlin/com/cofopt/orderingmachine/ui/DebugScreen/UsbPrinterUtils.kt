package com.cofopt.orderingmachine.ui.DebugScreen

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

fun buildUsbDeviceTitle(device: UsbDevice): String {
    val name = device.productName?.takeIf { it.isNotBlank() }
        ?: device.manufacturerName?.takeIf { it.isNotBlank() }
        ?: device.deviceName
    return name
}

fun isLikelyPrinter(device: UsbDevice): Boolean {
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return true
    }
    return false
}

fun findPrintableInterface(device: UsbDevice): UsbInterface? {
    var fallback: UsbInterface? = null
    for (i in 0 until device.interfaceCount) {
        val iface = device.getInterface(i)
        if (iface.interfaceClass == UsbConstants.USB_CLASS_PRINTER) return iface
        if (fallback == null && findBulkOutEndpoint(iface) != null) fallback = iface
    }
    return fallback
}

fun findBulkOutEndpoint(iface: UsbInterface): UsbEndpoint? {
    for (e in 0 until iface.endpointCount) {
        val ep = iface.getEndpoint(e)
        if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_OUT) {
            return ep
        }
    }
    return null
}

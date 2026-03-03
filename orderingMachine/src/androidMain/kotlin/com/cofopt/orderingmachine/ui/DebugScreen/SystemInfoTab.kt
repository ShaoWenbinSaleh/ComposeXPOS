package com.cofopt.orderingmachine.ui.DebugScreen

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cofopt.orderingmachine.ui.common.components.DebugSectionCard
import java.net.Inet4Address
import java.net.NetworkInterface

@Composable
fun SystemInfoTab() {
    val context = LocalContext.current
    val appInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val localIp = resolveLocalIpv4Address() ?: "-"
    val versionName = appInfo.versionName
    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        appInfo.longVersionCode.toString()
    } else {
        @Suppress("DEPRECATION")
        appInfo.versionCode.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        DebugSectionCard(
            title = "System Information",
            itemSpacing = 10.dp
        ) {
                Text(text = "App Version: $versionName ($versionCode)")
                Text(text = "Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                Text(text = "Local IP: $localIp")
                Text(text = "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                Text(text = "Device Name: ${Build.DEVICE}")
                Text(text = "Product: ${Build.PRODUCT}")
                Text(text = "Hardware: ${Build.HARDWARE}")
                Text(text = "Tags: ${Build.TAGS}")
                Text(text = "Type: ${Build.TYPE}")
                Text(text = "User: ${Build.USER}")
                Text(text = "Host: ${Build.HOST}")
                Text(text = "Time: ${Build.TIME}")
                Text(text = "ID: ${Build.ID}")
                Text(text = "Display: ${Build.DISPLAY}")
                Text(text = "Fingerprint: ${Build.FINGERPRINT}")
                Text(text = "Bootloader: ${Build.BOOTLOADER}")
                Text(text = "Serial: ${Build.SERIAL}")
        }

        DebugSectionCard(
            title = "Application Information",
            itemSpacing = 10.dp
        ) {
                Text(text = "Package Name: ${context.packageName}")
                Text(text = "Target SDK: ${context.applicationInfo.targetSdkVersion}")
                Text(text = "Min SDK: ${context.applicationInfo.minSdkVersion}")
                Text(text = "Install Time: ${java.util.Date(appInfo.firstInstallTime)}")
                Text(text = "Update Time: ${java.util.Date(appInfo.lastUpdateTime)}")
        }
    }
}

private fun resolveLocalIpv4Address(): String? {
    return runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
        interfaces.toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .map { it.hostAddress.orEmpty() }
            .firstOrNull { ip ->
                ip.isNotBlank() &&
                    !ip.startsWith("127.") &&
                    (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))
            }
            ?: interfaces.toList()
                .asSequence()
                .flatMap { it.inetAddresses.toList().asSequence() }
                .filterIsInstance<Inet4Address>()
                .map { it.hostAddress.orEmpty() }
                .firstOrNull { ip -> ip.isNotBlank() && !ip.startsWith("127.") }
    }.getOrNull()
}

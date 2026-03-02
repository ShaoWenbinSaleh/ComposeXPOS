package com.cofopt.orderingmachine

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.cofopt.orderingmachine.cmp.OrderingMachineApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        OrderingMachineApp()
    }
}

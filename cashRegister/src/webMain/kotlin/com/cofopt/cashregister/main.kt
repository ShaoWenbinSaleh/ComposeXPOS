package com.cofopt.cashregister

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.cofopt.cashregister.cmp.CashRegisterApp

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        CashRegisterApp()
    }
}

package com.cofopt.orderingmachine.viewmodel

import com.cofopt.orderingmachine.Screen

internal fun shouldResetOrderForScreen(screen: Screen): Boolean {
    return screen == Screen.MODE_SELECTION
}

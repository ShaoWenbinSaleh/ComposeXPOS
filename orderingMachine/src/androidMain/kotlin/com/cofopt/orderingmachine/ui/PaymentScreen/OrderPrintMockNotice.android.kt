package com.cofopt.orderingmachine.ui.PaymentScreen

import com.cofopt.orderingmachine.network.OrderingPlatformContext
import com.cofopt.shared.mock.MockFeatureNotice

actual fun showOrderPrintMockNotice(context: OrderingPlatformContext, feature: String) {
    MockFeatureNotice.showPrint(context, feature)
}

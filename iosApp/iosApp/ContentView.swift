import UIKit
import SwiftUI

#if CALLING_MACHINE
import CallingMachineApp
#elseif CASH_REGISTER
import CashRegisterApp
#elseif ORDERING_MACHINE
import OrderingMachineApp
#else
#error("Missing iOS app flavor. Use Calling/Cash/Ordering scheme or set CALLING_MACHINE/CASH_REGISTER/ORDERING_MACHINE.")
#endif

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

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
        let composeController = MainViewControllerKt.MainViewController()
#if ORDERING_MACHINE
        return PortraitLockedContainerController(content: composeController)
#else
        return composeController
#endif
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

#if ORDERING_MACHINE
private final class PortraitLockedContainerController: UIViewController {
    private let content: UIViewController

    init(content: UIViewController) {
        self.content = content
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(content)
        view.addSubview(content.view)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        content.didMove(toParent: self)
    }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { .portrait }

    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation { .portrait }

    override var shouldAutorotate: Bool { false }
}
#endif

struct ContentView: View {
    var body: some View {
        ComposeView()
    }
}

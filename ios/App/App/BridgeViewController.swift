import UIKit
import Capacitor
import WebKit

@objc(BridgeViewController)
class BridgeViewController: CAPBridgeViewController {
    override func capacitorDidLoad() {
        super.capacitorDidLoad()
        bridge?.registerPluginInstance(OdooWebViewPlugin())
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureTransparentShellWebView()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        configureTransparentShellWebView()
        (bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin)?.relayoutWebView()
    }

    override func webView(with frame: CGRect, configuration: WKWebViewConfiguration) -> WKWebView {
        PassThroughWebView(frame: frame, configuration: configuration)
    }

    private func configureTransparentShellWebView() {
        guard let wv = webView else { return }
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isOpaque = false
        wv.scrollView.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
    }
}

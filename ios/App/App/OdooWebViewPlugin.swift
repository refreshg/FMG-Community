import Foundation
import Capacitor
import WebKit

@objc(OdooWebViewPlugin)
public class OdooWebViewPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "OdooWebViewPlugin"
    public let jsName = "OdooWebView"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "initialize", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "setVisible", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "loadUrl", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "reload", returnType: CAPPluginReturnPromise)
    ]

    private var odooWebView: WKWebView?
    private var container: UIView?
    private var headerPx: CGFloat = 70
    private var tabbarPx: CGFloat = 134

    private func parentView() -> UIView? {
        bridge?.viewController?.view
    }

    private func shellWebView() -> WKWebView? {
        bridge?.webView
    }

    func relayoutWebView() {
        DispatchQueue.main.async {
            self.layoutWebView()
        }
    }

    private func ensureWebView() {
        guard odooWebView == nil, let parent = parentView() else { return }

        let config = WKWebViewConfiguration()
        config.websiteDataStore = .default()
        config.preferences.javaScriptEnabled = true
        config.preferences.javaScriptCanOpenWindowsAutomatically = true
        config.allowsInlineMediaPlayback = true
        if #available(iOS 14.0, *) {
            config.defaultWebpagePreferences.allowsContentJavaScript = true
        }

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.isOpaque = true
        webView.backgroundColor = UIColor(red: 0.96, green: 0.97, blue: 0.98, alpha: 1)
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.scrollView.bounces = true
        webView.navigationDelegate = self
        webView.uiDelegate = self
        webView.allowsBackForwardNavigationGestures = true

        let containerView = UIView(frame: .zero)
        containerView.backgroundColor = .clear
        containerView.isHidden = true
        containerView.clipsToBounds = true
        containerView.addSubview(webView)

        if let shell = shellWebView() {
            parent.insertSubview(containerView, belowSubview: shell)
        } else {
            parent.insertSubview(containerView, at: 0)
        }

        odooWebView = webView
        container = containerView
        layoutWebView()
    }

    private func layoutWebView() {
        guard let parent = parentView(), let container = container, let webView = odooWebView else { return }

        parent.layoutIfNeeded()
        let safeTop = parent.safeAreaInsets.top
        let safeBottom = parent.safeAreaInsets.bottom
        let top = headerPx + safeTop
        let bottom = tabbarPx + safeBottom
        let width = parent.bounds.width
        let height = max(0, parent.bounds.height - top - bottom)

        container.frame = CGRect(x: 0, y: top, width: width, height: height)
        webView.frame = container.bounds
    }

    @objc func initialize(_ call: CAPPluginCall) {
        headerPx = CGFloat(call.getInt("headerHeightPx") ?? 70)
        tabbarPx = CGFloat(call.getInt("tabbarHeightPx") ?? 134)
        DispatchQueue.main.async {
            self.ensureWebView()
            self.layoutWebView()
            call.resolve()
        }
    }

    @objc func setVisible(_ call: CAPPluginCall) {
        let visible = call.getBool("visible") ?? false
        DispatchQueue.main.async {
            self.ensureWebView()
            self.layoutWebView()
            self.container?.isHidden = !visible
            call.resolve()
        }
    }

    @objc func loadUrl(_ call: CAPPluginCall) {
        guard let urlString = call.getString("url"), let url = URL(string: urlString) else {
            call.reject("Missing url")
            return
        }
        DispatchQueue.main.async {
            self.ensureWebView()
            self.layoutWebView()
            self.container?.isHidden = false
            self.odooWebView?.load(URLRequest(url: url, cachePolicy: .useProtocolCachePolicy, timeoutInterval: 60))
            call.resolve()
        }
    }

    @objc func reload(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.odooWebView?.reload()
            call.resolve()
        }
    }

    private func notifyPageError(_ url: String, message: String, code: Int) {
        notifyListeners("pageError", data: [
            "url": url,
            "message": message,
            "code": code
        ])
    }
}

extension OdooWebViewPlugin: WKNavigationDelegate {
    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        decisionHandler(.allow)
    }

    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        guard let url = webView.url?.absoluteString else { return }
        notifyListeners("pageLoaded", data: ["url": url])
        if url.contains("/my/") {
            notifyListeners("portalSession", data: ["url": url])
        }
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        let url = webView.url?.absoluteString ?? ""
        let nsError = error as NSError
        notifyPageError(url, message: error.localizedDescription, code: nsError.code)
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let url = webView.url?.absoluteString ?? ""
        let nsError = error as NSError
        notifyPageError(url, message: error.localizedDescription, code: nsError.code)
    }
}

extension OdooWebViewPlugin: WKUIDelegate {
    public func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
            webView.load(URLRequest(url: url))
        }
        return nil
    }
}

import UIKit
import Capacitor
import WebKit

@objc(BridgeViewController)
class BridgeViewController: CAPBridgeViewController {
    private var tabTouchLayer: NativeTabBarTouchLayer?
    private var headerTouchLayer: NativeHeaderTouchLayer?
    private var loginTouchLayer: NativeLoginTouchLayer?
    private var onboardingTouchLayer: NativeOnboardingTouchLayer?
    private var nativeMenuOverlay: NativeMenuOverlayView?
    private var nativeLegalOverlay: NativeLegalOverlayView?
    private var loginTouchEnabled = false
    private var onboardingTouchEnabled = false
    private var legalOverlayVisible = false
    private var nativeOnboardingIndex = 0
    private let tabBarTotalHeight: CGFloat = 134

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
        layoutTabTouchLayer()
        layoutHeaderTouchLayer()
        layoutLoginTouchLayer()
        layoutOnboardingTouchLayer()
        relayoutNativeMenuOverlay()
        relayoutNativeLegalOverlay()
        applyOverlayZOrder()
    }

    func setOnboardingTouchLayerEnabled(_ enabled: Bool) {
        onboardingTouchEnabled = enabled
        if enabled { nativeOnboardingIndex = 0 }
        layoutOnboardingTouchLayer()
        onboardingTouchLayer?.isHidden = !enabled
        onboardingTouchLayer?.isUserInteractionEnabled = enabled
        applyOverlayZOrder()
        if enabled, let layer = onboardingTouchLayer {
            view?.bringSubviewToFront(layer)
        }
    }

    func showNativeLegal(which: String) {
        relayoutNativeLegalOverlay()
        nativeLegalOverlay?.show(which: which)
        legalOverlayVisible = true
        applyOverlayZOrder()
    }

    func hideNativeLegal() {
        nativeLegalOverlay?.hide()
        legalOverlayVisible = false
        applyOverlayZOrder()
    }

    private func layoutOnboardingTouchLayer() {
        guard view.bounds.width > 0, view.bounds.height > 0 else { return }

        if onboardingTouchLayer == nil {
            let layer = NativeOnboardingTouchLayer(frame: view.bounds)
            layer.onSkipTap = { [weak self] in
                self?.handleNativeOnboardingAction("skip")
            }
            layer.onNextTap = { [weak self] in
                self?.handleNativeOnboardingAction("next")
            }
            view.addSubview(layer)
            onboardingTouchLayer = layer
        }

        onboardingTouchLayer?.frame = view.bounds
        onboardingTouchLayer?.layoutButtons(
            safeTop: view.safeAreaInsets.top,
            safeBottom: view.safeAreaInsets.bottom
        )
        if onboardingTouchEnabled, let layer = onboardingTouchLayer {
            view.bringSubviewToFront(layer)
        }
    }

    private func handleNativeOnboardingAction(_ action: String) {
        if action == "next" {
            nativeOnboardingIndex += 1
            if nativeOnboardingIndex >= 3 {
                nativeOnboardingIndex = 0
                runOnboardingJs("""
                (function(){
                  if(window.__fmgOnboarding&&window.__fmgOnboarding.skip) return window.__fmgOnboarding.skip();
                  if(typeof completeOnboarding==='function') return completeOnboarding();
                  document.getElementById('ob-skip')?.click();
                })();
                """)
                return
            }
            runOnboardingJs("""
            (function(){
              if(window.__fmgOnboarding&&window.__fmgOnboarding.goTo) return window.__fmgOnboarding.goTo(\(nativeOnboardingIndex));
              if(typeof onboardingNext==='function') return onboardingNext();
              document.getElementById('ob-next')?.click();
            })();
            """)
            return
        }

        if action == "skip" {
            nativeOnboardingIndex = 0
            runOnboardingJs("""
            (function(){
              if(window.__fmgOnboarding&&window.__fmgOnboarding.skip) return window.__fmgOnboarding.skip();
              if(typeof completeOnboarding==='function') return completeOnboarding();
              document.getElementById('ob-skip')?.click();
            })();
            """)
        }
    }

    private func runOnboardingJs(_ script: String) {
        bridge?.webView?.evaluateJavaScript(script, completionHandler: { _, error in
            if let error = error {
                NSLog("[FMG] onboarding JS error: \(error.localizedDescription)")
            }
        })
    }

    private func layoutHeaderTouchLayer() {
        guard view.bounds.width > 0, view.bounds.height > 0 else { return }

        if headerTouchLayer == nil {
            let layer = NativeHeaderTouchLayer(frame: view.bounds)
            layer.onPhoneTap = { [weak self] in
                self?.handleNativePhoneTap()
            }
            view.addSubview(layer)
            headerTouchLayer = layer
        }

        headerTouchLayer?.frame = view.bounds
        headerTouchLayer?.layoutPhoneButton(safeTop: view.safeAreaInsets.top)
        if headerTouchLayer?.isHidden == false {
            view.bringSubviewToFront(headerTouchLayer!)
        }
    }

    func dialSupportPhone() {
        handleNativePhoneTap()
    }

    private func handleNativePhoneTap() {
        if let url = URL(string: "tel://0322470600") {
            UIApplication.shared.open(url, options: [:]) { ok in
                if !ok, let fallback = URL(string: "tel:0322470600") {
                    UIApplication.shared.open(fallback, options: [:], completionHandler: nil)
                }
            }
        }
    }

    private func relayoutNativeLegalOverlay() {
        guard let parent = view, parent.bounds.width > 0 else { return }
        guard let plugin = bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin else { return }
        let frame = plugin.contentAreaFrame(in: parent)
        guard frame.height > 0 else { return }

        if nativeLegalOverlay == nil {
            let legal = NativeLegalOverlayView(frame: frame)
            legal.onClose = { [weak self] in
                self?.legalOverlayVisible = false
                self?.runMenuJs("window.__fmgLegal && window.__fmgLegal.closed();")
                self?.applyOverlayZOrder()
            }
            nativeLegalOverlay = legal
            view.addSubview(legal)
        }
        nativeLegalOverlay?.frame = frame
    }

    func setLoginTouchLayerEnabled(_ enabled: Bool) {
        loginTouchEnabled = enabled
        layoutLoginTouchLayer()
        loginTouchLayer?.isHidden = !enabled
        loginTouchLayer?.isUserInteractionEnabled = enabled
        applyOverlayZOrder()
    }

    private func layoutLoginTouchLayer() {
        guard view.bounds.width > 0, view.bounds.height > 0 else { return }

        if loginTouchLayer == nil {
            let layer = NativeLoginTouchLayer(frame: view.bounds)
            layer.onLoginTap = { [weak self] in
                self?.handleNativeLoginTap()
            }
            view.addSubview(layer)
            loginTouchLayer = layer
        }

        loginTouchLayer?.frame = view.bounds
        loginTouchLayer?.layoutLoginButton(
            safeTop: view.safeAreaInsets.top,
            safeBottom: view.safeAreaInsets.bottom
        )
    }

    private func handleNativeLoginTap() {
        let script = """
        (function(){
          if (window.__fmgLogin && window.__fmgLogin.submit) return window.__fmgLogin.submit();
          if (typeof window.submitLogin === 'function') return window.submitLogin();
          var btn = document.getElementById('login-btn');
          if (btn) btn.click();
        })();
        """
        bridge?.webView?.evaluateJavaScript(script, completionHandler: { _, error in
            if let error = error {
                NSLog("[FMG] login JS error: \(error.localizedDescription)")
            }
        })
    }

    /// Login / onboarding / legal / main chrome z-order.
    private func applyOverlayZOrder() {
        guard let parent = view else { return }

        if loginTouchEnabled {
            if let odoo = (bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin)?.odooContainerView() {
                parent.sendSubviewToBack(odoo)
            }
            if let shell = webView { parent.bringSubviewToFront(shell) }
            if let loginTouchLayer = loginTouchLayer { parent.bringSubviewToFront(loginTouchLayer) }
            return
        }

        if onboardingTouchEnabled {
            if let odoo = (bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin)?.odooContainerView() {
                parent.sendSubviewToBack(odoo)
            }
            if let shell = webView { parent.bringSubviewToFront(shell) }
            if let onboardingTouchLayer = onboardingTouchLayer { parent.bringSubviewToFront(onboardingTouchLayer) }
            return
        }

        if legalOverlayVisible {
            applyChromeZOrder()
            if let legal = nativeLegalOverlay { parent.bringSubviewToFront(legal) }
            if let tabTouchLayer = tabTouchLayer, !tabTouchLayer.isHidden {
                parent.bringSubviewToFront(tabTouchLayer)
            }
            if let headerTouchLayer = headerTouchLayer, !headerTouchLayer.isHidden {
                parent.bringSubviewToFront(headerTouchLayer)
            }
            return
        }

        applyChromeZOrder()
    }

    func relayoutNativeMenuOverlay() {
        guard let parent = view, parent.bounds.width > 0 else { return }
        guard let plugin = bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin else { return }
        let frame = plugin.menuOverlayFrame(in: parent)
        guard frame.height > 0 else { return }

        if nativeMenuOverlay == nil {
            let menu = NativeMenuOverlayView(frame: frame)
            menu.onAction = { [weak self] action in
                self?.handleNativeMenuAction(action)
            }
            nativeMenuOverlay = menu
            if let odooContainer = plugin.odooContainerView() {
                parent.insertSubview(menu, aboveSubview: odooContainer)
            } else {
                parent.addSubview(menu)
            }
        }

        nativeMenuOverlay?.frame = frame
    }

    /// Z-order: shell (chrome) → Odoo content → native menu → tab hit targets.
    func applyChromeZOrder() {
        guard let parent = view, !loginTouchEnabled, !onboardingTouchEnabled else { return }
        if let shell = webView {
            parent.bringSubviewToFront(shell)
        }
        if let shell = webView,
           let odooContainer = (bridge?.plugin(withName: "OdooWebView") as? OdooWebViewPlugin)?.odooContainerView() {
            parent.insertSubview(odooContainer, aboveSubview: shell)
        }
        if let menu = nativeMenuOverlay, menu.isPresented {
            parent.bringSubviewToFront(menu)
            if let headerTouchLayer = headerTouchLayer, !headerTouchLayer.isHidden {
                parent.bringSubviewToFront(headerTouchLayer)
            }
            return
        }
        if let tabTouchLayer = tabTouchLayer, !tabTouchLayer.isHidden {
            parent.bringSubviewToFront(tabTouchLayer)
        }
        if let headerTouchLayer = headerTouchLayer, !headerTouchLayer.isHidden {
            parent.bringSubviewToFront(headerTouchLayer)
        }
    }

    func showNativeMenu(serverUrl: String) {
        relayoutNativeMenuOverlay()
        nativeMenuOverlay?.isUserInteractionEnabled = true
        nativeMenuOverlay?.setServerUrl(serverUrl)
        nativeMenuOverlay?.present(animated: true)
        if let menu = nativeMenuOverlay, let parent = view {
            parent.bringSubviewToFront(menu)
            if let headerTouchLayer = headerTouchLayer, !headerTouchLayer.isHidden {
                parent.bringSubviewToFront(headerTouchLayer)
            }
        }
    }

    func hideNativeMenu(animated: Bool) {
        nativeMenuOverlay?.dismiss(animated: animated)
        nativeMenuOverlay?.isUserInteractionEnabled = false
        applyChromeZOrder()
    }

    private func handleNativeMenuAction(_ action: String) {
        if action == "closed" {
            runMenuJs("window.__fmgMenuAction && window.__fmgMenuAction('closed');")
            return
        }
        if action.hasPrefix("server-save:") {
            let url = String(action.dropFirst("server-save:".count))
            let escaped = url.replacingOccurrences(of: "\\", with: "\\\\")
                .replacingOccurrences(of: "'", with: "\\'")
            runMenuJs("window.__fmgMenuAction && window.__fmgMenuAction('server-save','\(escaped)');")
            return
        }
        runMenuJs("window.__fmgMenuAction && window.__fmgMenuAction('\(action)');")
    }

    private func runMenuJs(_ script: String) {
        bridge?.webView?.evaluateJavaScript(script, completionHandler: { _, error in
            if let error = error {
                NSLog("[FMG] menu JS error: \(error.localizedDescription)")
            }
        })
    }

    override func webView(with frame: CGRect, configuration: WKWebViewConfiguration) -> WKWebView {
        PassThroughWebView(frame: frame, configuration: configuration)
    }

    func setTabTouchLayerEnabled(_ enabled: Bool) {
        tabTouchLayer?.isHidden = !enabled
        tabTouchLayer?.isUserInteractionEnabled = enabled
        headerTouchLayer?.isHidden = !enabled
        headerTouchLayer?.isUserInteractionEnabled = enabled
        if enabled {
            layoutHeaderTouchLayer()
            applyChromeZOrder()
        }
    }

    private func configureTransparentShellWebView() {
        guard let wv = webView else { return }
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isOpaque = false
        wv.scrollView.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        if #available(iOS 15.0, *) {
            wv.underPageBackgroundColor = .clear
        }
    }

    private func layoutTabTouchLayer() {
        guard view.bounds.width > 0, view.bounds.height > 0 else { return }

        let safeBottom = view.safeAreaInsets.bottom
        let layerHeight = tabBarTotalHeight + safeBottom
        let frame = CGRect(
            x: 0,
            y: view.bounds.height - layerHeight,
            width: view.bounds.width,
            height: layerHeight
        )

        if tabTouchLayer == nil {
            let layer = NativeTabBarTouchLayer(frame: frame)
            layer.isHidden = true
            layer.isUserInteractionEnabled = false
            layer.onNavTap = { [weak self] key in
                self?.handleNativeNavTap(key)
            }
            view.addSubview(layer)
            tabTouchLayer = layer
        }

        tabTouchLayer?.frame = frame
        tabTouchLayer?.layoutNavButtons(safeBottom: safeBottom)
        if tabTouchLayer?.isHidden == false {
            view.bringSubviewToFront(tabTouchLayer!)
        }
    }

    private func handleNativeNavTap(_ key: String) {
        let script: String
        if key == "menu" {
            script = "window.__fmgNav && window.__fmgNav.menu();"
        } else {
            script = "window.__fmgNav && window.__fmgNav.tab('\(key)');"
        }
        bridge?.webView?.evaluateJavaScript(script, completionHandler: { _, error in
            if let error = error {
                NSLog("[FMG] native nav JS error: \(error.localizedDescription)")
            }
        })
    }
}

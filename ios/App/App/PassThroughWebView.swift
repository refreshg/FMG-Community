import WebKit

/// Transparent Capacitor shell webview: pass touches through non-interactive areas to native views below.
class PassThroughWebView: WKWebView {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        if hit === self || hit === scrollView {
            return nil
        }
        return hit
    }
}

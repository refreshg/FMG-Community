import UIKit

/// UIKit tap target for the login screen — WKWebView often misses button taps on iOS.
final class NativeLoginTouchLayer: UIView {
    var onLoginTap: (() -> Void)?

    private let loginButton = UIButton(type: .custom)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isHidden = true
        isUserInteractionEnabled = false

        loginButton.backgroundColor = UIColor.white.withAlphaComponent(0.001)
        loginButton.accessibilityLabel = "შესვლა"
        loginButton.addTarget(self, action: #selector(loginTapped), for: .touchUpInside)
        addSubview(loginButton)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if isHidden || !isUserInteractionEnabled || alpha < 0.01 {
            return nil
        }
        let hit = super.hitTest(point, with: event)
        if hit === self { return nil }
        return hit
    }

    func layoutLoginButton(safeTop: CGFloat, safeBottom: CGFloat) {
        let w = bounds.width
        let h = bounds.height
        guard w > 0, h > 0 else { return }

        // Match www login-scroll + field + .btn-primary geometry
        let padTop: CGFloat = 60 + safeTop
        let logoBlock: CGFloat = 46
        let titleBlock: CGFloat = 30
        let subtitleBlock: CGFloat = 62
        let fieldBlock: CGFloat = 78
        let btnTop = padTop + logoBlock + titleBlock + subtitleBlock + fieldBlock
        let btnWidth = min(400, w - 48)
        let btnHeight: CGFloat = 52
        let btnX = (w - btnWidth) / 2

        // Generous hit slop — layout varies slightly by device / safe area
        loginButton.frame = CGRect(
            x: btnX - 16,
            y: btnTop - 20,
            width: btnWidth + 32,
            height: btnHeight + 40
        )
        _ = safeBottom
    }

    @objc private func loginTapped() {
        onLoginTap?()
    }
}

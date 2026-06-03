import UIKit

/// Transparent UIKit hit targets over the HTML bottom nav — WKWebView often misses taps on SVG-in-button.
final class NativeTabBarTouchLayer: UIView {
    var onNavTap: ((String) -> Void)?

    private var navButtons: [String: UIButton] = [:]
    private let navKeys = ["home", "property", "service", "finance", "community", "menu", "profile"]

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isUserInteractionEnabled = true
        for key in navKeys {
            let btn = UIButton(type: .custom)
            btn.backgroundColor = UIColor.white.withAlphaComponent(0.001)
            btn.accessibilityIdentifier = key
            btn.addTarget(self, action: #selector(navTapped(_:)), for: .touchUpInside)
            addSubview(btn)
            navButtons[key] = btn
        }
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        if hit === self { return nil }
        return hit
    }

    @objc private func navTapped(_ sender: UIButton) {
        guard let key = sender.accessibilityIdentifier else { return }
        onNavTap?(key)
    }

    func layoutNavButtons(safeBottom: CGFloat) {
        let w = bounds.width
        let h = bounds.height
        guard w > 0, h > 0 else { return }

        // Match www/assets/style.css + Android dimens.xml
        let barBottomMargin: CGFloat = 12
        let barHeight: CGFloat = 108
        let barHPadding: CGFloat = 7
        let fabGap: CGFloat = 28
        let fabSize: CGFloat = 70
        let fabTopOffset: CGFloat = 31

        let barTop = h - safeBottom - barBottomMargin - barHeight
        let barInnerW = w - barHPadding * 2
        let sideContainerW = barInnerW / 2
        let leftTabW = (sideContainerW - fabGap) / 3
        let rightTabW = (sideContainerW - fabGap) / 3
        let leftX = barHPadding
        let rightX = barHPadding + sideContainerW + fabGap
        let tabHeight = barHeight

        let leftKeys = ["home", "property", "service"]
        for (i, key) in leftKeys.enumerated() {
            navButtons[key]?.frame = CGRect(
                x: leftX + CGFloat(i) * leftTabW,
                y: barTop,
                width: leftTabW,
                height: tabHeight
            )
        }

        let rightKeys = ["finance", "community", "menu"]
        for (i, key) in rightKeys.enumerated() {
            navButtons[key]?.frame = CGRect(
                x: rightX + CGFloat(i) * rightTabW,
                y: barTop,
                width: rightTabW,
                height: tabHeight
            )
        }

        navButtons["profile"]?.frame = CGRect(
            x: w / 2 - fabSize / 2,
            y: max(0, barTop - fabTopOffset),
            width: fabSize,
            height: fabSize + 8
        )
    }
}

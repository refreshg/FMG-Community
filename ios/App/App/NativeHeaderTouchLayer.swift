import UIKit

/// UIKit tap target for header phone button (top-right).
final class NativeHeaderTouchLayer: UIView {
    var onPhoneTap: (() -> Void)?

    private let phoneButton = UIButton(type: .custom)
    private let headerContentHeight: CGFloat = 70

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isHidden = true
        isUserInteractionEnabled = false

        phoneButton.backgroundColor = UIColor.white.withAlphaComponent(0.001)
        phoneButton.accessibilityLabel = "დარეკვა"
        phoneButton.addTarget(self, action: #selector(phoneTapped), for: .touchUpInside)
        addSubview(phoneButton)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if isHidden || !isUserInteractionEnabled { return nil }
        let hit = super.hitTest(point, with: event)
        if hit === self { return nil }
        return hit
    }

    func layoutPhoneButton(safeTop: CGFloat) {
        let w = bounds.width
        guard w > 0 else { return }

        // Match .app-topbar: safe-top + 16px padding, phone btn 36×36 centered in 70px row
        let hitW: CGFloat = 68
        let hitH: CGFloat = 56
        let btnX = w - 20 - 36
        let btnY = safeTop + 17

        phoneButton.frame = CGRect(
            x: max(0, btnX - 16),
            y: max(0, btnY - 10),
            width: hitW,
            height: hitH
        )
    }

    @objc private func phoneTapped() {
        onPhoneTap?()
    }
}

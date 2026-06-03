import UIKit

/// UIKit tap targets for onboarding skip (top-right) and next (bottom).
final class NativeOnboardingTouchLayer: UIView {
    var onSkipTap: (() -> Void)?
    var onNextTap: (() -> Void)?

    private let skipButton = UIButton(type: .custom)
    private let nextButton = UIButton(type: .custom)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isHidden = true
        isUserInteractionEnabled = false

        skipButton.backgroundColor = UIColor.white.withAlphaComponent(0.001)
        skipButton.addTarget(self, action: #selector(skipTapped), for: .touchUpInside)
        addSubview(skipButton)

        nextButton.backgroundColor = UIColor.white.withAlphaComponent(0.001)
        nextButton.addTarget(self, action: #selector(nextTapped), for: .touchUpInside)
        addSubview(nextButton)
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

    func layoutButtons(safeTop: CGFloat, safeBottom: CGFloat) {
        let w = bounds.width
        let h = bounds.height
        guard w > 0, h > 0 else { return }

        skipButton.frame = CGRect(x: w - 150, y: safeTop + 4, width: 140, height: 48)

        // Match CSS: padding-bottom 32 + safe area; generous hit slop for ob-next
        let horizontalPad: CGFloat = 24
        let bottomPad: CGFloat = 32
        let btnHeight: CGFloat = 56
        let btnY = h - safeBottom - bottomPad - btnHeight - 12

        nextButton.frame = CGRect(
            x: horizontalPad - 8,
            y: max(safeTop + 80, btnY - 16),
            width: w - horizontalPad * 2 + 16,
            height: btnHeight + 32
        )
    }

    @objc private func skipTapped() {
        onSkipTap?()
    }

    @objc private func nextTapped() {
        onNextTap?()
    }
}

import UIKit

/// Native menu panel above Odoo WKWebView (HTML overlay is not visible over native WebView on iOS).
final class NativeMenuOverlayView: UIView {
    var onAction: ((String) -> Void)?

    private let backdrop = UIView()
    private let panel = UIView()
    private let menuScrollView = UIScrollView()
    private let stack = UIStackView()
    private let serverEditor = UIView()
    private let serverField = UITextField()
    private var panelBottomConstraint: NSLayoutConstraint?
    private var isOpen = false
    private var isAnimating = false

    private struct RowSpec {
        let action: String
        let emoji: String
        let title: String
        let logout: Bool
    }

    private let rows: [RowSpec] = [
        RowSpec(action: "profile", emoji: "👤", title: "პროფილი", logout: false),
        RowSpec(action: "news", emoji: "📰", title: "სიახლეები", logout: false),
        RowSpec(action: "offers", emoji: "🎁", title: "შეთავაზებები", logout: false),
        RowSpec(action: "how", emoji: "❓", title: "როგორ მუშაობს?", logout: false),
        RowSpec(action: "server", emoji: "🔗", title: "სერვისის ლინკი", logout: false),
        RowSpec(action: "privacy", emoji: "🔒", title: "კონფიდენციალურობა", logout: false),
        RowSpec(action: "terms", emoji: "📄", title: "წესები და პირობები", logout: false),
        RowSpec(action: "logout", emoji: "🚪", title: "გამოსვლა", logout: true),
    ]

    var isPresented: Bool { !isHidden }

    override init(frame: CGRect) {
        super.init(frame: frame)
        isHidden = true
        clipsToBounds = true
        setupUi()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        if isHidden || !isUserInteractionEnabled || alpha < 0.01 {
            return nil
        }
        return super.hitTest(point, with: event)
    }

    private func setupUi() {
        isUserInteractionEnabled = true
        backdrop.backgroundColor = UIColor.black.withAlphaComponent(0.3)
        backdrop.alpha = 0
        backdrop.translatesAutoresizingMaskIntoConstraints = false
        addSubview(backdrop)

        let backdropTap = UITapGestureRecognizer(target: self, action: #selector(backdropTapped))
        backdrop.addGestureRecognizer(backdropTap)

        panel.backgroundColor = UIColor(red: 0.96, green: 0.97, blue: 0.98, alpha: 1)
        panel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(panel)

        let title = UILabel()
        title.text = "მენიუ"
        title.font = .boldSystemFont(ofSize: 22)
        title.textColor = UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)
        title.translatesAutoresizingMaskIntoConstraints = false
        panel.addSubview(title)

        menuScrollView.translatesAutoresizingMaskIntoConstraints = false
        menuScrollView.isScrollEnabled = true
        menuScrollView.alwaysBounceVertical = true
        menuScrollView.delaysContentTouches = false
        menuScrollView.keyboardDismissMode = .onDrag
        panel.addSubview(menuScrollView)

        stack.axis = .vertical
        stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        menuScrollView.addSubview(stack)

        for spec in rows {
            stack.addArrangedSubview(makeRow(spec))
            if spec.action == "server" {
                setupServerEditor()
                stack.addArrangedSubview(serverEditor)
            }
        }

        menuScrollView.contentInset = UIEdgeInsets(top: 0, left: 0, bottom: 32, right: 0)
        menuScrollView.scrollIndicatorInsets = menuScrollView.contentInset

        NSLayoutConstraint.activate([
            backdrop.topAnchor.constraint(equalTo: topAnchor),
            backdrop.leadingAnchor.constraint(equalTo: leadingAnchor),
            backdrop.trailingAnchor.constraint(equalTo: trailingAnchor),
            backdrop.bottomAnchor.constraint(equalTo: bottomAnchor),

            panel.topAnchor.constraint(equalTo: topAnchor),
            panel.leadingAnchor.constraint(equalTo: leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: trailingAnchor),

            title.topAnchor.constraint(equalTo: panel.topAnchor, constant: 14),
            title.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 20),
            title.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -20),

            menuScrollView.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 10),
            menuScrollView.leadingAnchor.constraint(equalTo: panel.leadingAnchor),
            menuScrollView.trailingAnchor.constraint(equalTo: panel.trailingAnchor),
            menuScrollView.bottomAnchor.constraint(equalTo: panel.safeAreaLayoutGuide.bottomAnchor, constant: -8),

            stack.topAnchor.constraint(equalTo: menuScrollView.contentLayoutGuide.topAnchor),
            stack.leadingAnchor.constraint(equalTo: menuScrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: menuScrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: menuScrollView.contentLayoutGuide.bottomAnchor, constant: -12),
            stack.widthAnchor.constraint(equalTo: menuScrollView.frameLayoutGuide.widthAnchor, constant: -32),
        ])

        panelBottomConstraint = panel.bottomAnchor.constraint(equalTo: bottomAnchor)
        panelBottomConstraint?.isActive = true
    }

    private func setupServerEditor() {
        serverEditor.isHidden = true
        serverEditor.backgroundColor = .white
        serverEditor.layer.cornerRadius = 12
        serverEditor.layer.shadowColor = UIColor.black.cgColor
        serverEditor.layer.shadowOpacity = 0.06
        serverEditor.layer.shadowOffset = CGSize(width: 0, height: 1)
        serverEditor.layer.shadowRadius = 3
        serverEditor.translatesAutoresizingMaskIntoConstraints = false

        serverField.borderStyle = .roundedRect
        serverField.autocapitalizationType = .none
        serverField.autocorrectionType = .no
        serverField.keyboardType = .URL
        serverField.placeholder = "https://example.odoo.com"
        serverField.translatesAutoresizingMaskIntoConstraints = false

        let save = UIButton(type: .system)
        save.setTitle("შენახვა", for: .normal)
        save.setTitleColor(.white, for: .normal)
        save.backgroundColor = UIColor(red: 0.05, green: 0.58, blue: 0.54, alpha: 1)
        save.layer.cornerRadius = 8
        save.contentEdgeInsets = UIEdgeInsets(top: 10, left: 16, bottom: 10, right: 16)
        save.addTarget(self, action: #selector(serverSaveTapped), for: .touchUpInside)
        save.translatesAutoresizingMaskIntoConstraints = false

        serverEditor.addSubview(serverField)
        serverEditor.addSubview(save)

        NSLayoutConstraint.activate([
            serverEditor.heightAnchor.constraint(greaterThanOrEqualToConstant: 100),
            serverField.topAnchor.constraint(equalTo: serverEditor.topAnchor, constant: 14),
            serverField.leadingAnchor.constraint(equalTo: serverEditor.leadingAnchor, constant: 14),
            serverField.trailingAnchor.constraint(equalTo: serverEditor.trailingAnchor, constant: -14),
            save.topAnchor.constraint(equalTo: serverField.bottomAnchor, constant: 8),
            save.leadingAnchor.constraint(equalTo: serverEditor.leadingAnchor, constant: 14),
            save.bottomAnchor.constraint(equalTo: serverEditor.bottomAnchor, constant: -14),
        ])
    }

    private func makeRow(_ spec: RowSpec) -> UIView {
        let card = UIButton(type: .custom)
        card.backgroundColor = .white
        card.layer.cornerRadius = 12
        card.layer.shadowColor = UIColor.black.cgColor
        card.layer.shadowOpacity = 0.06
        card.layer.shadowOffset = CGSize(width: 0, height: 1)
        card.layer.shadowRadius = 3
        card.accessibilityIdentifier = spec.action
        card.addTarget(self, action: #selector(rowTapped(_:)), for: .touchUpInside)
        card.translatesAutoresizingMaskIntoConstraints = false
        card.heightAnchor.constraint(greaterThanOrEqualToConstant: 56).isActive = true

        let badge = UILabel()
        badge.text = spec.emoji
        badge.font = .systemFont(ofSize: 16)
        badge.textAlignment = .center
        badge.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        badge.layer.cornerRadius = 8
        badge.clipsToBounds = true
        badge.translatesAutoresizingMaskIntoConstraints = false
        badge.isUserInteractionEnabled = false

        let label = UILabel()
        label.text = spec.title
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.textColor = spec.logout
            ? UIColor(red: 0.9, green: 0.22, blue: 0.27, alpha: 1)
            : UIColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 1)
        label.translatesAutoresizingMaskIntoConstraints = false
        label.isUserInteractionEnabled = false

        let chevron = UILabel()
        chevron.text = "›"
        chevron.font = .systemFont(ofSize: 18)
        chevron.textColor = UIColor(red: 0.53, green: 0.53, blue: 0.53, alpha: 1)
        chevron.translatesAutoresizingMaskIntoConstraints = false
        chevron.isUserInteractionEnabled = false

        card.addSubview(badge)
        card.addSubview(label)
        card.addSubview(chevron)

        NSLayoutConstraint.activate([
            badge.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            badge.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            badge.widthAnchor.constraint(equalToConstant: 36),
            badge.heightAnchor.constraint(equalToConstant: 36),
            label.leadingAnchor.constraint(equalTo: badge.trailingAnchor, constant: 12),
            label.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            chevron.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            label.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -8),
        ])

        return card
    }

    func setServerUrl(_ url: String) {
        serverField.text = url
    }

    func present(animated: Bool = true) {
        guard !isOpen, !isAnimating else { return }
        isHidden = false
        serverEditor.isHidden = true
        layoutIfNeeded()

        let slideFrom = panel.bounds.height > 0 ? panel.bounds.height : bounds.height
        panel.transform = CGAffineTransform(translationX: 0, y: slideFrom)
        backdrop.alpha = 0

        isAnimating = true
        menuScrollView.setContentOffset(CGPoint(x: 0, y: -menuScrollView.contentInset.top), animated: false)
        let animations = {
            self.panel.transform = .identity
            self.backdrop.alpha = 1
        }
        let completion: (Bool) -> Void = { _ in
            self.isOpen = true
            self.isAnimating = false
        }
        if animated {
            UIView.animate(withDuration: 0.25, animations: animations, completion: completion)
        } else {
            animations()
            completion(true)
        }
    }

    func dismiss(animated: Bool = true) {
        guard isOpen || !isHidden else {
            isHidden = true
            isOpen = false
            return
        }
        if isAnimating { return }

        let slideTo = panel.bounds.height > 0 ? panel.bounds.height : bounds.height
        isAnimating = true
        let animations = {
            self.panel.transform = CGAffineTransform(translationX: 0, y: slideTo)
            self.backdrop.alpha = 0
        }
        let completion: (Bool) -> Void = { _ in
            self.isOpen = false
            self.isAnimating = false
            self.isHidden = true
            self.panel.transform = .identity
            self.onAction?("closed")
        }
        if animated {
            UIView.animate(withDuration: 0.25, animations: animations, completion: completion)
        } else {
            animations()
            completion(true)
        }
    }

    @objc private func backdropTapped() {
        dismiss(animated: true)
    }

    @objc private func rowTapped(_ sender: UIButton) {
        guard let action = sender.accessibilityIdentifier else { return }
        if action == "server" {
            serverEditor.isHidden.toggle()
            if !serverEditor.isHidden {
                serverField.becomeFirstResponder()
            }
            return
        }
        onAction?(action)
        dismiss(animated: true)
    }

    @objc private func serverSaveTapped() {
        onAction?("server-save:\(serverField.text ?? "")")
    }
}

import UIKit
import WebKit

/// Bundled legal HTML (www/legal/) — same as Android LegalDocuments + layout_legal_overlay.xml.
final class NativeLegalOverlayView: UIView {
    var onClose: (() -> Void)?

    private let header = UIView()
    private let closeButton = UIButton(type: .system)
    private let titleLabel = UILabel()
    private let webView = WKWebView(frame: .zero, configuration: WKWebViewConfiguration())
    private var isVisible = false

    override init(frame: CGRect) {
        super.init(frame: frame)
        isHidden = true
        backgroundColor = .white
        setupUi()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func setupUi() {
        header.translatesAutoresizingMaskIntoConstraints = false
        header.backgroundColor = .white
        addSubview(header)

        closeButton.setTitle("←", for: .normal)
        closeButton.titleLabel?.font = .systemFont(ofSize: 22)
        closeButton.setTitleColor(UIColor(red: 0.9, green: 0.22, blue: 0.27, alpha: 1), for: .normal)
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        header.addSubview(closeButton)

        titleLabel.font = .boldSystemFont(ofSize: 17)
        titleLabel.textColor = UIColor(red: 0.9, green: 0.22, blue: 0.27, alpha: 1)
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        header.addSubview(titleLabel)

        webView.translatesAutoresizingMaskIntoConstraints = false
        webView.isOpaque = true
        webView.backgroundColor = .white
        webView.scrollView.backgroundColor = .white
        addSubview(webView)

        NSLayoutConstraint.activate([
            header.topAnchor.constraint(equalTo: topAnchor),
            header.leadingAnchor.constraint(equalTo: leadingAnchor),
            header.trailingAnchor.constraint(equalTo: trailingAnchor),
            header.heightAnchor.constraint(equalToConstant: 56),

            closeButton.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 4),
            closeButton.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            closeButton.widthAnchor.constraint(equalToConstant: 48),
            closeButton.heightAnchor.constraint(equalToConstant: 48),

            titleLabel.leadingAnchor.constraint(equalTo: closeButton.trailingAnchor, constant: 4),
            titleLabel.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            titleLabel.centerYAnchor.constraint(equalTo: header.centerYAnchor),

            webView.topAnchor.constraint(equalTo: header.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: trailingAnchor),
            webView.bottomAnchor.constraint(equalTo: bottomAnchor),
        ])
    }

    func show(which: String) {
        let isTerms = which == "terms"
        titleLabel.text = isTerms ? "წესები და პირობები" : "კონფიდენციალურობის პოლიტიკა"
        let fileName = isTerms ? "terms" : "privacy"
        guard let url = bundledLegalUrl(fileName: fileName) else {
            NSLog("[FMG] legal file missing: \(fileName).html")
            return
        }
        let publicDir = Bundle.main.bundleURL.appendingPathComponent("public")
        webView.loadFileURL(url, allowingReadAccessTo: publicDir)
        isHidden = false
        isVisible = true
    }

    func hide() {
        isVisible = false
        isHidden = true
        webView.load(URLRequest(url: URL(string: "about:blank")!))
    }

    private func bundledLegalUrl(fileName: String) -> URL? {
        if let url = Bundle.main.url(forResource: fileName, withExtension: "html", subdirectory: "public/legal") {
            return url
        }
        return Bundle.main.bundleURL
            .appendingPathComponent("public/legal/\(fileName).html")
    }

    @objc private func closeTapped() {
        hide()
        onClose?()
    }
}

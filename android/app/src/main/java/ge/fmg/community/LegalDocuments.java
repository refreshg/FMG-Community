package ge.fmg.community;

/** Bundled legal HTML under assets/legal/ (copied from www/legal/ at build time). */
public final class LegalDocuments {
  private LegalDocuments() {}

  public static final String PRIVACY_HTML = "legal/privacy.html";
  public static final String TERMS_HTML = "legal/terms.html";

  public static String assetUrl(String relativePath) {
    return "file:///android_asset/" + relativePath;
  }
}

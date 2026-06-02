package ge.fmg.community;

/** App paths relative to server base — compose with {@link #url}. */
public final class NavUrls {
  private NavUrls() {}

  // Bottom navigation
  public static final String HOME = "/mtavari";
  public static final String PROPERTY = "/qoneba";
  public static final String SERVICE = "/servisi";
  public static final String PROFILE = "/profili";
  public static final String FINANCE = "/finansebi";
  public static final String COMMUNITY = "/community";
  public static final String MENU = "/menu";

  // Menu items
  public static final String NEWS = "/news";
  public static final String SUGGESTION = "/shetavazeba";
  public static final String HOW_IT_WORKS = "/how";

  public static String url(String base, String path) {
    if (base == null || path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("base and path required; path must start with /");
    }
    String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return b + path;
  }
}

package ge.fmg.community;

/** Odoo portal paths (English locale) relative to server base — compose with {@link #url}. */
public final class NavUrls {
  private NavUrls() {}

  public static final String HOME = "/en/my/home";
  public static final String PROPERTY = "/en/my/home/property";
  public static final String SERVICE = "/en/my/home/service";
  public static final String FINANCE = "/en/my/home/finance";
  public static final String COMMUNITY = "/en/my/home/community";

  public static final String PROFILE = "/en/my/home/personal/info";
  public static final String NOTIFICATIONS = "/en/my/home/notifications";
  public static final String SUGGESTION = "/en/my/home/suggestion";
  public static final String HOW_IT_WORKS = "/en/my/home/howtowork";

  public static String url(String base, String path) {
    if (base == null || path == null || !path.startsWith("/")) {
      throw new IllegalArgumentException("base and path required; path must start with /");
    }
    String b = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return b + path;
  }
}

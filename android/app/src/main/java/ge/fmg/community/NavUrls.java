package ge.fmg.community;

/** Odoo portal paths (English locale) relative to server base — compose with {@link #url}. */
public final class NavUrls {
  private NavUrls() {}

  /** Portal home (header logo). */
  public static final String HOME = "/en/my/home";

  /** Balance tab — English locale root. */
  public static final String BALANCE = "/en";
  public static final String TICKETS = "/en/my/home/tickets";
  public static final String CREATE_TICKET = "/en/my/home/tickets/create";
  public static final String REAL_ESTATE = "/en/my/home/real/estate";

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

  public static String home(String base) {
    return url(base, HOME);
  }

  public static String tickets(String base) {
    return url(base, TICKETS);
  }

  public static String createTicket(String base) {
    return url(base, CREATE_TICKET);
  }

  public static String realEstate(String base) {
    return url(base, REAL_ESTATE);
  }

  public static String profile(String base) {
    return url(base, PROFILE);
  }

  public static String notifications(String base) {
    return url(base, NOTIFICATIONS);
  }

  public static String suggestion(String base) {
    return url(base, SUGGESTION);
  }

  public static String howItWorks(String base) {
    return url(base, HOW_IT_WORKS);
  }
}

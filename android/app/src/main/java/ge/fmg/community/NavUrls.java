package ge.fmg.community;

/** Odoo portal paths (English locale) relative to server base. */
public final class NavUrls {
  private NavUrls() {}

  public static String home(String base) {
    return base + "/en/my/home";
  }

  public static String tickets(String base) {
    return base + "/en/my/home/tickets";
  }

  public static String createTicket(String base) {
    return base + "/en/my/home/tickets/create";
  }

  public static String realEstate(String base) {
    return base + "/en/my/home/real/estate";
  }

  public static String news(String base) {
    return base + "/en/my/home/news";
  }

  public static String offers(String base) {
    return base + "/en/my/home/offers";
  }

  public static String howItWorks(String base) {
    return base + "/en/my/home/how-it-works";
  }

  public static String chat(String base) {
    return base + "/en/my/home/chat";
  }
}

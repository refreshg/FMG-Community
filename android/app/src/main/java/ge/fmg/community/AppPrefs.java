package ge.fmg.community;

import android.content.Context;
import android.content.SharedPreferences;

/** Native storage (same keys as Capacitor Preferences). Never store passwords. */
public final class AppPrefs {
  private static final String NAME = "ge.fmg.community.prefs";

  public static final String KEY_SERVER = "odooServer";
  public static final String KEY_PUSH = "pushEnabled";
  public static final String KEY_BIO = "bioEnabled";
  /** Same key as @capacitor/preferences in the web shell. */
  public static final String KEY_ONBOARDING_SEEN = "onboardingSeen";

  private static final String DEFAULT_SERVER = "https://fmggeo-araa-19679928.dev.odoo.com";

  private final SharedPreferences prefs;

  public AppPrefs(Context context) {
    prefs = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
  }

  public String getServer() {
    return prefs.getString(KEY_SERVER, DEFAULT_SERVER);
  }

  public void setServer(String server) {
    prefs.edit().putString(KEY_SERVER, server).apply();
  }

  public boolean isPushEnabled() {
    return prefs.getBoolean(KEY_PUSH, false);
  }

  public void setPushEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_PUSH, enabled).apply();
  }

  public boolean isBioEnabled() {
    return prefs.getBoolean(KEY_BIO, false);
  }

  public void setBioEnabled(boolean enabled) {
    prefs.edit().putBoolean(KEY_BIO, enabled).apply();
  }

  public boolean isOnboardingSeen() {
    if (prefs.contains(KEY_ONBOARDING_SEEN)) {
      return prefs.getBoolean(KEY_ONBOARDING_SEEN, false);
    }
    String legacy = prefs.getString(KEY_ONBOARDING_SEEN, null);
    return "true".equalsIgnoreCase(legacy);
  }

  public void setOnboardingSeen(boolean seen) {
    prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply();
  }

  /** Clears onboarding flag so slides show again (e.g. after logout). Server URL is unchanged. */
  public void clearOnboardingSeen() {
    prefs.edit().remove(KEY_ONBOARDING_SEEN).apply();
  }
}

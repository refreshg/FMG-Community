package ge.fmg.community;

import android.content.Context;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.File;

/** Debug logging for Odoo session / cookie / preference persistence (tag: FmgSession). */
public final class SessionDiagnostics {
  public static final String TAG = "FmgSession";

  private SessionDiagnostics() {}

  public static void logPrefs(AppPrefs prefs, String phase) {
    Log.i(
        TAG,
        phase
            + " @capacitor/preferences — loggedIn="
            + prefs.isLoggedIn()
            + " onboardingSeen="
            + prefs.isOnboardingSeen()
            + " serverUrl(odooServer)="
            + prefs.getServer());
  }

  public static void logCookiesForBase(String phase, String odooBase) {
    CookieManager cm = CookieManager.getInstance();
    if (odooBase == null || odooBase.isEmpty()) {
      Log.w(TAG, phase + " ODOO_BASE is null/empty — cannot read cookies");
      return;
    }
    String atBase = cm.getCookie(odooBase);
    String atSlash = cm.getCookie(odooBase.endsWith("/") ? odooBase : odooBase + "/");
    logCookieLine(phase, "CookieManager.getCookie(ODOO_BASE)", atBase);
    if (atSlash != null && !atSlash.equals(atBase)) {
      logCookieLine(phase, "CookieManager.getCookie(ODOO_BASE + \"/\")", atSlash);
    }
    boolean hasSession = atBase != null && atBase.contains("session_id=");
    Log.i(TAG, phase + " session_id in cookie string=" + hasSession);
  }

  private static void logCookieLine(String phase, String label, String cookies) {
    if (cookies == null || cookies.isEmpty()) {
      Log.i(TAG, phase + " " + label + "=(empty or null)");
    } else {
      Log.i(TAG, phase + " " + label + "=" + cookies);
    }
  }

  public static void logFlush(String phase) {
    try {
      CookieManager.getInstance().flush();
      Log.i(TAG, phase + " CookieManager.flush() completed (no exception thrown)");
    } catch (Exception e) {
      Log.e(TAG, phase + " CookieManager.flush() FAILED", e);
    }
  }

  public static void logWebViewAndManifest(Context context, WebView webView) {
    Log.i(TAG, "Manifest: clearTaskOnLaunch NOT set (default false)");
    Log.i(TAG, "Manifest: finishOnTaskLaunch NOT set (default false)");
    Log.i(TAG, "Manifest: launchMode=singleTask on MainActivity");
    Log.i(TAG, "Manifest: no android:process split — same process retains WebView cookie store");

    CookieManager cm = CookieManager.getInstance();
    Log.i(TAG, "WebView: CookieManager.setAcceptCookie(true) — acceptCookie=" + cm.acceptCookie());
    Log.i(
        TAG,
        "WebView: setAcceptThirdPartyCookies(webView, true) —="
            + cm.acceptThirdPartyCookies(webView));

    WebSettings settings = webView.getSettings();
    Log.i(
        TAG,
        "WebView: domStorageEnabled="
            + settings.getDomStorageEnabled()
            + " databaseEnabled="
            + settings.getDatabaseEnabled());
    File dbDir = context.getDir("databases", Context.MODE_PRIVATE);
    Log.i(
        TAG,
        "WebView: getDir(databases)="
            + dbDir.getAbsolutePath()
            + " exists="
            + dbDir.exists()
            + " writable="
            + dbDir.canWrite());
    Log.i(TAG, "WebView: no incognito/private mode API used on this WebView instance");
  }

  public static void logScreenRoute(String phase, String screen) {
    Log.i(TAG, phase + " routing decision → show " + screen);
  }

  public static void logLoadRequest(String phase, String fullUrl) {
    Log.i(TAG, phase + " WebView.loadUrl(" + fullUrl + ")");
  }

  public static void logPageFinished(String phase, String url) {
    Log.i(TAG, phase + " WebView onPageFinished url=" + url);
    if (url != null && url.contains("/web/login")) {
      Log.w(
          TAG,
          phase
              + " DIAGNOSIS: redirected to /web/login — Odoo session cookie missing or expired");
    } else if (url != null && url.contains("/my/")) {
      Log.i(TAG, phase + " DIAGNOSIS: on portal /my/* — session cookie accepted by Odoo");
    }
  }
}

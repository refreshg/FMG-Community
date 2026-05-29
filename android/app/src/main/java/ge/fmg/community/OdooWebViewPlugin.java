package ge.fmg.community;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import android.webkit.CookieManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "OdooWebView")
public class OdooWebViewPlugin extends Plugin {
  private static final String TAG = "OdooWebView";
  private WebView odooWebView;
  private FrameLayout container;
  private ViewGroup root;
  private int headerPx = 52;
  private int tabbarPx = 60;
  private final ExecutorService netExecutor = Executors.newSingleThreadExecutor();

  @SuppressLint("SetJavaScriptEnabled")
  private void ensureWebView() {
    if (odooWebView != null) return;

    // Simplest working mode: native Odoo WebView full-screen on top.
    root = (ViewGroup) getActivity().findViewById(android.R.id.content);

    container = new FrameLayout(getActivity());
    container.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    container.setBackgroundColor(Color.TRANSPARENT);
    container.setVisibility(View.GONE);
    container.setClickable(true);

    odooWebView = new WebView(getActivity());
    odooWebView.setLayoutParams(new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    odooWebView.setFocusable(true);
    odooWebView.setFocusableInTouchMode(true);
    odooWebView.setClickable(true);
    odooWebView.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        v.requestFocus();
        v.requestFocusFromTouch();
        try {
          InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
          if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        } catch (Throwable ignored) {}
      }
      return false;
    });

    WebSettings s = odooWebView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setAllowFileAccess(false);
    s.setAllowContentAccess(false);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setLoadWithOverviewMode(true);
    s.setUseWideViewPort(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
    }

    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      cm.setAcceptThirdPartyCookies(odooWebView, true);
    }

    // Capture JS console logs (console.log/warn/error) from within the WebView.
    odooWebView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        String msg = consoleMessage.message();
        String src = consoleMessage.sourceId();
        int line = consoleMessage.lineNumber();
        String level = consoleMessage.messageLevel() != null ? consoleMessage.messageLevel().toString() : "UNKNOWN";
        Log.i(TAG, "console[" + level + "] " + src + ":" + line + " " + msg);

        JSObject data = new JSObject();
        data.put("level", level);
        data.put("sourceId", src);
        data.put("lineNumber", line);
        data.put("message", msg);
        notifyListeners("console", data);
        return super.onConsoleMessage(consoleMessage);
      }
    });

    odooWebView.setWebViewClient(new WebViewClient() {
      @Override
      public void onPageFinished(WebView view, String url) {
        Log.i(TAG, "pageLoaded url=" + url);
        JSObject data = new JSObject();
        data.put("url", url);
        notifyListeners("pageLoaded", data);

        // After load, sample DOM state to detect "loaded but blank" issues.
        try {
          view.evaluateJavascript(
            "(function(){try{return JSON.stringify({title:document.title||'', bodyLen:(document.body&&document.body.innerHTML)?document.body.innerHTML.length:0, ready:document.readyState||''});}catch(e){return JSON.stringify({error:String(e)});}})();",
            value -> {
              Log.i(TAG, "domInfo url=" + url + " value=" + value);
              JSObject dom = new JSObject();
              dom.put("url", url);
              dom.put("value", value);
              notifyListeners("domInfo", dom);
            }
          );
        } catch (Throwable t) {
          Log.e(TAG, "evaluateJavascript failed: " + t);
        }
      }

      @Override
      public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
        if (!request.isForMainFrame()) return;
        Log.e(TAG, "pageError url=" + request.getUrl() + " code=" + error.getErrorCode() + " desc=" + error.getDescription());
        JSObject data = new JSObject();
        data.put("url", request.getUrl().toString());
        data.put("code", error.getErrorCode());
        data.put("description", String.valueOf(error.getDescription()));
        notifyListeners("pageError", data);
      }

      @Override
      public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceResponse errorResponse) {
        if (!request.isForMainFrame()) return;
        Log.e(TAG, "pageHttpError url=" + request.getUrl() + " status=" + errorResponse.getStatusCode() + " reason=" + errorResponse.getReasonPhrase());
        JSObject data = new JSObject();
        data.put("url", request.getUrl().toString());
        data.put("statusCode", errorResponse.getStatusCode());
        data.put("reason", errorResponse.getReasonPhrase());
        notifyListeners("pageHttpError", data);
      }
    });

    container.addView(odooWebView);
    // Full-screen and on top.
    root.addView(container);
    container.bringToFront();
  }

  @PluginMethod
  public void setVisible(PluginCall call) {
    Boolean visible = call.getBoolean("visible");
    getActivity().runOnUiThread(() -> {
      ensureWebView();
      boolean v = visible != null && visible;
      if (container != null) {
        container.setVisibility(v ? View.VISIBLE : View.GONE);
      }
      call.resolve();
    });
  }

  @PluginMethod
  public void initialize(PluginCall call) {
    headerPx = call.getInt("headerHeightPx", 52);
    tabbarPx = call.getInt("tabbarHeightPx", 60);
    getActivity().runOnUiThread(() -> {
      ensureWebView();
      call.resolve();
    });
  }

  @PluginMethod
  public void loadUrl(PluginCall call) {
    String url = call.getString("url");
    if (url == null || url.isEmpty()) {
      call.reject("Missing url");
      return;
    }
    getActivity().runOnUiThread(() -> {
      ensureWebView();
      if (container != null) {
        container.setVisibility(View.VISIBLE);
        container.bringToFront();
      }
      Log.i(TAG, "loadUrl " + url);

      // Log user-agent (Odoo sometimes serves different bundles per UA)
      final String ua;
      try {
        ua = odooWebView.getSettings().getUserAgentString();
        Log.i(TAG, "userAgent " + ua);
        JSObject uaObj = new JSObject();
        uaObj.put("userAgent", ua);
        notifyListeners("userAgent", uaObj);
      } catch (Throwable t) {
        Log.e(TAG, "userAgent read failed: " + t);
        // best-effort fallback
        call.resolve();
        return;
      }

      // Fetch key response headers out-of-band (WebView doesn't expose them directly)
      fetchHeaders(url, ua);

      odooWebView.loadUrl(url);
      call.resolve();
    });
  }

  private void fetchHeaders(String targetUrl, String userAgent) {
    netExecutor.execute(() -> {
      HttpURLConnection conn = null;
      try {
        URL u = new URL(targetUrl);
        conn = (HttpURLConnection) u.openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", userAgent != null ? userAgent : "");

        int code = conn.getResponseCode();
        String csp = conn.getHeaderField("Content-Security-Policy");
        String xfo = conn.getHeaderField("X-Frame-Options");
        String location = conn.getHeaderField("Location");

        Log.i(TAG, "headers url=" + targetUrl + " code=" + code + " csp=" + (csp != null ? csp : "") + " xfo=" + (xfo != null ? xfo : "") + " location=" + (location != null ? location : ""));

        JSObject h = new JSObject();
        h.put("url", targetUrl);
        h.put("status", code);
        h.put("csp", csp != null ? csp : "");
        h.put("xFrameOptions", xfo != null ? xfo : "");
        h.put("location", location != null ? location : "");
        notifyListeners("headers", h);
      } catch (IOException e) {
        Log.e(TAG, "headers fetch failed url=" + targetUrl + " err=" + e);
      } catch (Throwable t) {
        Log.e(TAG, "headers fetch failed url=" + targetUrl + " err=" + t);
      } finally {
        if (conn != null) conn.disconnect();
      }
    });
  }

  @PluginMethod
  public void reload(PluginCall call) {
    getActivity().runOnUiThread(() -> {
      if (odooWebView != null) odooWebView.reload();
      call.resolve();
    });
  }
}


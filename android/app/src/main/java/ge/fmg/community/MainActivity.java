package ge.fmg.community;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
  private static final int BRAND_PURPLE = Color.parseColor("#714b67");
  private static final int NAV_INACTIVE = Color.parseColor("#bbbbbb");
  private static final int NAV_ACTIVE = Color.parseColor("#ffffff");
  private enum NavTab { HOME, TICKET, CREATE, OBJECTS, PROFILE }

  private AppPrefs prefs;
  private String odooBase;
  private String cachedUserDisplayName;
  private NavTab activeNavTab = NavTab.HOME;

  private View onboardingScreen;
  private ViewPager2 onboardingPager;
  private TextView onboardingSkip;
  private Button onboardingNext;
  private LinearLayout onboardingDots;
  private View[] dotViews;

  private View loginScreen;
  private EditText loginServerInput;
  private Button loginSubmitBtn;

  private View mainContainer;
  private WebView webView;
  private View bottomNavContainer;

  private View navHome;
  private View navTicket;
  private View navObjects;
  private View navProfile;
  private ImageView navHomeIcon;
  private ImageView navTicketIcon;
  private ImageView navObjectsIcon;
  private ImageView navProfileIcon;
  private TextView navHomeLabel;
  private TextView navTicketLabel;
  private TextView navObjectsLabel;
  private TextView navProfileLabel;

  private View menuOverlay;
  private View menuBackdrop;
  private View menuPanel;
  private TextView menuProfileSubname;
  private View menuServerEditor;
  private EditText menuServerInput;
  private boolean menuOpen;
  private boolean menuAnimating;

  private boolean loggingOut;
  private ValueCallback<Uri[]> filePathCallback;
  private Uri cameraPhotoUri;

  private ActivityResultLauncher<Intent> fileChooserLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    SplashScreen.installSplashScreen(this);
    if (BuildConfig.DEBUG) {
      WebView.setWebContentsDebuggingEnabled(true);
    }
    super.onCreate(savedInstanceState);

    WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
    setContentView(R.layout.activity_main);

    prefs = new AppPrefs(this);
    odooBase = prefs.getServer();

    fileChooserLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Uri[] results = null;
              if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                if (data.getClipData() != null) {
                  int count = data.getClipData().getItemCount();
                  results = new Uri[count];
                  for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                  }
                } else if (data.getData() != null) {
                  results = new Uri[] {data.getData()};
                }
              } else if (result.getResultCode() == RESULT_OK && cameraPhotoUri != null) {
                results = new Uri[] {cameraPhotoUri};
              }
              if (filePathCallback != null) {
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
              }
              cameraPhotoUri = null;
            });

    bindViews();
    applyBottomNavInsets();
    setupWebView();
    setupLogin();
    setupOnboarding();
    setupBottomNav();
    setupMenu();
    setupBackNavigation();

    if (prefs.isOnboardingSeen()) {
      showLogin();
    } else {
      showOnboarding();
    }
  }

  private void bindViews() {
    onboardingScreen = findViewById(R.id.onboarding_screen);
    onboardingPager = findViewById(R.id.onboarding_pager);
    onboardingSkip = findViewById(R.id.onboarding_skip);
    onboardingNext = findViewById(R.id.onboarding_next);
    onboardingDots = findViewById(R.id.onboarding_dots);

    loginScreen = findViewById(R.id.login_screen);
    loginServerInput = findViewById(R.id.login_server_input);
    loginSubmitBtn = findViewById(R.id.login_submit_btn);

    mainContainer = findViewById(R.id.main_container);
    webView = findViewById(R.id.odoo_webview);
    bottomNavContainer = findViewById(R.id.bottom_nav_container);

    navHome = findViewById(R.id.nav_home);
    navTicket = findViewById(R.id.nav_ticket);
    navObjects = findViewById(R.id.nav_objects);
    navProfile = findViewById(R.id.nav_profile);
    navHomeIcon = findViewById(R.id.nav_home_icon);
    navTicketIcon = findViewById(R.id.nav_ticket_icon);
    navObjectsIcon = findViewById(R.id.nav_objects_icon);
    navProfileIcon = findViewById(R.id.nav_profile_icon);
    navHomeLabel = findViewById(R.id.nav_home_label);
    navTicketLabel = findViewById(R.id.nav_ticket_label);
    navObjectsLabel = findViewById(R.id.nav_objects_label);
    navProfileLabel = findViewById(R.id.nav_profile_label);

    menuOverlay = findViewById(R.id.menu_overlay);
    menuBackdrop = findViewById(R.id.menu_backdrop);
    menuPanel = findViewById(R.id.menu_panel);
    menuProfileSubname = findViewById(R.id.menu_profile_subname);
    menuServerEditor = findViewById(R.id.menu_server_editor);
    menuServerInput = findViewById(R.id.menu_server_input);
  }

  private void applyBottomNavInsets() {
    if (bottomNavContainer == null) return;
    ViewCompat.setOnApplyWindowInsetsListener(
        bottomNavContainer,
        (v, insets) -> {
          int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
          v.setPadding(0, 0, 0, bottom);
          return insets;
        });
    ViewCompat.requestApplyInsets(bottomNavContainer);
  }

  private void setupWebView() {
    webView.setFocusable(true);
    webView.setFocusableInTouchMode(true);
    webView.setNestedScrollingEnabled(true);
    webView.setVerticalScrollBarEnabled(true);
    webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);

    WebSettings s = webView.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false);
    s.setLoadWithOverviewMode(false);
    s.setUseWideViewPort(true);
    s.setAllowFileAccess(true);
    s.setAllowContentAccess(true);
    s.setBuiltInZoomControls(false);
    s.setDisplayZoomControls(false);

    CookieManager cm = CookieManager.getInstance();
    cm.setAcceptCookie(true);
    cm.setAcceptThirdPartyCookies(webView, true);

    webView.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public boolean onShowFileChooser(
              WebView view,
              ValueCallback<Uri[]> callback,
              FileChooserParams params) {
            if (filePathCallback != null) {
              filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            openFileChooser(params);
            return true;
          }
        });

    webView.setWebViewClient(
        new WebViewClient() {
          @Override
          public void onPageFinished(WebView view, String url) {
            if (loggingOut) {
              finishLogout();
            } else {
              updateCachedUserNameFromSession();
            }
          }
        });
  }

  private void openFileChooser(WebChromeClient.FileChooserParams params) {
    Intent pickIntent = new Intent(Intent.ACTION_GET_CONTENT);
    pickIntent.addCategory(Intent.CATEGORY_OPENABLE);
    pickIntent.setType("*/*");
    if (params != null && params.getAcceptTypes() != null && params.getAcceptTypes().length > 0) {
      String type = params.getAcceptTypes()[0];
      if (type != null && !type.isEmpty()) {
        pickIntent.setType(type);
      }
    }
    pickIntent.putExtra(
        Intent.EXTRA_ALLOW_MULTIPLE,
        params != null && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);

    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (cameraIntent.resolveActivity(getPackageManager()) != null) {
      try {
        File photoFile = createImageFile();
        cameraPhotoUri =
            FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
      } catch (IOException e) {
        cameraIntent = null;
        cameraPhotoUri = null;
      }
    }

    Intent chooser = Intent.createChooser(pickIntent, "აირჩიე ფაილი");
    if (cameraIntent != null) {
      chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {cameraIntent});
    }
    fileChooserLauncher.launch(chooser);
  }

  private File createImageFile() throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    return File.createTempFile("FMG_" + timeStamp, ".jpg", getCacheDir());
  }

  private void setupOnboarding() {
    OnboardingSlideAdapter.Slide[] slides =
        new OnboardingSlideAdapter.Slide[] {
          new OnboardingSlideAdapter.Slide(
              R.drawable.onboarding_scene_1,
              R.drawable.ic_onboarding_ticket,
              R.string.onboarding_slide1_title,
              R.string.onboarding_slide1_body),
          new OnboardingSlideAdapter.Slide(
              R.drawable.onboarding_scene_2,
              R.drawable.ic_onboarding_wallet,
              R.string.onboarding_slide2_title,
              R.string.onboarding_slide2_body),
          new OnboardingSlideAdapter.Slide(
              R.drawable.onboarding_scene_3,
              R.drawable.ic_onboarding_bell,
              R.string.onboarding_slide3_title,
              R.string.onboarding_slide3_body),
        };
    onboardingPager.setAdapter(new OnboardingSlideAdapter(slides));
    onboardingPager.setOffscreenPageLimit(3);

    dotViews = new View[slides.length];
    onboardingDots.removeAllViews();
    int dotSize = (int) (8 * getResources().getDisplayMetrics().density);
    int dotMargin = (int) (6 * getResources().getDisplayMetrics().density);
    for (int i = 0; i < slides.length; i++) {
      View dot = new View(this);
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(dotSize, dotSize);
      if (i > 0) {
        lp.setMarginStart(dotMargin);
      }
      dot.setLayoutParams(lp);
      dotViews[i] = dot;
      onboardingDots.addView(dot);
    }
    updateOnboardingUi(0);

    onboardingPager.registerOnPageChangeCallback(
        new ViewPager2.OnPageChangeCallback() {
          @Override
          public void onPageSelected(int position) {
            updateOnboardingUi(position);
          }
        });

    onboardingSkip.setOnClickListener(v -> completeOnboarding());
    onboardingNext.setOnClickListener(
        v -> {
          int current = onboardingPager.getCurrentItem();
          if (current < slides.length - 1) {
            onboardingPager.setCurrentItem(current + 1, true);
          } else {
            completeOnboarding();
          }
        });
  }

  private void updateOnboardingUi(int position) {
    for (int i = 0; i < dotViews.length; i++) {
      dotViews[i].setBackgroundResource(
          i == position ? R.drawable.onboarding_dot_active : R.drawable.onboarding_dot_inactive);
    }
    onboardingSkip.setVisibility(position < 2 ? View.VISIBLE : View.GONE);
    onboardingNext.setText(
        position == 2 ? R.string.onboarding_finish : R.string.onboarding_next);
  }

  private void showOnboarding() {
    webView.stopLoading();
    onboardingPager.setCurrentItem(0, false);
    updateOnboardingUi(0);
    onboardingScreen.setVisibility(View.VISIBLE);
    loginScreen.setVisibility(View.GONE);
    mainContainer.setVisibility(View.GONE);
    if (menuOverlay != null) {
      menuOverlay.setVisibility(View.GONE);
      menuOpen = false;
    }
  }

  private void completeOnboarding() {
    prefs.setOnboardingSeen(true);
    onboardingScreen.setVisibility(View.GONE);
    showLogin();
  }

  private void setupLogin() {
    loginServerInput.setText(odooBase);
    loginSubmitBtn.setOnClickListener(v -> submitLogin());
    loginServerInput.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO) {
            submitLogin();
            return true;
          }
          return false;
        });
  }

  private void submitLogin() {
    String normalized = normalizeServer(loginServerInput.getText().toString());
    if (normalized == null) {
      Toast.makeText(this, "სერვერის მისამართი არასწორია", Toast.LENGTH_SHORT).show();
      return;
    }
    odooBase = normalized;
    prefs.setServer(odooBase);
    hideKeyboard(loginServerInput);
    showMain();
    loadOdooPath("/web/login");
  }

  private void setupBottomNav() {
    findViewById(R.id.nav_create).setOnClickListener(v -> navigateTo(NavTab.CREATE));
    navHome.setOnClickListener(v -> navigateTo(NavTab.HOME));
    navTicket.setOnClickListener(v -> navigateTo(NavTab.TICKET));
    navObjects.setOnClickListener(v -> navigateTo(NavTab.OBJECTS));
    navProfile.setOnClickListener(v -> navigateTo(NavTab.PROFILE));
    setActiveNavTab(NavTab.HOME);
  }

  private void navigateTo(NavTab tab) {
    if (tab == NavTab.PROFILE) {
      setActiveNavTab(NavTab.PROFILE);
      showMenu();
      return;
    }
    hideMenu();
    setActiveNavTab(tab);
    switch (tab) {
      case HOME:
        loadOdooPath(NavUrls.HOME);
        break;
      case TICKET:
        loadOdooPath(NavUrls.TICKETS);
        break;
      case CREATE:
        loadOdooPath(NavUrls.CREATE_TICKET);
        break;
      case OBJECTS:
        loadOdooPath(NavUrls.REAL_ESTATE);
        break;
      default:
        break;
    }
  }

  private void setActiveNavTab(NavTab tab) {
    activeNavTab = tab;
    applyNavItemStyle(navHomeIcon, navHomeLabel, tab == NavTab.HOME);
    applyNavItemStyle(navTicketIcon, navTicketLabel, tab == NavTab.TICKET);
    applyNavItemStyle(navObjectsIcon, navObjectsLabel, tab == NavTab.OBJECTS);
    applyNavItemStyle(navProfileIcon, navProfileLabel, tab == NavTab.PROFILE);
  }

  private void applyNavItemStyle(ImageView icon, TextView label, boolean active) {
    int color = active ? NAV_ACTIVE : NAV_INACTIVE;
    icon.setColorFilter(color);
    label.setTextColor(color);
  }

  private void setupMenu() {
    bindMenuRow(findViewById(R.id.menu_item_news), R.drawable.ic_menu_news, R.string.menu_news);
    bindMenuRow(findViewById(R.id.menu_item_offers), R.drawable.ic_menu_gift, R.string.menu_offers);
    bindMenuRow(
        findViewById(R.id.menu_item_how_it_works), R.drawable.ic_menu_help, R.string.menu_how_it_works);

    menuBackdrop.setOnClickListener(v -> hideMenu());

    findViewById(R.id.menu_item_profile).setOnClickListener(v -> openMenuPath(NavUrls.PROFILE));
    findViewById(R.id.menu_item_news)
        .setOnClickListener(v -> openMenuPath(NavUrls.NOTIFICATIONS));
    findViewById(R.id.menu_item_offers)
        .setOnClickListener(v -> openMenuPath(NavUrls.SUGGESTION));
    findViewById(R.id.menu_item_how_it_works)
        .setOnClickListener(v -> openMenuPath(NavUrls.HOW_IT_WORKS));

    findViewById(R.id.menu_item_server)
        .setOnClickListener(
            v -> {
              boolean show = menuServerEditor.getVisibility() != View.VISIBLE;
              menuServerEditor.setVisibility(show ? View.VISIBLE : View.GONE);
              if (show) {
                menuServerInput.setText(odooBase);
                menuServerInput.requestFocus();
              }
            });

    findViewById(R.id.menu_server_save_btn)
        .setOnClickListener(
            v -> {
              String normalized = normalizeServer(menuServerInput.getText().toString());
              if (normalized == null) {
                Toast.makeText(this, "სერვერის მისამართი არასწორია", Toast.LENGTH_SHORT).show();
                return;
              }
              odooBase = normalized;
              prefs.setServer(odooBase);
              loginServerInput.setText(odooBase);
              menuServerEditor.setVisibility(View.GONE);
              Toast.makeText(this, "შენახულია", Toast.LENGTH_SHORT).show();
              hideMenu();
              reloadCurrentNavView();
            });

    findViewById(R.id.menu_item_logout).setOnClickListener(v -> logout());

    findViewById(R.id.menu_social_youtube)
        .setOnClickListener(v -> openExternal("https://www.youtube.com/"));
    findViewById(R.id.menu_social_facebook)
        .setOnClickListener(v -> openExternal("https://www.facebook.com/"));
    findViewById(R.id.menu_social_linkedin)
        .setOnClickListener(v -> openExternal("https://www.linkedin.com/"));
    findViewById(R.id.menu_social_instagram)
        .setOnClickListener(v -> openExternal("https://www.instagram.com/"));
    findViewById(R.id.menu_social_tiktok)
        .setOnClickListener(v -> openExternal("https://www.tiktok.com/"));
    findViewById(R.id.menu_social_website)
        .setOnClickListener(v -> openExternal("https://fmg.ge/"));
  }

  private void bindMenuRow(View row, int iconRes, int labelRes) {
    if (row == null) return;
    ImageView icon = row.findViewById(R.id.menu_row_icon);
    TextView label = row.findViewById(R.id.menu_row_label);
    if (icon != null) icon.setImageResource(iconRes);
    if (label != null) label.setText(labelRes);
  }

  private void openMenuPath(String path) {
    hideMenu();
    loadOdooPath(path);
  }

  private void reloadCurrentNavView() {
    if (activeNavTab == NavTab.PROFILE) {
      setActiveNavTab(NavTab.HOME);
      loadOdooPath(NavUrls.HOME);
      return;
    }
    navigateTo(activeNavTab);
  }

  private void showMenu() {
    if (menuOpen || menuAnimating) return;
    refreshMenuProfileName();
    menuServerEditor.setVisibility(View.GONE);
    menuServerInput.setText(odooBase);
    menuOverlay.setVisibility(View.VISIBLE);
    menuBackdrop.setAlpha(0f);
    menuPanel.setTranslationY(menuPanel.getHeight() > 0 ? menuPanel.getHeight() : 1200f);
    menuAnimating = true;
    menuPanel.post(
        () -> {
          float slideFrom = menuPanel.getHeight();
          menuPanel.setTranslationY(slideFrom);
          menuBackdrop.animate().alpha(1f).setDuration(250).start();
          menuPanel
              .animate()
              .translationY(0f)
              .setDuration(250)
              .withEndAction(
                  () -> {
                    menuOpen = true;
                    menuAnimating = false;
                  })
              .start();
        });
  }

  private void hideMenu() {
    if (!menuOpen || menuAnimating) {
      menuOverlay.setVisibility(View.GONE);
      menuOpen = false;
      return;
    }
    menuAnimating = true;
    menuServerEditor.setVisibility(View.GONE);
    float slideTo = menuPanel.getHeight();
    menuBackdrop.animate().alpha(0f).setDuration(250).start();
    menuPanel
        .animate()
        .translationY(slideTo)
        .setDuration(250)
        .withEndAction(
            () -> {
              menuOverlay.setVisibility(View.GONE);
              menuOpen = false;
              menuAnimating = false;
            })
        .start();
  }

  private void refreshMenuProfileName() {
    String displayName =
        cachedUserDisplayName != null && !cachedUserDisplayName.isEmpty()
            ? cachedUserDisplayName
            : getString(R.string.default_user_name);
    menuProfileSubname.setText(displayName);
    fetchProfileDisplayName();
  }

  private void setupBackNavigation() {
    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                if (onboardingScreen.getVisibility() == View.VISIBLE) {
                  int current = onboardingPager.getCurrentItem();
                  if (current > 0) {
                    onboardingPager.setCurrentItem(current - 1, true);
                  }
                  return;
                }
                if (menuOpen) {
                  hideMenu();
                  return;
                }
                if (mainContainer.getVisibility() == View.VISIBLE && webView.canGoBack()) {
                  webView.goBack();
                  return;
                }
                if (mainContainer.getVisibility() == View.VISIBLE) {
                  showLogin();
                  return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
              }
            });
  }

  private void updateCachedUserNameFromSession() {
    String js =
        "(function(){try{var s=window.odoo&&odoo.session_info;if(s){return "
            + "s.name||s.partner_display_name||s.username||'';}}catch(e){}return '';})()";
    webView.evaluateJavascript(
        js,
        value -> {
          String name = parseJsString(value);
          if (name != null && !name.isEmpty()) {
            cachedUserDisplayName = name;
            if (menuOpen && menuProfileSubname != null) {
              menuProfileSubname.setText(name);
            }
          }
        });
  }

  private void fetchProfileDisplayName() {
    updateCachedUserNameFromSession();
    String fetchJs =
        "(function(){try{return fetch('/web/session/get_session_info',{method:'POST',"
            + "headers:{'Content-Type':'application/json'},body:'{}',credentials:'same-origin'})"
            + ".then(function(r){return r.json();}).then(function(d){"
            + "var x=d.result||d;return x.name||x.partner_display_name||x.username||'';})"
            + ".catch(function(){return '';});}catch(e){return Promise.resolve('');}})()";
    webView.evaluateJavascript(
        fetchJs,
        value -> {
          // Promise result may arrive as quoted string on newer WebViews
          String name = parseJsString(value);
          if (name != null && !name.isEmpty()) {
            cachedUserDisplayName = name;
            runOnUiThread(() -> menuProfileSubname.setText(name));
          }
        });
  }

  private static String parseJsString(String raw) {
    if (raw == null || "null".equals(raw) || "\"\"".equals(raw)) {
      return null;
    }
    String s = raw.trim();
    if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
      s = s.substring(1, s.length() - 1);
      s = s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
    return s.isEmpty() ? null : s;
  }

  private void showLogin() {
    hideMenu();
    onboardingScreen.setVisibility(View.GONE);
    mainContainer.setVisibility(View.GONE);
    loginScreen.setVisibility(View.VISIBLE);
    odooBase = prefs.getServer();
    loginServerInput.setText(odooBase);
    webView.stopLoading();
    webView.post(
        () -> {
          loginServerInput.requestFocus();
          InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
          if (imm != null) {
            imm.showSoftInput(loginServerInput, InputMethodManager.SHOW_IMPLICIT);
          }
        });
  }

  private void showMain() {
    loginScreen.setVisibility(View.GONE);
    hideMenu();
    mainContainer.setVisibility(View.VISIBLE);
    setActiveNavTab(NavTab.HOME);
  }

  private void loadOdooPath(String path) {
    webView.loadUrl(NavUrls.url(odooBase, path));
  }

  private void logout() {
    hideMenu();
    loggingOut = true;
    cachedUserDisplayName = null;
    loadOdooPath("/web/session/logout");
    webView.postDelayed(this::finishLogout, 3000);
  }

  private void finishLogout() {
    if (!loggingOut) return;
    loggingOut = false;
    prefs.clearOnboardingSeen();
    CookieManager.getInstance()
        .removeAllCookies(
            value -> {
              CookieManager.getInstance().flush();
              runOnUiThread(this::showOnboarding);
            });
  }

  private void openExternal(String url) {
    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
  }

  private void hideKeyboard(View view) {
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  static String normalizeServer(String input) {
    if (input == null) return null;
    String s = input.trim();
    if (s.isEmpty()) return null;
    if (!s.matches("(?i)https?://.*")) {
      s = "https://" + s;
    }
    try {
      URI uri = new URI(s);
      String scheme = uri.getScheme();
      if (scheme == null || (!scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("http"))) {
        return null;
      }
      String host = uri.getHost();
      if (host == null || host.isEmpty()) return null;
      int port = uri.getPort();
      if (port > 0) {
        return scheme.toLowerCase() + "://" + host + ":" + port;
      }
      return scheme.toLowerCase() + "://" + host;
    } catch (Exception e) {
      return null;
    }
  }
}

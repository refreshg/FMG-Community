package ge.fmg.community;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
  private enum NavTab { HOME, PROPERTY, SERVICE, PROFILE, FINANCE, COMMUNITY, MENU }

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

  private View headerContainer;
  private View navHome;
  private View navProperty;
  private View navService;
  private View navFinance;
  private View navCommunity;
  private View navMenu;
  private FrameLayout navHomeIconWrap;
  private FrameLayout navPropertyIconWrap;
  private FrameLayout navServiceIconWrap;
  private FrameLayout navFinanceIconWrap;
  private FrameLayout navCommunityIconWrap;
  private FrameLayout navMenuIconWrap;
  private ImageView navHomeIcon;
  private ImageView navPropertyIcon;
  private ImageView navServiceIcon;
  private TextView navFinanceLari;
  private ImageView navCommunityIcon;
  private ImageView navMenuIcon;
  private TextView navHomeLabel;
  private TextView navPropertyLabel;
  private TextView navServiceLabel;
  private TextView navFinanceLabel;
  private TextView navCommunityLabel;
  private TextView navMenuLabel;

  private View menuOverlay;
  private View menuBackdrop;
  private View menuPanel;
  private View menuServerEditor;
  private EditText menuServerInput;
  private boolean menuOpen;
  private boolean menuAnimating;

  private View legalOverlay;
  private TextView legalTitle;
  private View legalClose;
  private WebView legalContentWebView;
  private boolean legalOpen;

  private boolean loggingOut;
  /** True while waiting for first onPageFinished after cold start resume load to /my/home. */
  private boolean sessionResumeProbe;
  private ValueCallback<Uri[]> filePathCallback;
  private Uri cameraPhotoUri;
  private WebChromeClient.FileChooserParams pendingFileChooserParams;

  private ActivityResultLauncher<Intent> fileChooserLauncher;
  private ActivityResultLauncher<String> cameraPermissionLauncher;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    SplashScreen.installSplashScreen(this);
    if (BuildConfig.DEBUG) {
      WebView.setWebContentsDebuggingEnabled(true);
    }
    super.onCreate(savedInstanceState);

    prefs = new AppPrefs(this);
    odooBase = prefs.getServer();
    logColdStartDiagnostics();

    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    setContentView(R.layout.activity_main);
    configureSystemBars();

    cameraPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
              WebChromeClient.FileChooserParams pending = pendingFileChooserParams;
              pendingFileChooserParams = null;
              if (pending != null) {
                launchFileChooserIntent(pending, granted);
              }
            });

    fileChooserLauncher =
        registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              Uri[] results = null;
              if (result.getResultCode() == RESULT_OK) {
                Intent data = result.getData();
                if (data != null && data.getClipData() != null) {
                  int count = data.getClipData().getItemCount();
                  results = new Uri[count];
                  for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                  }
                } else if (data != null && data.getData() != null) {
                  results = new Uri[] {data.getData()};
                } else if (isCapturedPhotoValid(cameraPhotoUri)) {
                  results = new Uri[] {cameraPhotoUri};
                }
              }
              deliverFileChooserResult(results);
            });

    bindViews();
    applyHeaderInsets();
    applyFullscreenScreenInsets(loginScreen);
    applyFullscreenScreenInsets(onboardingScreen);
    applyBottomNavInsets();
    setupWebView();
    setupLogin();
    setupOnboarding();
    setupHeader();
    setupBottomNav();
    setupMenu();
    setupLegalOverlay();
    setupBackNavigation();

    routeStartup();
  }

  /**
   * Restores UI from preferences: onboarding → server screen → WebView (when logged in).
   * {@code loggedIn} is set only after Odoo redirects to /my/*; cookies persist via CookieManager.
   */
  /** STEP 2 — logged before any screen is shown on cold start. */
  private void logColdStartDiagnostics() {
    Log.i(SessionDiagnostics.TAG, "========== STEP 2: cold start (onCreate, before UI route) ==========");
    SessionDiagnostics.logPrefs(prefs, "STEP2");
    SessionDiagnostics.logCookiesForBase("STEP2", odooBase);
  }

  private void routeStartup() {
    odooBase = prefs.getServer();
    Log.i(SessionDiagnostics.TAG, "========== routeStartup ==========");
    SessionDiagnostics.logPrefs(prefs, "STEP2 routeStartup");
    SessionDiagnostics.logCookiesForBase("STEP2 routeStartup", odooBase);

    // Older builds could set onboardingSeen without a successful login; reset until loggedIn.
    if (!prefs.isLoggedIn() && prefs.isOnboardingSeen()) {
      Log.w(
          SessionDiagnostics.TAG,
          "routeStartup: clearing legacy onboardingSeen (loggedIn still false)");
      prefs.clearLoginState();
      SessionDiagnostics.logPrefs(prefs, "STEP2 after legacy clear");
    }
    if (prefs.isLoggedIn() && odooBase != null && !odooBase.isEmpty()) {
      SessionDiagnostics.logScreenRoute("STEP2", "WebView + load " + NavUrls.HOME);
      sessionResumeProbe = true;
      showMain();
      loadOdooPath(NavUrls.HOME);
      return;
    }
    if (!prefs.isLoggedIn() && !prefs.isOnboardingSeen()) {
      SessionDiagnostics.logScreenRoute("STEP2", "onboarding slides");
      showOnboarding();
      return;
    }
    SessionDiagnostics.logScreenRoute("STEP2", "server URL screen (login shell)");
    showLogin();
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

    headerContainer = findViewById(R.id.header_container);
    navHome = findViewById(R.id.nav_home);
    navProperty = findViewById(R.id.nav_property);
    navService = findViewById(R.id.nav_service);
    navFinance = findViewById(R.id.nav_finance);
    navCommunity = findViewById(R.id.nav_community);
    navMenu = findViewById(R.id.nav_menu);
    navHomeIconWrap = findViewById(R.id.nav_home_icon_wrap);
    navPropertyIconWrap = findViewById(R.id.nav_property_icon_wrap);
    navServiceIconWrap = findViewById(R.id.nav_service_icon_wrap);
    navFinanceIconWrap = findViewById(R.id.nav_finance_icon_wrap);
    navCommunityIconWrap = findViewById(R.id.nav_community_icon_wrap);
    navMenuIconWrap = findViewById(R.id.nav_menu_icon_wrap);
    navHomeIcon = findViewById(R.id.nav_home_icon);
    navPropertyIcon = findViewById(R.id.nav_property_icon);
    navServiceIcon = findViewById(R.id.nav_service_icon);
    navFinanceLari = findViewById(R.id.nav_finance_lari);
    navCommunityIcon = findViewById(R.id.nav_community_icon);
    navMenuIcon = findViewById(R.id.nav_menu_icon);
    navHomeLabel = findViewById(R.id.nav_home_label);
    navPropertyLabel = findViewById(R.id.nav_property_label);
    navServiceLabel = findViewById(R.id.nav_service_label);
    navFinanceLabel = findViewById(R.id.nav_finance_label);
    navCommunityLabel = findViewById(R.id.nav_community_label);
    navMenuLabel = findViewById(R.id.nav_menu_label);

    View menuRoot = findViewById(R.id.menu_overlay);
    menuOverlay = menuRoot;
    if (menuRoot != null) {
      menuBackdrop = menuRoot.findViewById(R.id.menu_backdrop);
      menuPanel = menuRoot.findViewById(R.id.menu_panel);
      menuServerEditor = menuRoot.findViewById(R.id.menu_server_editor);
      menuServerInput = menuRoot.findViewById(R.id.menu_server_input);
    }

    View legalRoot = findViewById(R.id.legal_overlay);
    legalOverlay = legalRoot;
    if (legalRoot != null) {
      legalTitle = legalRoot.findViewById(R.id.legal_title);
      legalClose = legalRoot.findViewById(R.id.legal_close);
      legalContentWebView = legalRoot.findViewById(R.id.legal_content_webview);
    }
  }

  private void configureSystemBars() {
    getWindow().setStatusBarColor(Color.TRANSPARENT);
    applySystemBarAppearance(false);
  }

  private void applySystemBarAppearance(boolean mainChrome) {
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (controller != null) {
      controller.setAppearanceLightStatusBars(!mainChrome);
    }
    getWindow()
        .setNavigationBarColor(
            ContextCompat.getColor(this, mainChrome ? R.color.header_bg : R.color.surface));
    getWindow().setStatusBarColor(ContextCompat.getColor(this, mainChrome ? R.color.header_bg : R.color.surface));
  }

  private void applyHeaderInsets() {
    if (headerContainer == null) return;
    ViewCompat.setOnApplyWindowInsetsListener(
        headerContainer,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.statusBars()
                      | WindowInsetsCompat.Type.displayCutout());
          v.setPaddingRelative(0, bars.top, 0, 0);
          return insets;
        });
    ViewCompat.requestApplyInsets(headerContainer);
  }

  private void applyFullscreenScreenInsets(View screen) {
    if (screen == null) return;
    ViewCompat.setOnApplyWindowInsetsListener(
        screen,
        (v, insets) -> {
          Insets bars =
              insets.getInsets(
                  WindowInsetsCompat.Type.systemBars()
                      | WindowInsetsCompat.Type.displayCutout());
          v.setPaddingRelative(bars.left, bars.top, bars.right, bars.bottom);
          return insets;
        });
    ViewCompat.requestApplyInsets(screen);
  }

  private void applyBottomNavInsets() {
    if (bottomNavContainer == null) return;
    ViewCompat.setOnApplyWindowInsetsListener(
        bottomNavContainer,
        (v, insets) -> {
          int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
          // Use bottom margin for the system gesture area so we do not shrink the fixed-height
          // bottom nav container (shrinking can clip tab labels).
          ViewGroup.LayoutParams rawLp = v.getLayoutParams();
          if (rawLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rawLp;
            if (lp.bottomMargin != bottom) {
              lp.bottomMargin = bottom;
              v.setLayoutParams(lp);
            }
          }
          // Match web reference: WebView content reserves space for the whole bottom bar
          // (safe-tab) + safe-area inset.
          if (webView != null) {
            int safeTab = getResources().getDimensionPixelSize(R.dimen.bottom_nav_total_height);
            int webBottom = safeTab + bottom;
            if (webView.getPaddingBottom() != webBottom) {
              webView.setPadding(0, 0, 0, webBottom);
              webView.setClipToPadding(false);
            }
          }
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
    SessionDiagnostics.logWebViewAndManifest(this, webView);

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
          public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (sessionResumeProbe) {
              SessionDiagnostics.logPageFinished("STEP3 onPageStarted (resume probe)", url);
            }
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            if (sessionResumeProbe) {
              Log.i(
                  SessionDiagnostics.TAG,
                  "========== STEP 3: first navigation after resume load ==========");
              SessionDiagnostics.logPageFinished("STEP3 onPageFinished (resume probe)", url);
              sessionResumeProbe = false;
            }
            handleWebViewUrlLoaded(url);
          }
        });
  }

  private void openFileChooser(WebChromeClient.FileChooserParams params) {
    if (!hasCameraPermission()) {
      pendingFileChooserParams = params;
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
      return;
    }
    launchFileChooserIntent(params, true);
  }

  private void launchFileChooserIntent(
      WebChromeClient.FileChooserParams params, boolean includeCamera) {
    cameraPhotoUri = null;

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

    Intent cameraIntent = null;
    if (includeCamera && hasCameraPermission()) {
      cameraIntent = buildCameraCaptureIntent();
    }

    Intent chooser = Intent.createChooser(pickIntent, "აირჩიე ფაილი");
    if (cameraIntent != null) {
      chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] {cameraIntent});
    }
    fileChooserLauncher.launch(chooser);
  }

  private Intent buildCameraCaptureIntent() {
    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (cameraIntent.resolveActivity(getPackageManager()) == null) {
      return null;
    }
    try {
      File photoFile = createImageFile();
      String authority = getPackageName() + ".fileprovider";
      cameraPhotoUri = FileProvider.getUriForFile(this, authority, photoFile);

      cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
      cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      cameraIntent.setClipData(ClipData.newUri(getContentResolver(), "capture", cameraPhotoUri));

      for (android.content.pm.ResolveInfo info :
          getPackageManager().queryIntentActivities(cameraIntent, PackageManager.MATCH_DEFAULT_ONLY)) {
        String pkg = info.activityInfo.packageName;
        grantUriPermission(
            pkg,
            cameraPhotoUri,
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      }
      return cameraIntent;
    } catch (IOException e) {
      cameraPhotoUri = null;
      return null;
    }
  }

  private boolean hasCameraPermission() {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean isCapturedPhotoValid(Uri uri) {
    if (uri == null) {
      return false;
    }
    try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
      return in != null && in.read() != -1;
    } catch (IOException e) {
      return false;
    }
  }

  private void deliverFileChooserResult(Uri[] results) {
    if (filePathCallback != null) {
      filePathCallback.onReceiveValue(results);
      filePathCallback = null;
    }
    cameraPhotoUri = null;
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
    float density = getResources().getDisplayMetrics().density;
    int dotInactive = (int) (8 * density);
    int dotActiveW = (int) (24 * density);
    int dotActiveH = (int) (8 * density);
    int dotMargin = (int) (6 * density);
    for (int i = 0; i < slides.length; i++) {
      View dot = new View(this);
      boolean active = i == 0;
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              active ? dotActiveW : dotInactive, active ? dotActiveH : dotInactive);
      if (i > 0) {
        lp.setMarginStart(dotMargin);
      }
      dot.setLayoutParams(lp);
      dot.setBackgroundResource(
          active ? R.drawable.onboarding_dot_active : R.drawable.onboarding_dot_inactive);
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
    float density = getResources().getDisplayMetrics().density;
    int dotInactive = (int) (8 * density);
    int dotActiveW = (int) (24 * density);
    int dotActiveH = (int) (8 * density);
    for (int i = 0; i < dotViews.length; i++) {
      boolean active = i == position;
      View dot = dotViews[i];
      LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
      lp.width = active ? dotActiveW : dotInactive;
      lp.height = active ? dotActiveH : dotInactive;
      dot.setLayoutParams(lp);
      dot.setBackgroundResource(
          active ? R.drawable.onboarding_dot_active : R.drawable.onboarding_dot_inactive);
    }
    onboardingSkip.setVisibility(View.VISIBLE);
    onboardingNext.setText(
        position == 2 ? R.string.onboarding_finish : R.string.onboarding_next);
  }

  private void showOnboarding() {
    applySystemBarAppearance(false);
    hideLegalPage();
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

  /** Hides onboarding for this session only; persisted after successful Odoo login. */
  private void completeOnboarding() {
    onboardingScreen.setVisibility(View.GONE);
    showLogin();
  }

  private void handleWebViewUrlLoaded(String url) {
    if (loggingOut) {
      finishLogout();
      return;
    }
    if (isOdooPortalSessionUrl(url)) {
      markLoginSuccess(url);
    }
    updateCachedUserNameFromSession();
  }

  /** True when Odoo has redirected to the authenticated portal (/my/*). */
  private static boolean isOdooPortalSessionUrl(String url) {
    if (url == null || url.isEmpty()) {
      return false;
    }
    try {
      URI uri = URI.create(url);
      String path = uri.getPath();
      if (path == null || path.isEmpty()) {
        return false;
      }
      return path.contains("/my/");
    } catch (Exception ignored) {
      return false;
    }
  }

  private void markLoginSuccess(String triggerUrl) {
    Log.i(
        SessionDiagnostics.TAG,
        "========== STEP 1: successful login (/my/* detected) ==========");
    Log.i(SessionDiagnostics.TAG, "STEP1 trigger url=" + triggerUrl);
    SessionDiagnostics.logCookiesForBase("STEP1 (before persist)", odooBase);
    if (!prefs.isLoggedIn()) {
      prefs.persistLoginSuccess();
      CookieManager.getInstance().flush();
      Log.i(SessionDiagnostics.TAG, "STEP1 wrote loggedIn=true onboardingSeen=true to prefs");
    } else {
      Log.i(SessionDiagnostics.TAG, "STEP1 loggedIn was already true — prefs not rewritten");
    }
    SessionDiagnostics.logPrefs(prefs, "STEP1");
    SessionDiagnostics.logFlush("STEP1");
    SessionDiagnostics.logCookiesForBase("STEP1 (after flush)", odooBase);
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

  private void setupHeader() {
    View.OnClickListener goHome = v -> navigateHeaderHome();
    View logoPill = findViewById(R.id.header_logo_pill);
    View appTitle = findViewById(R.id.header_app_title);
    if (logoPill != null) {
      logoPill.setOnClickListener(goHome);
    }
    if (appTitle != null) {
      appTitle.setOnClickListener(goHome);
    }
    View bellBtn = findViewById(R.id.header_bell_btn);
    if (bellBtn != null) {
      bellBtn.setOnClickListener(
          v -> {
            String number = "0322470600";
            Uri uri = Uri.parse("tel:" + Uri.encode(number));
            startActivity(new Intent(Intent.ACTION_DIAL, uri));
          });
    }
  }

  private void navigateHeaderHome() {
    if (legalOpen) {
      hideLegalPage();
    }
    hideMenu();
    loadOdooPath(NavUrls.HOME);
  }

  private void setupBottomNav() {
    navHome.setOnClickListener(v -> navigateTo(NavTab.HOME));
    navProperty.setOnClickListener(v -> navigateTo(NavTab.PROPERTY));
    navService.setOnClickListener(v -> navigateTo(NavTab.SERVICE));
    // Center button (profile) is a content tab in this build.
    findViewById(R.id.nav_profile_center).setOnClickListener(v -> navigateTo(NavTab.PROFILE));
    navFinance.setOnClickListener(v -> navigateTo(NavTab.FINANCE));
    navCommunity.setOnClickListener(v -> navigateTo(NavTab.COMMUNITY));
    navMenu.setOnClickListener(
        v -> {
          if (legalOpen) {
            hideLegalPage();
          }
          // Keep native menu overlay behavior.
          setActiveNavTab(NavTab.MENU);
          showMenu();
          // Also bind to the base URL + /menu as requested.
          loadOdooPath(NavUrls.MENU);
        });
    setActiveNavTab(NavTab.HOME);
  }

  private void navigateTo(NavTab tab) {
    if (legalOpen) {
      hideLegalPage();
    }
    // Menu is an overlay; navigating elsewhere should close it.
    if (tab != NavTab.MENU) {
      hideMenu();
    }
    setActiveNavTab(tab);
    switch (tab) {
      case HOME:
        loadOdooPath(NavUrls.HOME);
        break;
      case PROPERTY:
        loadOdooPath(NavUrls.PROPERTY);
        break;
      case SERVICE:
        loadOdooPath(NavUrls.SERVICE);
        break;
      case PROFILE:
        loadOdooPath(NavUrls.PROFILE);
        break;
      case FINANCE:
        loadOdooPath(NavUrls.FINANCE);
        break;
      case COMMUNITY:
        loadOdooPath(NavUrls.COMMUNITY);
        break;
      case MENU:
        showMenu();
        loadOdooPath(NavUrls.MENU);
        break;
      default:
        break;
    }
  }

  private void setActiveNavTab(NavTab tab) {
    activeNavTab = tab;
    applyNavItemStyle(navHomeIconWrap, navHomeIcon, navHomeLabel, tab == NavTab.HOME);
    applyNavItemStyle(navPropertyIconWrap, navPropertyIcon, navPropertyLabel, tab == NavTab.PROPERTY);
    applyNavItemStyle(navServiceIconWrap, navServiceIcon, navServiceLabel, tab == NavTab.SERVICE);
    // Center FAB does not use label/iconWrap styling here; keep its visual constant.
    applyNavItemStyle(navFinanceIconWrap, navFinanceLari, navFinanceLabel, tab == NavTab.FINANCE);
    applyNavItemStyle(
        navCommunityIconWrap, navCommunityIcon, navCommunityLabel, tab == NavTab.COMMUNITY);
    applyNavItemStyle(navMenuIconWrap, navMenuIcon, navMenuLabel, tab == NavTab.MENU);
  }

  private void applyNavItemStyle(
      FrameLayout iconWrap, View icon, TextView label, boolean active) {
    int iconColor =
        ContextCompat.getColor(
            this, active ? R.color.nav_icon_on_bar_active : R.color.nav_icon_on_bar);
    int labelColor =
        ContextCompat.getColor(
            this, active ? R.color.nav_label_on_bar_active : R.color.nav_label_on_bar);
    if (active) {
      iconWrap.setBackgroundResource(R.drawable.bg_nav_active_circle);
    } else {
      iconWrap.setBackground(null);
    }
    if (icon instanceof ImageView) {
      ((ImageView) icon).setColorFilter(iconColor);
    } else if (icon instanceof TextView) {
      ((TextView) icon).setTextColor(iconColor);
    }
    if (label != null) {
      label.setTextColor(labelColor);
      label.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }
  }

  private void setupMenu() {
    View menuRoot = menuOverlay != null ? menuOverlay : findViewById(R.id.menu_overlay);
    if (menuRoot == null) return;

    if (menuBackdrop != null) {
      menuBackdrop.setOnClickListener(v -> hideMenu());
    }

    menuRoot.findViewById(R.id.menu_item_profile)
        .setOnClickListener(v -> openMenuPath(NavUrls.PROFILE));
    menuRoot.findViewById(R.id.menu_item_news)
        .setOnClickListener(v -> openMenuPath(NavUrls.NEWS));
    menuRoot.findViewById(R.id.menu_item_offers)
        .setOnClickListener(v -> openMenuPath(NavUrls.SUGGESTION));
    menuRoot.findViewById(R.id.menu_item_how_it_works)
        .setOnClickListener(v -> openMenuPath(NavUrls.HOW_IT_WORKS));
    menuRoot
        .findViewById(R.id.menu_item_privacy)
        .setOnClickListener(
            v -> showLegalPage(LegalDocuments.PRIVACY_HTML, R.string.legal_privacy_title));
    menuRoot
        .findViewById(R.id.menu_item_terms)
        .setOnClickListener(v -> showLegalPage(LegalDocuments.TERMS_HTML, R.string.legal_terms_title));

    menuRoot.findViewById(R.id.menu_item_server)
        .setOnClickListener(
            v -> {
              boolean show = menuServerEditor.getVisibility() != View.VISIBLE;
              menuServerEditor.setVisibility(show ? View.VISIBLE : View.GONE);
              if (show) {
                menuServerInput.setText(odooBase);
                menuServerInput.requestFocus();
              }
            });

    menuRoot
        .findViewById(R.id.menu_server_save_btn)
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

    menuRoot.findViewById(R.id.menu_item_logout).setOnClickListener(v -> logout());
  }

  // Menu rows are defined in XML (emoji + labels); no runtime binding needed.

  private void openMenuPath(String path) {
    hideMenu();
    loadOdooPath(path);
  }

  private void setupLegalOverlay() {
    if (legalContentWebView == null) return;

    WebSettings settings = legalContentWebView.getSettings();
    settings.setJavaScriptEnabled(false);
    settings.setDomStorageEnabled(false);
    settings.setAllowFileAccess(true);
    settings.setAllowContentAccess(false);
    settings.setBuiltInZoomControls(false);

    legalContentWebView.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return true;
          }

          @SuppressWarnings("deprecation")
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return true;
          }
        });

    legalClose.setOnClickListener(v -> hideLegalPage());
  }

  private void showLegalPage(String assetPath, int titleResId) {
    if (legalOverlay == null || legalContentWebView == null || legalTitle == null) {
      Toast.makeText(this, "Legal page unavailable", Toast.LENGTH_SHORT).show();
      return;
    }
    hideMenuImmediate();
    legalTitle.setText(titleResId);
    try {
      getAssets().open(assetPath).close();
    } catch (IOException e) {
      Toast.makeText(this, "Legal document missing: " + assetPath, Toast.LENGTH_LONG).show();
      return;
    }
    legalContentWebView.loadUrl(LegalDocuments.assetUrl(assetPath));
    legalOverlay.setVisibility(View.VISIBLE);
    legalOverlay.bringToFront();
    legalOpen = true;
  }

  private void hideLegalPage() {
    if (!legalOpen) return;
    legalOverlay.setVisibility(View.GONE);
    legalContentWebView.loadUrl("about:blank");
    legalContentWebView.scrollTo(0, 0);
    legalOpen = false;
  }

  private void hideMenuImmediate() {
    if (menuServerEditor != null) {
      menuServerEditor.setVisibility(View.GONE);
    }
    if (menuOverlay != null) {
      menuOverlay.setVisibility(View.GONE);
    }
    menuOpen = false;
    menuAnimating = false;
  }

  private void reloadCurrentNavView() {
    navigateTo(activeNavTab);
  }

  private void showMenu() {
    if (menuOpen || menuAnimating) return;
    if (legalOpen) {
      hideLegalPage();
    }
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
      if (menuOverlay != null) {
        menuOverlay.setVisibility(View.GONE);
      }
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

  private static boolean isValidDisplayName(String name) {
    if (name == null) return false;
    String s = name.trim();
    if (s.isEmpty()) return false;
    if ("{}".equals(s) || "[]".equals(s)) return false;
    return !"null".equalsIgnoreCase(s) && !"undefined".equalsIgnoreCase(s);
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
                if (legalOpen) {
                  hideLegalPage();
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
          if (isValidDisplayName(name)) {
            cachedUserDisplayName = name;
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
    return isValidDisplayName(s) ? s.trim() : null;
  }

  private void showLogin() {
    applySystemBarAppearance(false);
    hideLegalPage();
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
    applySystemBarAppearance(true);
    loginScreen.setVisibility(View.GONE);
    hideMenu();
    mainContainer.setVisibility(View.VISIBLE);
    setActiveNavTab(NavTab.HOME);
  }

  private void loadOdooPath(String path) {
    String fullUrl = NavUrls.url(odooBase, path);
    if (sessionResumeProbe) {
      Log.i(
          SessionDiagnostics.TAG,
          "========== STEP 3: loading resume URL ==========");
      SessionDiagnostics.logLoadRequest("STEP3", fullUrl);
      SessionDiagnostics.logCookiesForBase("STEP3 (before loadUrl)", odooBase);
    }
    webView.loadUrl(fullUrl);
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
    prefs.clearLoginState();
    CookieManager.getInstance()
        .removeAllCookies(
            value -> {
              CookieManager.getInstance().flush();
              runOnUiThread(this::showOnboarding);
            });
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

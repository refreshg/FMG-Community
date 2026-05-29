package ge.fmg.community;

import android.Manifest;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.webkit.WebResourceRequest;
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
  private static final int NAV_INACTIVE = Color.parseColor("#888888");
  private static final int NAV_ACTIVE = Color.parseColor("#E63946");
  private enum NavTab { BALANCE, TICKET, CREATE, OBJECTS, PROFILE }

  private AppPrefs prefs;
  private String odooBase;
  private String cachedUserDisplayName;
  private NavTab activeNavTab = NavTab.BALANCE;

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
  private View navBalance;
  private View navTicket;
  private View navObjects;
  private View navProfile;
  private ImageView navBalanceIcon;
  private ImageView navTicketIcon;
  private ImageView navObjectsIcon;
  private ImageView navProfileIcon;
  private TextView navBalanceLabel;
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

  private View legalOverlay;
  private TextView legalTitle;
  private View legalClose;
  private WebView legalContentWebView;
  private boolean legalOpen;

  private boolean loggingOut;
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

    WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    setContentView(R.layout.activity_main);
    configureSystemBars();

    prefs = new AppPrefs(this);
    odooBase = prefs.getServer();

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

    headerContainer = findViewById(R.id.header_container);
    navBalance = findViewById(R.id.nav_balance);
    navTicket = findViewById(R.id.nav_ticket);
    navObjects = findViewById(R.id.nav_objects);
    navProfile = findViewById(R.id.nav_profile);
    navBalanceIcon = findViewById(R.id.nav_balance_icon);
    navTicketIcon = findViewById(R.id.nav_ticket_icon);
    navObjectsIcon = findViewById(R.id.nav_objects_icon);
    navProfileIcon = findViewById(R.id.nav_profile_icon);
    navBalanceLabel = findViewById(R.id.nav_balance_label);
    navTicketLabel = findViewById(R.id.nav_ticket_label);
    navObjectsLabel = findViewById(R.id.nav_objects_label);
    navProfileLabel = findViewById(R.id.nav_profile_label);

    View menuRoot = findViewById(R.id.menu_overlay);
    menuOverlay = menuRoot;
    if (menuRoot != null) {
      menuBackdrop = menuRoot.findViewById(R.id.menu_backdrop);
      menuPanel = menuRoot.findViewById(R.id.menu_panel);
      menuProfileSubname = menuRoot.findViewById(R.id.menu_profile_subname);
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
    WindowInsetsControllerCompat controller =
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
    if (controller != null) {
      controller.setAppearanceLightStatusBars(true);
    }
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

  private void setupHeader() {
    View homeIcon = findViewById(R.id.header_home_icon);
    View homeTitle = findViewById(R.id.header_home_title);
    View.OnClickListener goHome = v -> navigateHeaderHome();
    if (homeIcon != null) {
      homeIcon.setOnClickListener(goHome);
    }
    if (homeTitle != null) {
      homeTitle.setOnClickListener(goHome);
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
    findViewById(R.id.nav_create).setOnClickListener(v -> navigateTo(NavTab.CREATE));
    navBalance.setOnClickListener(v -> navigateTo(NavTab.BALANCE));
    navTicket.setOnClickListener(v -> navigateTo(NavTab.TICKET));
    navObjects.setOnClickListener(v -> navigateTo(NavTab.OBJECTS));
    navProfile.setOnClickListener(v -> navigateTo(NavTab.PROFILE));
    setActiveNavTab(NavTab.BALANCE);
  }

  private void navigateTo(NavTab tab) {
    if (legalOpen) {
      hideLegalPage();
    }
    if (tab == NavTab.PROFILE) {
      setActiveNavTab(NavTab.PROFILE);
      showMenu();
      return;
    }
    hideMenu();
    setActiveNavTab(tab);
    switch (tab) {
      case BALANCE:
        loadOdooPath(NavUrls.BALANCE);
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
    applyNavItemStyle(navBalanceIcon, navBalanceLabel, tab == NavTab.BALANCE);
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
    View menuRoot = menuOverlay != null ? menuOverlay : findViewById(R.id.menu_overlay);
    if (menuRoot == null) return;

    bindMenuRow(menuRoot.findViewById(R.id.menu_item_news), R.drawable.ic_menu_news, R.string.menu_news);
    bindMenuRow(menuRoot.findViewById(R.id.menu_item_offers), R.drawable.ic_menu_gift, R.string.menu_offers);
    bindMenuRow(
        menuRoot.findViewById(R.id.menu_item_how_it_works),
        R.drawable.ic_menu_help,
        R.string.menu_how_it_works);

    if (menuBackdrop != null) {
      menuBackdrop.setOnClickListener(v -> hideMenu());
    }

    menuRoot.findViewById(R.id.menu_item_profile).setOnClickListener(v -> openMenuPath(NavUrls.PROFILE));
    menuRoot.findViewById(R.id.menu_item_news)
        .setOnClickListener(v -> openMenuPath(NavUrls.NOTIFICATIONS));
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
    if (activeNavTab == NavTab.PROFILE) {
      setActiveNavTab(NavTab.BALANCE);
      loadOdooPath(NavUrls.BALANCE);
      return;
    }
    navigateTo(activeNavTab);
  }

  private void showMenu() {
    if (menuOpen || menuAnimating) return;
    if (legalOpen) {
      hideLegalPage();
    }
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
    applyMenuProfileSubname(cachedUserDisplayName);
    fetchProfileDisplayName();
  }

  private void applyMenuProfileSubname(String name) {
    if (menuProfileSubname == null) return;
    if (isValidDisplayName(name)) {
      menuProfileSubname.setText(name.trim());
      menuProfileSubname.setVisibility(View.VISIBLE);
    } else {
      menuProfileSubname.setText("");
      menuProfileSubname.setVisibility(View.GONE);
    }
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
            if (menuOpen) {
              runOnUiThread(() -> applyMenuProfileSubname(name));
            }
          }
        });
  }

  private void fetchProfileDisplayName() {
    updateCachedUserNameFromSession();
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
    loginScreen.setVisibility(View.GONE);
    hideMenu();
    mainContainer.setVisibility(View.VISIBLE);
    setActiveNavTab(NavTab.BALANCE);
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

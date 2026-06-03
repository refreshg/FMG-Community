// FMG Community — Capacitor shell (iOS). Android uses native MainActivity with matching UI.
// Preference keys match AppPrefs.java / @capacitor/preferences.

const DEFAULT_SERVER = "https://fmggeo-araa-19679928.dev.odoo.com";

const PREF_SERVER_KEY = "odooServer";
const PREF_ONBOARDING_KEY = "onboardingSeen";
const PREF_LOGGED_IN_KEY = "loggedIn";
const PREF_PUSH_KEY = "pushEnabled";
const PREF_BIO_KEY = "bioEnabled";

/** Odoo paths — match NavUrls.java */
const ROUTES = {
  HOME: "/mtavari",
  PROPERTY: "/qoneba",
  SERVICE: "/servisi",
  PROFILE: "/profili",
  FINANCE: "/finansebi",
  COMMUNITY: "/community",
  MENU: "/menu",
  NEWS: "/news",
  SUGGESTION: "/shetavazeba",
  HOW_IT_WORKS: "/how",
  LOGIN: "/web/login",
  LOGOUT: "/web/session/logout",
};

const HEADER_HEIGHT_PX = 70;
const TABBAR_HEIGHT_PX = 134;

const NAV_PATH_BY_KEY = {
  home: ROUTES.HOME,
  property: ROUTES.PROPERTY,
  service: ROUTES.SERVICE,
  profile: ROUTES.PROFILE,
  finance: ROUTES.FINANCE,
  community: ROUTES.COMMUNITY,
  menu: ROUTES.MENU,
};

/** Called from iOS NativeTabBarTouchLayer (UIKit hit targets above WKWebView). */
window.__fmgNav = {
  menu() {
    return openMenuTab();
  },
  tab(key) {
    const path = NAV_PATH_BY_KEY[key];
    if (!path) return Promise.resolve();
    if (key === "menu") return openMenuTab();
    return navigateTo(key, path);
  },
};

async function setNativeTabBarEnabled(enabled) {
  const odoo = getOdooWebViewPlugin();
  if (odoo?.setTabBarTouchEnabled) {
    await odoo.setTabBarTouchEnabled({ enabled });
  }
}

async function setNativeLoginTouchEnabled(enabled) {
  const odoo = getOdooWebViewPlugin();
  if (odoo?.setLoginTouchEnabled) {
    await odoo.setLoginTouchEnabled({ enabled });
  }
}

async function setNativeOnboardingTouchEnabled(enabled) {
  const odoo = getOdooWebViewPlugin();
  if (odoo?.setOnboardingTouchEnabled) {
    await odoo.setOnboardingTouchEnabled({ enabled });
  }
}

/** Called from iOS NativeLoginTouchLayer */
window.__fmgLogin = {
  submit() {
    return submitLogin();
  },
};

window.__fmgOnboarding = {
  goTo(index) {
    onboardingIndex = index;
    renderOnboardingSlide();
  },
  next() {
    return onboardingNext();
  },
  skip() {
    return completeOnboarding();
  },
  continue() {
    return onboardingNext();
  },
};

window.__fmgLegal = {
  closed() {
    legalOpen = false;
  },
};

function useNativeMenuOverlay() {
  return (
    window.Capacitor?.getPlatform?.() === "ios" &&
    typeof getOdooWebViewPlugin()?.showMenuOverlay === "function"
  );
}

function useNativeLegalOverlay() {
  return (
    window.Capacitor?.getPlatform?.() === "ios" &&
    typeof getOdooWebViewPlugin()?.showLegalOverlay === "function"
  );
}

/** Menu row actions from iOS NativeMenuOverlayView */
window.__fmgMenuAction = function (action, payload) {
  switch (action) {
    case "closed":
      menuOpen = false;
      return;
    case "profile":
      openMenuPath(ROUTES.PROFILE);
      return;
    case "news":
      openMenuPath(ROUTES.NEWS);
      return;
    case "offers":
      openMenuPath(ROUTES.SUGGESTION);
      return;
    case "how":
      openMenuPath(ROUTES.HOW_IT_WORKS);
      return;
    case "privacy":
      showLegalPage("privacy").catch(console.error);
      return;
    case "terms":
      showLegalPage("terms").catch(console.error);
      return;
    case "logout":
      logout().catch(console.error);
      return;
    case "server-save": {
      const normalized = normalizeServer(payload || "");
      if (!normalized) {
        toast("სერვერის მისამართი არასწორია");
        return;
      }
      (async () => {
        ODOO_BASE = normalized;
        await prefSet(PREF_SERVER_KEY, ODOO_BASE);
        const loginInput = $("login-server");
        if (loginInput) loginInput.value = ODOO_BASE;
        await hideMenu();
        setActiveNav("home");
        await loadOdooPath(ROUTES.HOME);
      })().catch(console.error);
      return;
    }
    default:
      break;
  }
};

const SUPPORT_PHONE = "0322470600";

const ONBOARDING_SLIDES = [
  {
    icon: "🎫",
    title: "გახსენი ბილეთი",
    body: "აცნობე ნებისმიერი დაზიანების შესახებ — სადარბაზო, ლიფტი, ეზო. გადაიღე ფოტო და თვალი ადევნე სტატუსს.",
  },
  {
    icon: "💰",
    title: "ნახე ბალანსი ერთი კლიკით",
    body: "ლიფტი, კომუნალური, საერთო საფასური — ყველა გადასახადი ერთ ადგილას. გადახდის ისტორიაც აქვე ჩანს.",
  },
  {
    icon: "🔔",
    title: "შეტყობინებები რომ არ გამოგრჩეს",
    body: "ახალი ცნობები, ტიკეტის სტატუსი, გადასახადის შეხსენებები — ყველაფერი პირდაპირ ტელეფონზე.",
  },
];

let onboardingIndex = 0;
let ODOO_BASE = DEFAULT_SERVER;
let activeNavKey = "home";
let menuOpen = false;
let legalOpen = false;

/** Bundled legal HTML (www/legal/) */
const LEGAL = {
  PRIVACY: "legal/privacy.html",
  TERMS: "legal/terms.html",
};

function normalizeServer(input) {
  let s = (input || "").trim();
  if (!s) return null;
  if (!/^https?:\/\//i.test(s)) s = "https://" + s;
  try {
    const u = new URL(s);
    if (u.protocol !== "https:" && u.protocol !== "http:") return null;
    return u.origin.replace(/\/$/, "");
  } catch {
    return null;
  }
}

function odooUrl(path) {
  const base = ODOO_BASE.endsWith("/") ? ODOO_BASE.slice(0, -1) : ODOO_BASE;
  return base + path;
}

function isNativePlatform() {
  return !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
}

function getPreferencesPlugin() {
  if (!isNativePlatform()) return null;
  return window.Capacitor.Plugins?.Preferences || null;
}

function getOdooWebViewPlugin() {
  if (!isNativePlatform()) return null;
  if (window.Capacitor.Plugins?.OdooWebView) {
    return window.Capacitor.Plugins.OdooWebView;
  }
  if (typeof window.Capacitor.registerPlugin === "function") {
    return window.Capacitor.registerPlugin("OdooWebView");
  }
  return null;
}

async function prefGet(key) {
  const prefs = getPreferencesPlugin();
  if (!prefs) return null;
  const result = await prefs.get({ key });
  return result?.value ?? null;
}

async function prefSet(key, value) {
  const prefs = getPreferencesPlugin();
  if (!prefs) return;
  await prefs.set({ key, value: String(value) });
}

function $(id) {
  return document.getElementById(id);
}

function show(el) {
  el?.classList.remove("hidden");
}

function hide(el) {
  el?.classList.add("hidden");
}

let tapActionLock = 0;

function runTapAction(fn) {
  const now = Date.now();
  if (now - tapActionLock < 400) return;
  tapActionLock = now;
  fn();
}

/** iOS WKWebView-safe tap: real buttons + touchend capture + click fallback */
function bindTap(el, handler) {
  if (!el) return;
  el.style.touchAction = "manipulation";
  el.style.cursor = "pointer";

  const fire = (e) => {
    if (e?.cancelable) e.preventDefault();
    e?.stopPropagation?.();
    runTapAction(() => handler(e));
  };

  if (el.tagName === "BUTTON") {
    el.onclick = (e) => fire(e);
  }

  el.addEventListener(
    "touchend",
    (e) => fire(e),
    { passive: false, capture: true }
  );

  el.addEventListener("click", (e) => fire(e), { capture: true });
}

/** Event delegation for a container (bottom nav, menu list) */
function bindTapDelegate(container, selector, handler) {
  if (!container) return;
  container.style.touchAction = "manipulation";
  container.style.cursor = "default";

  const onInteract = (e) => {
    const target = e.target.closest?.(selector);
    if (!target || !container.contains(target)) return;
    if (e.cancelable) e.preventDefault();
    e.stopPropagation();
    runTapAction(() => handler(target, e));
  };

  container.addEventListener("touchend", onInteract, { passive: false, capture: true });
  container.addEventListener("click", onInteract, { capture: true });
}

function toast(message) {
  console.warn(message);
  alert(message);
}

async function initOdooWebView() {
  const odoo = getOdooWebViewPlugin();
  if (!odoo) return null;
  await odoo.initialize({
    headerHeightPx: HEADER_HEIGHT_PX,
    tabbarHeightPx: TABBAR_HEIGHT_PX,
  });
  return odoo;
}

async function loadOdooPath(path) {
  const url = odooUrl(path);
  const odoo = getOdooWebViewPlugin();
  if (odoo) {
    show($("loader"));
    hide($("offline"));
    await odoo.setVisible({ visible: true });
    await odoo.loadUrl({ url });
    return;
  }
  window.location.href = url;
}

function setActiveNav(key) {
  activeNavKey = key;
  document.querySelectorAll("#tabbar .tab, #tabbar .tab-fab").forEach((el) => {
    el.classList.toggle("active", el.dataset.key === key);
  });
}

async function navigateTo(key, path) {
  hideMenu();
  hideLegalPage();
  setActiveNav(key);
  await loadOdooPath(path);
}

async function showMenu() {
  if (menuOpen) return;
  hideLegalPage();
  if (useNativeMenuOverlay()) {
    menuOpen = true;
    const odoo = getOdooWebViewPlugin();
    await odoo.showMenuOverlay({ serverUrl: ODOO_BASE });
    return;
  }
  const overlay = $("menu-overlay");
  if (!overlay) return;
  hide($("menu-server-editor"));
  const input = $("menu-server-input");
  if (input) input.value = ODOO_BASE;
  overlay.classList.remove("hidden");
  requestAnimationFrame(() => overlay.classList.add("open"));
  menuOpen = true;
}

async function hideMenu() {
  if (useNativeMenuOverlay()) {
    if (!menuOpen) return;
    menuOpen = false;
    const odoo = getOdooWebViewPlugin();
    await odoo.hideMenuOverlay();
    return;
  }
  const overlay = $("menu-overlay");
  if (!overlay || !menuOpen) {
    overlay?.classList.add("hidden");
    overlay?.classList.remove("open");
    menuOpen = false;
    return;
  }
  overlay.classList.remove("open");
  menuOpen = false;
  setTimeout(() => {
    if (!menuOpen) overlay.classList.add("hidden");
  }, 260);
}

async function openMenuTab() {
  setActiveNav("menu");
  await showMenu();
  await loadOdooPath(ROUTES.MENU);
}

function openMenuPath(path) {
  const key = path === ROUTES.PROFILE ? "profile" : activeNavKey;
  hideMenu()
    .then(() => navigateTo(key, path))
    .catch(console.error);
}

async function showLegalPage(which) {
  await hideMenu();
  const overlay = $("legal-overlay");
  const frame = $("legal-frame");
  const title = $("legal-title");

  legalOpen = true;

  if (useNativeLegalOverlay()) {
    const odoo = getOdooWebViewPlugin();
    await odoo.showLegalOverlay({ which });
    return;
  }

  if (!overlay || !frame || !title) return;
  const isTerms = which === "terms";
  title.textContent = isTerms ? "წესები და პირობები" : "კონფიდენციალურობის პოლიტიკა";
  frame.src = new URL(isTerms ? LEGAL.TERMS : LEGAL.PRIVACY, window.location.href).href;
  overlay.classList.remove("hidden");
}

async function hideLegalPage() {
  if (!legalOpen) return;
  legalOpen = false;

  if (useNativeLegalOverlay()) {
    const odoo = getOdooWebViewPlugin();
    await odoo.hideLegalOverlay();
    return;
  }

  const overlay = $("legal-overlay");
  const frame = $("legal-frame");
  overlay?.classList.add("hidden");
  if (frame) frame.src = "about:blank";
}

async function logout() {
  await hideMenu();
  await hideLegalPage();
  await loadOdooPath(ROUTES.LOGOUT);
  await prefSet(PREF_LOGGED_IN_KEY, "false");
  await prefSet(PREF_ONBOARDING_KEY, "false");
  const odoo = getOdooWebViewPlugin();
  if (odoo) await odoo.setVisible({ visible: false });
  await showOnboarding();
}

async function showLoginScreen() {
  setNativeTabBarEnabled(false);
  hide($("onboarding"));
  hide($("app-header"));
  hide($("content"));
  hide($("tabbar"));
  hideMenu();
  show($("login-screen"));
  const odoo = getOdooWebViewPlugin();
  if (odoo) odoo.setVisible({ visible: false }).catch(() => {});
  await setNativeLoginTouchEnabled(true);
}

async function showOnboarding() {
  setNativeTabBarEnabled(false);
  await setNativeLoginTouchEnabled(false);
  hide($("login-screen"));
  hide($("app-header"));
  hide($("content"));
  hide($("tabbar"));
  hideMenu();
  resetOnboardingSlides();
  await setNativeOnboardingTouchEnabled(true);
  show($("onboarding"));
}

async function showMainShell() {
  await setNativeLoginTouchEnabled(false);
  await setNativeOnboardingTouchEnabled(false);
  hide($("login-screen"));
  hide($("onboarding"));
  show($("app-header"));
  show($("content"));
  show($("tabbar"));
  await setNativeTabBarEnabled(true);
}

async function submitLogin() {
  const input = $("login-server");
  const normalized = normalizeServer(input?.value);
  if (!normalized) {
    toast("სერვერის მისამართი არასწორია");
    return;
  }

  ODOO_BASE = normalized;
  await prefSet(PREF_SERVER_KEY, ODOO_BASE);
  if (input) input.blur();

  await showMainShell();
  await initOdooWebView();
  setActiveNav("home");
  await loadOdooPath(ROUTES.LOGIN);
}

async function completeOnboarding() {
  await prefSet(PREF_ONBOARDING_KEY, "true");
  await showLoginScreen();
  const saved = await prefGet(PREF_SERVER_KEY);
  const input = $("login-server");
  if (input) input.value = saved || DEFAULT_SERVER;
}

async function routeStartup() {
  ODOO_BASE = (await prefGet(PREF_SERVER_KEY)) || DEFAULT_SERVER;
  let loggedIn = (await prefGet(PREF_LOGGED_IN_KEY)) === "true";
  let onboardingSeen = (await prefGet(PREF_ONBOARDING_KEY)) === "true";

  // Match Android MainActivity: stale onboardingSeen without loggedIn
  if (!loggedIn && onboardingSeen) {
    await prefSet(PREF_ONBOARDING_KEY, "false");
    onboardingSeen = false;
  }

  const loginInput = $("login-server");
  if (loginInput) loginInput.value = ODOO_BASE;

  if (loggedIn && ODOO_BASE) {
    await showMainShell();
    await initOdooWebView();
    setActiveNav("home");
    await loadOdooPath(ROUTES.HOME);
    return;
  }

  if (!loggedIn && !onboardingSeen) {
    await showOnboarding();
    return;
  }

  await showLoginScreen();
}

window.submitLogin = submitLogin;

function setupLoginUi() {
  bindTap($("login-btn"), () => {
    submitLogin().catch((err) => {
      console.error("login failed", err);
      toast("შესვლა ვერ მოხერხდა");
    });
  });

  $("login-server")?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      submitLogin().catch((err) => console.error("login failed", err));
    }
  });

  bindTap($("retry-btn"), () => {
    loadOdooPath(ROUTES.LOGIN).catch(console.error);
  });
}

function setupOnboardingUi() {
  bindTap($("ob-skip"), () => {
    completeOnboarding().catch((err) => console.error("onboarding failed", err));
  });
  bindTap($("ob-next"), () => {
    onboardingNext().catch((err) => console.error("onboarding failed", err));
  });
}

function dialSupportPhone() {
  const odoo = window.Capacitor?.Plugins?.OdooWebView;
  if (odoo?.dialSupportPhone) {
    odoo.dialSupportPhone().catch(() => {
      window.location.href = `tel:${SUPPORT_PHONE}`;
    });
    return;
  }
  window.location.href = `tel:${SUPPORT_PHONE}`;
}

function renderOnboardingSlide() {
  const slide = ONBOARDING_SLIDES[onboardingIndex];
  if (!slide) return;

  const icon = $("ob-icon");
  const title = $("ob-title");
  const body = $("ob-body");
  const nextBtn = $("ob-next");
  const dots = $("ob-dots")?.querySelectorAll(".ob-dot");

  if (icon) icon.textContent = slide.icon;
  if (title) title.textContent = slide.title;
  if (body) body.textContent = slide.body;
  if (nextBtn) {
    nextBtn.textContent =
      onboardingIndex === ONBOARDING_SLIDES.length - 1
        ? "გადადით აპლიკაციაში"
        : "შემდეგი";
  }
  dots?.forEach((dot, i) => {
    dot.classList.toggle("active", i === onboardingIndex);
  });
}

function resetOnboardingSlides() {
  onboardingIndex = 0;
  renderOnboardingSlide();
}

window.renderOnboardingSlide = renderOnboardingSlide;
window.onboardingNext = onboardingNext;
window.completeOnboarding = completeOnboarding;

function onboardingNext() {
  if (onboardingIndex < ONBOARDING_SLIDES.length - 1) {
    onboardingIndex += 1;
    renderOnboardingSlide();
    return Promise.resolve();
  }
  return completeOnboarding();
}

function setupHeaderUi() {
  bindTap($("header-logo-pill"), () => {
    navigateTo("home", ROUTES.HOME).catch(console.error);
  });
  bindTap($("header-phone-btn"), dialSupportPhone);
}

function handleNavButton(btn) {
  const key = btn.dataset.key;
  const path = btn.dataset.path;
  if (!key || !path) return;
  if (key === "menu") {
    openMenuTab().catch(console.error);
    return;
  }
  navigateTo(key, path).catch(console.error);
}

function setupBottomNav() {
  const tabbar = $("tabbar");
  if (!tabbar) return;

  tabbar.querySelectorAll("button[data-key]").forEach((btn) => {
    btn.style.cursor = "pointer";
    btn.style.touchAction = "manipulation";
  });

  bindTapDelegate(tabbar, "button[data-key]", (btn) => handleNavButton(btn));
}

function setupMenuUi() {
  const overlay = $("menu-overlay");
  bindTap($("menu-backdrop"), hideMenu);

  bindTapDelegate(overlay, ".menu-row[data-path]", (row) => openMenuPath(row.dataset.path));

  bindTap($("menu-item-server"), () => {
    const editor = $("menu-server-editor");
    const input = $("menu-server-input");
    if (!editor) return;
    const showEditor = editor.classList.contains("hidden");
    if (showEditor) {
      if (input) input.value = ODOO_BASE;
      show(editor);
      input?.focus();
    } else {
      hide(editor);
    }
  });

  bindTap($("menu-server-save"), async () => {
    const input = $("menu-server-input");
    const normalized = normalizeServer(input?.value);
    if (!normalized) {
      toast("სერვერის მისამართი არასწორია");
      return;
    }
    ODOO_BASE = normalized;
    await prefSet(PREF_SERVER_KEY, ODOO_BASE);
    const loginInput = $("login-server");
    if (loginInput) loginInput.value = ODOO_BASE;
    hide($("menu-server-editor"));
    hideMenu();
    setActiveNav("home");
    await loadOdooPath(ROUTES.HOME);
  });

  bindTapDelegate(overlay, "#menu-item-privacy", () => showLegalPage("privacy"));
  bindTapDelegate(overlay, "#menu-item-terms", () => showLegalPage("terms"));
  bindTap($("menu-item-logout"), () => logout().catch(console.error));
}

function setupLegalUi() {
  bindTap($("legal-close"), hideLegalPage);
}

function setupOdooListeners() {
  const odoo = getOdooWebViewPlugin();
  if (!odoo || typeof odoo.addListener !== "function") return;

  odoo.addListener("portalSession", async () => {
    hide($("loader"));
    await prefSet(PREF_LOGGED_IN_KEY, "true");
    await prefSet(PREF_ONBOARDING_KEY, "true");
  });

  odoo.addListener("pageLoaded", (ev) => {
    hide($("loader"));
    hide($("offline"));
    const url = ev?.url || "";
    if (url.includes("/my/")) {
      prefSet(PREF_LOGGED_IN_KEY, "true");
      prefSet(PREF_ONBOARDING_KEY, "true");
    }
  });

  odoo.addListener("pageError", (ev) => {
    hide($("loader"));
    console.error("Odoo WebView error", ev);
    show($("offline"));
  });
}

window.addEventListener("DOMContentLoaded", () => {
  setupLoginUi();
  setupOnboardingUi();
  setupHeaderUi();
  setupBottomNav();
  setupMenuUi();
  setupLegalUi();
  setupOdooListeners();

  const boot = () => {
    routeStartup().catch((err) => {
      console.error("startup failed", err);
      showLoginScreen();
    });
  };

  if (window.Capacitor) {
    boot();
  } else {
    document.addEventListener("capacitorReady", boot, { once: true });
    setTimeout(boot, 500);
  }
});

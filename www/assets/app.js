// FMG Community — Capacitor shell (legacy). Native Android flow uses MainActivity.
// Preference keys match AppPrefs.java / @capacitor/preferences.

const DEFAULT_SERVER = "https://fmggeo-araa-19679928.dev.odoo.com";

const PREF_SERVER_KEY = "odooServer";
const PREF_PUSH_KEY = "pushEnabled";
const PREF_BIO_KEY = "bioEnabled";

/** Relative Odoo portal paths — compose with ODOO_BASE at runtime. */
const ROUTES = {
  HOME: "/en/my/home",
  TICKETS: "/en/my/home/tickets",
  CREATE_TICKET: "/en/my/home/tickets/create",
  REAL_ESTATE: "/en/my/home/real/estate",
  PROFILE: "/en/my/home/personal/info",
  NOTIFICATIONS: "/en/my/home/notifications",
  SUGGESTION: "/en/my/home/suggestion",
  HOW_IT_WORKS: "/en/my/home/howtowork",
  LOGIN: "/web/login",
  LOGOUT: "/web/session/logout",
};

let ODOO_BASE = DEFAULT_SERVER;

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

function openExternal(url) {
  if (window.Capacitor?.Plugins?.Browser) {
    window.Capacitor.Plugins.Browser.open({ url });
  } else {
    window.open(url, "_blank");
  }
}

window.addEventListener("DOMContentLoaded", () => {
  console.log("app.js loaded (Capacitor shell; native app uses MainActivity)");
});

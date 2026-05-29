// FMG Community — Capacitor shell (legacy). Native Android flow uses MainActivity.
// Preference keys match AppPrefs.java / @capacitor/preferences.

const DEFAULT_SERVER = "https://fmggeo-araa-19679928.dev.odoo.com";

const PREF_SERVER_KEY = "odooServer";
const PREF_PUSH_KEY = "pushEnabled";
const PREF_BIO_KEY = "bioEnabled";

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

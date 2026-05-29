# სამეზობლო — Android ჩარჩო (Capacitor)

ჰიბრიდული აპლიკაცია: native bottom tab bar + Odoo WebView კონტენტი.

## სტრუქტურა
```
sazomi-app/
├── capacitor.config.json   # Capacitor კონფიგი
├── package.json
└── www/                    # ლოკალური native shell
    ├── index.html          # tab bar, profile, onboarding
    └── assets/
        ├── style.css
        └── app.js          # routing + native plugins
```

shell ლოკალურია, Odoo კი iframe-ში remote-ად იტვირთება.
სანამ ააწყობ — `app.js`-ში შეცვალე `ODOO_BASE` შენი დომენით.

---

## ნაბიჯ-ნაბიჯ აწყობა

### 0. წინაპირობა
დააინსტალირე:
- Node.js LTS
- Android Studio (SDK + emulator)
- JDK 17

### 1. dependencies
```bash
cd sazomi-app
npm install
```

### 2. Capacitor init (პირველად)
თუ `android/` ფოლდერი ჯერ არ გაქვს:
```bash
npx cap add android
```

### 3. ვების კოპირება native პროექტში
```bash
npx cap sync
```

### 4. icon + splash
ჩასვი `resources/icon.png` (1024×1024) და `resources/splash.png` (2732×2732), მერე:
```bash
npx capacitor-assets generate --android
```

### 5. ODOO დომენისა და route-ების მითითება
გახსენი `www/assets/app.js` — ზედა CONFIG ბლოკში ერთ ადგილასაა:
```js
const ODOO_BASE = "https://fmggeo-araa-19679928.dev.odoo.com";
const ROUTES = {
  tickets: "/my/tickets",
  balance: "/my/balance",
  notifications: "/my/notifications",
  home: "/my/home",
};
```
- `ODOO_BASE` — production-ზე გადასვლისას მხოლოდ ეს ერთი ხაზი შეცვალე.
- `ROUTES` — შეცვალე შენი რეალური Odoo portal route-ებით.
  `/my/home` არსებობს default-ად; დანარჩენი custom მოდულის route-ებია.
ხელახლა გაუშვი `npx cap sync`.

### 6. Permissions
`android/app/src/main/AndroidManifest.xml`-ში `<manifest>` ტეგში:
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 7. Firebase (push)
1. Firebase Console → ახალი პროექტი → Add Android app (`ge.yourcompany.sazomi`)
2. ჩამოტვირთე `google-services.json` → ჩასვი `android/app/`-ში
3. Capacitor push docs-ის მიხედვით დაამატე Gradle plugin

### 8. გაშვება ტესტზე
```bash
npx cap open android
```
Android Studio → Run. შეამოწმე:
- tab bar ჩანს, გადართვა მუშაობს
- Odoo კონტენტი იტვირთება iframe-ში
- offline ეკრანი ჩანს ინტერნეტის გათიშვისას
- profile ეკრანი native-ია

### 9. Release AAB
```bash
keytool -genkey -v -keystore release.keystore \
  -alias sazomi -keyalg RSA -keysize 2048 -validity 10000
```
Android Studio → Build → Generate Signed Bundle → Android App Bundle (.aab)

### 10. Play Console
- Store listing, Privacy Policy URL
- Data safety: financial info + photos დაადეკლარირე
- Internal testing → Production

---

## Odoo backend მხარეს (push-ისთვის)
დაგჭირდება custom controller, რომელიც FCM token-ს მიიღებს:
`/api/register_device` (POST, JSON `{token}`).
ეს token-ი შეინახე `res.partner`-თან მიბმულ ცხრილში და FCM-ით გაუგზავნე
შეტყობინებები (ტიკეტის სტატუსი, გადასახადის შეხსენება).

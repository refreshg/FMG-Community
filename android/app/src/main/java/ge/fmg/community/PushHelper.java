package ge.fmg.community;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

/** FCM registration when google-services.json is present. */
public final class PushHelper {
  private static final String TAG = "PushHelper";

  private final ComponentActivity activity;
  private final AppPrefs prefs;
  private ActivityResultLauncher<String> notificationPermissionLauncher;

  public PushHelper(ComponentActivity activity, AppPrefs prefs) {
    this.activity = activity;
    this.prefs = prefs;
    notificationPermissionLauncher =
        activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
              if (granted) {
                registerFcmToken();
              } else {
                prefs.setPushEnabled(false);
                Toast.makeText(activity, "ნოტიფიკაციების ნებართვა არ მოცემულა", Toast.LENGTH_SHORT)
                    .show();
              }
            });
  }

  public boolean isFirebaseAvailable() {
    try {
      return !FirebaseApp.getApps(activity).isEmpty();
    } catch (Throwable t) {
      return false;
    }
  }

  /** Called when user enables push in settings. */
  public void enablePush() {
    if (!isFirebaseAvailable()) {
      prefs.setPushEnabled(false);
      Toast.makeText(
              activity,
              "დაამატე android/app/google-services.json და გადააგრო აპი",
              Toast.LENGTH_LONG)
          .show();
      return;
    }
    if (Build.VERSION.SDK_INT >= 33) {
      if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
          == PackageManager.PERMISSION_GRANTED) {
        registerFcmToken();
      } else {
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
      }
    } else {
      registerFcmToken();
    }
  }

  public void disablePush() {
    try {
      FirebaseMessaging.getInstance().deleteToken();
    } catch (Throwable t) {
      Log.w(TAG, "deleteToken failed", t);
    }
  }

  private void registerFcmToken() {
    try {
      FirebaseMessaging.getInstance()
          .getToken()
          .addOnCompleteListener(
              task -> {
                if (!task.isSuccessful()) {
                  Log.w(TAG, "FCM token failed", task.getException());
                  return;
                }
                String token = task.getResult();
                Log.i(TAG, "FCM token: " + token);
                // POST token to Odoo when a backend endpoint is ready.
              });
    } catch (Throwable t) {
      Log.w(TAG, "registerFcmToken failed", t);
      prefs.setPushEnabled(false);
    }
  }
}

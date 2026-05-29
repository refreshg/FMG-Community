package ge.fmg.community;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/** Receives FCM messages when google-services.json is configured. */
public class FcmMessagingService extends FirebaseMessagingService {
  private static final String TAG = "FcmMessagingService";

  @Override
  public void onMessageReceived(RemoteMessage message) {
    Log.i(TAG, "Push from: " + message.getFrom());
  }

  @Override
  public void onNewToken(String token) {
    Log.i(TAG, "FCM token refreshed: " + token);
  }
}

package com.tapmedia.yoush.groups;

import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowManager;

import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.TextSecurePreferences;

import org.jitsi.meet.sdk.JitsiMeetActivity;

import java.util.HashMap;

public class GroupCallActivity extends JitsiMeetActivity {

    private static final String TAG = GroupCallActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        // PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        // PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
        //         "MyApp::MyWakelockTag");
        // wakeLock.acquire();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initializeScreenshotSecurity();
    }

    private void initializeScreenshotSecurity() {
    if (TextSecurePreferences.isScreenSecurityEnabled(this)) {
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    } else {
      getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
  }
//
//    @Override
//    protected void onConferenceTerminated(HashMap<String, Object> extraData) {
//        Log.d(TAG, "Conference terminated: " + extraData);
//        Log.d(TAG, "Conference terminated: " + extraData);
//    }
}
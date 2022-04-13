package com.tapmedia.yoush.groups;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.RestrictionEntry;
import android.content.RestrictionsManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

import org.jitsi.meet.sdk.BroadcastAction;
import org.jitsi.meet.sdk.BroadcastEvent;
import org.jitsi.meet.sdk.BroadcastIntentHelper;
import org.jitsi.meet.sdk.JitsiMeet;
import org.jitsi.meet.sdk.JitsiMeetActivity;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.ParticipantsService;
//import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.pin.PinRestoreActivity;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

public class JitsiService {

    private static final String TAG                       = JitsiService.class.getSimpleName();

    private static android.os.CountDownTimer count = null;

    private Window window;

    /**
     * The request code identifying requests for the permission to draw on top
     * of other apps. The value must be 16-bit and is arbitrarily chosen here.
     */
    private static final int OVERLAY_PERMISSION_REQUEST_CODE
            = (int) (Math.random() * Short.MAX_VALUE);

    /**
     * ServerURL configuration key for restriction configuration using {@link android.content.RestrictionsManager}
     */
    public static final String RESTRICTION_SERVER_URL = "SERVER_URL";

    /**
     * Broadcast receiver for restrictions handling
     */
//    private BroadcastReceiver broadcastReceiver;

    /**
     * Flag if configuration is provided by RestrictionManager
     */
    private boolean configurationByRestrictions = false;

    /**
     * Default URL as could be obtained from RestrictionManager
     */
    private String defaultURL;

    private static final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBroadcastReceived(context, intent);
        }
    };


    @SuppressLint("WrongConstant")
    public static void launch(Context context, JitsiMeetConferenceOptions options, boolean isReceiver, Window window) {

        if (count != null) {
            count.cancel();
            count = null;
        }

        if (!isReceiver) {
            count = new android.os.CountDownTimer(40000, 1000) {
                public void onTick(long millisUntilFinished) {
//      textic.setText("Time Left: " + millisUntilFinished / 1000);
                }
                public void onFinish() {
                    if (participantList.size() == 1) {
                        JitsiService obj = new JitsiService();
                        obj.hangUp(context, false);
//                ConversationActivity obj2 = new ConversationActivity();
//                obj2.checkDenyStartCall = true;
                    }
                }
            };

            count.start();
        }
        registerForBroadcastMessages(context);


//        JitsiMeetActivity.launch(context, options);

        Intent intent = new Intent(context, GroupCallActivity.class);
        intent.setAction("org.jitsi.meet.CONFERENCE");
        intent.putExtra("JitsiMeetConferenceOptions", options);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(intent);
    }

    private static void registerForBroadcastMessages(Context context) {
        IntentFilter intentFilter = new IntentFilter();

        /* This registers for every possible event sent from JitsiMeetSDK
           If only some of the events are needed, the for loop can be replaced
           with individual statements:
           ex:  intentFilter.addAction(BroadcastEvent.Type.AUDIO_MUTED_CHANGED.getAction());
                intentFilter.addAction(BroadcastEvent.Type.CONFERENCE_TERMINATED.getAction());
                ... other events
         */
        for (BroadcastEvent.Type type : BroadcastEvent.Type.values()) {
            intentFilter.addAction(type.getAction());
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(broadcastReceiver, intentFilter);
    }

    // Example for handling different JitsiMeetSDK events

    private static List<String> participantList = new ArrayList<>();
    private static int participantSizeMax = new Integer(0);
    private static String statusEvent = "";
//    private static Object hostLocal = new Object();

    public static ParticipantsService.ParticipantsInfoCallback retriveCallBack()  {
        return null;
    }

    private static void onBroadcastReceived(Context context, Intent intent) {
        if (intent != null) {
            BroadcastEvent event = new BroadcastEvent(intent);

            switch (event.getType()) {
                case CONFERENCE_JOINED:
                    getParticipantCount(intent,"CONFERENCE_JOINED");
                    break;
                case PARTICIPANT_JOINED:
                    getParticipantCount(intent,"PARTICIPANT_JOINED");
                    break;
                case CONFERENCE_TERMINATED:
                    getParticipantCount(intent,"CONFERENCE_TERMINATED");
                    break;
                case PARTICIPANT_LEFT:
                    getParticipantCount(intent,"PARTICIPANT_LEFT");
                    break;
                case READY_TO_CLOSE:
                    participantSizeMax = 0;
                    participantList = new ArrayList<>();
                    statusEvent = "";
                    break;

                case PARTICIPANTS_INFO_RETRIEVED:
                    participantList = Arrays.asList(event.getData().get("participantsInfo").toString().split("\\}, \\{", -1));
                    if (participantSizeMax < participantList.size()) {
                        participantSizeMax = participantList.size();
                    }

                    if (statusEvent.equals("PARTICIPANT_LEFT")) {
                        if (participantList.size() == 1) {
                            // DONE: local hangup
                            JitsiService obj = new JitsiService();
                            obj.hangUp(context, false);
                        }
                    }

                    if (statusEvent.equals("CONFERENCE_TERMINATED")) {
                        if (participantSizeMax == 1 && participantList.size() == 1) {
                            // DONE: host hangup
                            ConversationActivity obj2 = new ConversationActivity();
                            obj2.checkDenyStartCall = true;
                            participantSizeMax = 0;
                        }

                        if (participantSizeMax == 2 && participantList.size() == 1) { // IOS
                            JitsiService obj = new JitsiService();
                            obj.hangUp(context, false);
                        }
                    }


                    if (participantList.size() > 1) {
                        if (count != null) {
                            count.cancel();
                            count = null;
                        }
                        Intent     intent2     = new Intent(context, GroupCallBeginService.class);
                        intent2.setAction(GroupCallBeginService.ACTION_STOP_RING_OUTCALL);
                        context.startService(intent2);
                    }


                    System.out.println("-----------------");
                    System.out.println(participantList.size());
                    break;
            }
        }
    }

    private static void getParticipantCount(Intent intent, String eventName) {
        statusEvent = eventName;
        ParticipantsService service = ParticipantsService.getInstance();
        service.retrieveParticipantsInfo(retriveCallBack());
    }

    // Example for sending actions to JitsiMeetSDK
    public void hangUp(Context context,boolean isSenderCall) {

        if (context != null) {
            if (count != null) {
                count.cancel();
                count = null;
            }
           Intent     intent     = new Intent(context, GroupCallBeginService.class);
           intent.setAction(GroupCallBeginService.ACTION_LAST_ENDCALL);
           context.startService(intent);

//            if (isSenderCall) {
//                participantSizeMax = 0;
//            }

            Intent hangupBroadcastIntent = BroadcastIntentHelper.buildHangUpIntent();
            LocalBroadcastManager.getInstance(context).sendBroadcast(hangupBroadcastIntent);
        }
    }

    public void hostHangUp(Context context) {

        if (context != null) {
            if (count != null) {
                count.cancel();
                count = null;
            }
            Intent     intent     = new Intent(context, GroupCallBeginService.class);
            intent.setAction(GroupCallBeginService.ACTION_HOST_END_CALL);
            context.startService(intent);

            Intent hangupBroadcastIntent = BroadcastIntentHelper.buildHangUpIntent();
            LocalBroadcastManager.getInstance(context).sendBroadcast(hangupBroadcastIntent);

        }
    }
}



package com.tapmedia.yoush.groups;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;
import com.tapmedia.yoush.MainNavigator;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.RecipientDatabase.VibrateState;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.events.WebRtcViewModel;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.notifications.DoNotDisturbUtil;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.ringrtc.CallState;
import com.tapmedia.yoush.ringrtc.RemotePeer;
import com.tapmedia.yoush.service.KeyCachingService;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.webrtc.CallNotificationBuilder;
import com.tapmedia.yoush.webrtc.audio.OutgoingRinger;
import com.tapmedia.yoush.webrtc.audio.SignalAudioManager;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.tapmedia.yoush.webrtc.CallNotificationBuilder.TYPE_ESTABLISHED;
import static com.tapmedia.yoush.webrtc.CallNotificationBuilder.TYPE_INCOMING_RINGING;

public class GroupCallBeginService extends Service implements MainNavigator.BackHandler
{

  private static final String TAG = GroupCallBeginService.class.getSimpleName();

  public static final String ROOM_NAME                       = "room_name";
  public static final String GROUP_NAME                       = "group_name";
  public static final String SENDER_NAME                       = "sender_name";
  public static final String AUDIO_ONLY                       = "audio_only";
  public static final String MESSAGE_TYPE                       = "message_type";
  public static final String CALL_ID                       = "call_id";
  public static final String SUBJECT                       = "subject";

  public static final String ACTION_RECEIVE_OFFER                       = "RECEIVE_OFFER";
  public static final String ACTION_LOCAL_RINGING                       = "LOCAL_RINGING";
  public static final String ACTION_DENY_CALL                           = "DENY_CALL";
  public static final String ACTION_ANSWER_CALL                           = "ACTION_ANSWER_CALL";
  public static final String ACTION_OUTGOING_CALL                           = "ACTION_OUTGOING_CALL";
  public static final String ACTION_LOCAL_HANGUP                           = "ACTION_LOCAL_HANGUP";
  public static final String ACTION_STOP_RING_OUTCALL                          = "ACTION_STOP_RING_OUTCALL";
  public static final String ACTION_HOST_END_CALL                          = "ACTION_HOST_END_CALL";
  public static final String ACTION_DENY_CALL_BUTTON                          = "ACTION_DENY_CALL_BUTTON";
  public static final String ACTION_CHANGE_STATE                         = "ACTION_CHANGE_STATE";
  public static final String ACTION_LAST_ENDCALL                         = "ACTION_LAST_ENDCALL";

  public static final String EXTRA_REMOTE_PEER                = "remote_peer";

  private android.os.CountDownTimer count = new android.os.CountDownTimer(40000, 1000) {
    public void onTick(long millisUntilFinished) {
//      textic.setText("Time Left: " + millisUntilFinished / 1000);
    }
    public void onFinish() {
      handleDenyCall(null);
      sendEndCall();
    }
  };

  private final ExecutorService serviceExecutor = Executors.newSingleThreadExecutor();
  private SignalAudioManager              audioManager;

  @Nullable private RemotePeer          activePeer;
  private String                        roomName;
  private String                        groupName;
  private String                        senderName;
  private String                        audioOnly;
  private String                        messageType;
  private String                        callId;
  private String                        subject;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(final Intent intent, int flags, int startId) {
    if (intent == null || intent.getAction() == null) return START_NOT_STICKY;

    serviceExecutor.execute(() -> {
      if      (intent.getAction().equals(ACTION_RECEIVE_OFFER))                       handleReceivedOffer(intent);
      else if (intent.getAction().equals(ACTION_DENY_CALL))                           handleDenyCall(intent);
      else if (intent.getAction().equals(ACTION_ANSWER_CALL))                         handleAnswerCall(intent);
      else if (intent.getAction().equals(ACTION_OUTGOING_CALL))                       handleOutgoingCall(intent);
      else if (intent.getAction().equals(ACTION_LOCAL_HANGUP))                        handleHangup(intent);
      else if (intent.getAction().equals(ACTION_STOP_RING_OUTCALL))                   handleStopRingOutCall(intent);
      else if (intent.getAction().equals(ACTION_HOST_END_CALL))                       handleHostEndCall(intent);
      else if (intent.getAction().equals(ACTION_DENY_CALL_BUTTON))                    handleDenyCallButton(intent);
      else if (intent.getAction().equals(ACTION_CHANGE_STATE))                        handleChangeState(intent);
      else if (intent.getAction().equals(ACTION_LAST_ENDCALL))                        sendEndCall();
    });

    return START_NOT_STICKY;
  }

  private void handleChangeState(Intent intent) {
    count.cancel();
    RemotePeer        remotePeer                  = getRemotePeer(intent);

    if (activePeer != null && remotePeer != null) {
      String recipientIdNew = remotePeer.getId().toString();
      String recipientIdOld = activePeer.getId().toString();

      CallState callStateOld = activePeer.getState();

      boolean isNotIdle = (callStateOld == CallState.CONNECTED || callStateOld == CallState.DIALING || callStateOld == CallState.RECEIVED_BUSY || callStateOld == CallState.REMOTE_RINGING);

      if (recipientIdNew.equals(recipientIdOld)) {
        if (isNotIdle) {
          // deny ????
          activePeer.handleIdle();
          terminate(intent);

//          if (messageType.equals("call")) {
            JitsiService obj = new JitsiService();
            obj.hangUp(getApplicationContext(), true);
//          }
        } else {
          if (activePeer.getState() != CallState.DIALING && activePeer.getState() != CallState.TERMINATED) {
            DatabaseFactory.getSmsDatabase(this).insertMissedCall(activePeer.getId());
          }
          activePeer.handleIdle();
          terminate(intent);

          if (messageType.equals("call")) {
            JitsiService obj = new JitsiService();
            obj.hangUp(getApplicationContext(), true);
          } else if (messageType.equals("groupCall")) {
            Intent intentBroadcast = new Intent("CANCEL_JOIN_CALL_BROADCAST");
            intentBroadcast.setPackage(getApplicationContext().getPackageName());
            getApplicationContext().sendBroadcast(intentBroadcast, KeyCachingService.KEY_PERMISSION);
          }
        }
      } else {
        if (isNotIdle) {
          //deny
        } else {
          if (activePeer.getState() != CallState.DIALING && activePeer.getState() != CallState.TERMINATED) {
            DatabaseFactory.getSmsDatabase(this).insertMissedCall(activePeer.getId());
          }
          activePeer.handleIdle();
          terminate(intent);

          if (messageType.equals("call")) {
            JitsiService obj = new JitsiService();
            obj.hangUp(getApplicationContext(), true);
          }
        }
      }
    } else {
//      if (activePeer.getState() != CallState.DIALING && activePeer.getState() != CallState.TERMINATED) {
//        DatabaseFactory.getSmsDatabase(this).insertMissedCall(activePeer.getId());
//      }
      activePeer.handleIdle();
//      terminate(intent);
//
//      if (messageType.equals("call")) {
//        JitsiService obj = new JitsiService();
//        obj.hangUp(getApplicationContext(), true);
//      }
    }
  }

  private void handleHostEndCall(Intent intent) {

    // TODO: check for call group vs call 1-1

    activePeer.handleIdle();

    sendEndCall();
  }

  private void sendEndCall() {

    if (activePeer != null && roomName != null) {
      if (activePeer.getState() == CallState.DIALING) {
        activePeer.handleTerminated();

        // if ("groupCall".equals(messageType)) {
        Recipient recipient1 = Recipient.self();
        String callState = "endCall";
        String callerName = "";

        roomName = roomName.replaceAll("!","");
        String callObject = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+false+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";
        String message = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+false+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";

        OutgoingMediaMessage outgoingMessage =
                new OutgoingMediaMessage(activePeer.getRecipient(),
                        message,
                        Collections.emptyList(),
                        System.currentTimeMillis(),
                        -1,
                        activePeer.getRecipient().getExpireMessages() * 1000,
                        false,
                        ThreadDatabase.DistributionTypes.DEFAULT,
                        null,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(), "");

        MessageSender.sendCallGroup(this, outgoingMessage,callObject, -1, false, null);
        // }
      }
    }
  }

  private void handleStopRingOutCall(Intent intent) {
    audioManager.stop();
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state,
                           @NonNull RemotePeer            remotePeer)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state,
            remotePeer.getRecipient()));
  }

  private void sendMessage(@NonNull WebRtcViewModel.State state)
  {
    EventBus.getDefault().postSticky(new WebRtcViewModel(state,
            null));
  }

  private void handleHangup(Intent intent) {
    if (activePeer != null) {
      activePeer.handleIdle();
    }
    terminate(null);

    if ("groupCall".equals(messageType)) {
      Intent intentBroadcast = new Intent("CANCEL_JOIN_CALL_BROADCAST");
      intentBroadcast.setPackage(getApplicationContext().getPackageName());
      getApplicationContext().sendBroadcast(intentBroadcast, KeyCachingService.KEY_PERMISSION);
    }
  }

  private void handleOutgoingCall(Intent intent) {
    count.cancel();
    RemotePeer remotePeer = getRemotePeer(intent);

//    if (remotePeer.getState() != CallState.IDLE) {
//      throw new IllegalStateException("Dialing from non-idle?");
//    }

    remotePeer.dialing();
    activePeer = remotePeer;
  }

  private void handleAnswerCall(Intent intent) {
//    RemotePeer        remotePeer                  = getRemotePeer(intent);
//    remotePeer.handleIdle();
    audioManager.stop();
    activePeer.dialing();

    count.cancel();
    terminate(null);
    DatabaseFactory.getSmsDatabase(this).insertReceivedCall(activePeer.getId());
    JitsiMeetConferenceOptions options = null;
    try {
      Recipient recipient1 = Recipient.self();
      JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();

      String name = recipient1.getProfileName().toString();

      if (name != null && !name.isEmpty()) {
        userInfo.setDisplayName(name);
      } else {
        userInfo.setDisplayName(recipient1.getE164().get());
      }

      userInfo.setEmail(recipient1.getE164().get() + "@tapofthink.com");

      String jws = Util.getJitsiToken(name, recipient1.getE164().get() + "@tapofthink.com");

      String configOverride = "#config.disableAEC=false&config.p2p.enabled=false&config.disableNS=false";

      options = new JitsiMeetConferenceOptions.Builder()
              .setRoom(roomName)
              .setServerURL(new URL("" + configOverride))
              .setToken(jws)
              .setUserInfo(userInfo)
              .setVideoMuted(Boolean.parseBoolean(audioOnly))
//              .setWelcomePageEnabled(false)
              .setFeatureFlag("chat.enabled", false)
              .setFeatureFlag("add-people.enabled", false)
              .setFeatureFlag("invite.enabled", false)
              .setFeatureFlag("meeting-password.enabled", false)
              .setFeatureFlag("live-streaming.enabled", false)
              .setFeatureFlag("video-share.enabled", false)
              .setFeatureFlag("recording.enabled", false)
              .setFeatureFlag("call-integration.enabled", false)
              // .setConfigOverride("disableAEC", false)
              // .setConfigOverride("p2p.enabled", false)
              .build();
    } catch (MalformedURLException | UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    JitsiService.launch(this,options, true, null);
  }

  private void handleDenyCallButton(Intent intent) {
    audioManager.stop();
    if ("call".equals(messageType)) {
      String callState = "endCall";
      String          callObject        = "{\"messageType\":\"call\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\": \""+callState+"\", \"subject\": \""+subject+"\", \"callId\": \""+callId+"\", \"audioOnly\":\""+false+"\"}";
      String          message     = "{\"messageType\":\"call\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\": \""+callState+"\", \"subject\": \""+subject+"\", \"callId\": \""+callId+"\", \"audioOnly\":\""+false+"\"}";

      OutgoingMediaMessage outgoingMessage =
              new OutgoingMediaMessage(activePeer.getRecipient(),
                      message,
                      Collections.emptyList(),
                      System.currentTimeMillis(),
                      -1,
                      activePeer.getRecipient().getExpireMessages() * 1000,
                      false,
                      ThreadDatabase.DistributionTypes.DEFAULT,
                      null,
                      Collections.emptyList(),
                      Collections.emptyList(),
                      Collections.emptyList(),
                      Collections.emptyList(), "");

      MessageSender.sendCallGroup(this, outgoingMessage,callObject, -1, false, null);
    }

    handleDenyCall(intent);
  }

  private void handleDenyCall(Intent intent) {
    audioManager.stop();
    if (activePeer != null) {
      if (activePeer.getState() != CallState.TERMINATED) {
       DatabaseFactory.getSmsDatabase(this).insertMissedCall(activePeer.getId());
      }
      activePeer.handleTerminated();
    }
    count.cancel();

    terminate(intent);

    if ("groupCall".equals(messageType)) {
      Intent intentBroadcast = new Intent("CANCEL_JOIN_CALL_BROADCAST");
      intentBroadcast.setPackage(getApplicationContext().getPackageName());
      getApplicationContext().sendBroadcast(intentBroadcast, KeyCachingService.KEY_PERMISSION);
    }
  }


  private synchronized void terminate(Intent intent) {
    Log.i(TAG, "terminate()");

    if (activePeer == null) {
      Log.i(TAG, "terminate(): skipping with no active peer");
//      return;
    }


    sendMessage(WebRtcViewModel.State.CALL_DECLINED_ELSEWHERE);


    stopForeground(true);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "onCreate");

    initializeResources();
  }

  private void initializeResources() {
    this.audioManager          = new SignalAudioManager(this);

  }

  private void handleReceivedOffer(Intent intent) {

    messageType = intent.getStringExtra(MESSAGE_TYPE);

    RemotePeer        remotePeer                  = getRemotePeer(intent);
    audioOnly = intent.getStringExtra(AUDIO_ONLY);

    if (activePeer != null) {
      String recipientIdNew = remotePeer.getId().toString();
      String recipientIdOld = activePeer.getId().toString();

      CallState callStateNew = remotePeer.getState();
      CallState callStateOld = activePeer.getState();

      boolean isNotIdle = (callStateOld == CallState.CONNECTED     ||
              callStateOld == CallState.DIALING       ||
              callStateOld == CallState.RECEIVED_BUSY ||
              callStateOld == CallState.REMOTE_RINGING
//              || callStateOld == CallState.TERMINATED
      );

      if (recipientIdNew.equals(recipientIdOld)) {
        // cung group
        if (isNotIdle) {
          startForeground(CallNotificationBuilder.getNotificationIdCallGroup(),
                  CallNotificationBuilder.getCallInProgressNotificationCallGroup(this, TYPE_ESTABLISHED, remotePeer.getRecipient(),intent));
          stopForeground(true);
          count.cancel();
        } else {
          dialingCall(intent, remotePeer);
        }
      } else {
        // khac group
        // bao busy

        if (isNotIdle) {
          count.cancel();
          String callState = "busy";
          String          callObject        = "{\"messageType\":\"groupCall\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\": \""+callState+"\", \"callId\": \""+callId+"\", \"audioOnly\":\""+false+"\"}";
          String          message     = "{\"messageType\":\"groupCall\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\": \""+callState+"\", \"\": \""+callId+"\", \"audioOnly\":\""+false+"\"}";

          OutgoingMediaMessage outgoingMessage =
                  new OutgoingMediaMessage(remotePeer.getRecipient(),
                          message,
                          Collections.emptyList(),
                          System.currentTimeMillis(),
                          -1,
                          remotePeer.getRecipient().getExpireMessages() * 1000,
                          false,
                          ThreadDatabase.DistributionTypes.DEFAULT,
                          null,
                          Collections.emptyList(),
                          Collections.emptyList(),
                          Collections.emptyList(),
                          Collections.emptyList(), "");

          MessageSender.sendCallGroup(this, outgoingMessage,callObject, -1, false, null);

          startForeground(CallNotificationBuilder.getNotificationIdCallGroup(),
                  CallNotificationBuilder.getCallInProgressNotificationCallGroup(this, TYPE_ESTABLISHED, remotePeer.getRecipient(), intent));
          stopForeground(true);
        } else {
          dialingCall(intent, remotePeer);
        }


      }
    } else {
      dialingCall(intent, remotePeer);
    }
  }

  private void dialingCall (Intent intent, RemotePeer remotePeer) {

    Recipient  recipient  = remotePeer.getRecipient();
    Uri          ringtone     = recipient.resolve().getCallRingtone();
    VibrateState vibrateState = recipient.resolve().getCallVibrate();
    if (ringtone == null) ringtone = TextSecurePreferences.getCallNotificationRingtone(this);
    audioManager.startIncomingRinger(ringtone, vibrateState == VibrateState.ENABLED || (vibrateState == VibrateState.DEFAULT && TextSecurePreferences.isCallNotificationVibrateEnabled(this)));

    roomName = intent.getStringExtra(ROOM_NAME);
    groupName = intent.getStringExtra(GROUP_NAME);
    senderName = intent.getStringExtra(SENDER_NAME);
    activePeer = intent.getParcelableExtra(EXTRA_REMOTE_PEER);
    messageType = intent.getStringExtra(MESSAGE_TYPE);
    callId = intent.getStringExtra(CALL_ID);
    subject = intent.getStringExtra(SUBJECT);

    setCallInProgressNotification(TYPE_INCOMING_RINGING,remotePeer, intent);
    count.start();
  }

  private void setCallInProgressNotification(int type, RemotePeer remotePeer, Intent intent) {
    System.out.println("------------------------------------------------------------------------------------------");
    System.out.println(remotePeer);
    System.out.println(remotePeer.getRecipient());
    System.out.println("------------------------------------------------------------------------------------------");

    remotePeer.localRingingCallGroup();

//    sendMessage(WebRtcViewModel.State.CALL_INCOMING, activePeer, localCameraState, remoteVideoEnabled, bluetoothAvailable, microphoneEnabled, isRemoteVideoOffer);
    sendMessage(WebRtcViewModel.State.CALL_INCOMING,remotePeer);

    startCallCardActivityIfPossible();
    startForeground(CallNotificationBuilder.getNotificationIdCallGroup(),
              CallNotificationBuilder.getCallInProgressNotificationCallGroup(this, type, remotePeer.getRecipient(), intent));
  }

  private void startCallCardActivityIfPossible() {
    Intent activityIntent = new Intent();
    activityIntent.setClass(this, GroupCallBeginActivity.class);
    activityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(GroupCallBeginActivity.ROOM_NAME, roomName)
            .putExtra(GroupCallBeginActivity.GROUP_NAME, groupName)
            .putExtra(GroupCallBeginActivity.SENDER_NAME, senderName)
    ;
    this.startActivity(activityIntent);


  }

  private static @NonNull RemotePeer getRemotePeer(Intent intent) {
    RemotePeer remotePeer = intent.getParcelableExtra(EXTRA_REMOTE_PEER);
//    if (remotePeer == null) throw new AssertionError("No RemotePeer in intent!");
    if (remotePeer == null) return null;

    return remotePeer;
  }

  @Override
  public boolean onBackPressed() {
    return false;
  }
}

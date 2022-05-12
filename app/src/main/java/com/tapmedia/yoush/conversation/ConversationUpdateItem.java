package com.tapmedia.yoush.conversation;

import android.content.Context;
import android.content.Intent;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import org.jitsi.meet.sdk.JitsiMeetUserInfo;
import com.tapmedia.yoush.BindableConversationItem;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.VerifyIdentityActivity;
import com.tapmedia.yoush.components.ThumbnailView;
import com.tapmedia.yoush.conversation.background.BackgroundJobBinding;
import com.tapmedia.yoush.conversation.pin.PinJobBinding;
import com.tapmedia.yoush.database.IdentityDatabase.IdentityRecord;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.database.model.MessageRecord;
import com.tapmedia.yoush.groups.GroupCallBeginService;
import com.tapmedia.yoush.groups.JitsiService;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.GlideRequests;
import com.tapmedia.yoush.mms.OutgoingMediaMessage;
import com.tapmedia.yoush.recipients.LiveRecipient;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientForeverObserver;
import com.tapmedia.yoush.ringrtc.RemotePeer;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.DateUtils;
import com.tapmedia.yoush.util.ExpirationUtil;
import com.tapmedia.yoush.util.GroupUtil;
import com.tapmedia.yoush.util.IdentityUtil;
import com.tapmedia.yoush.util.ThemeUtil;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.concurrent.ListenableFuture;

import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConversationUpdateItem extends LinearLayout
    implements RecipientForeverObserver, BindableConversationItem
{
  private static final String TAG = ConversationUpdateItem.class.getSimpleName();

  private Set<MessageRecord> batchSelected;

  public ImageView     icon;
  public TextView      title;
  public TextView      body;
  public TextView      date;
  public LiveRecipient sender;
  public MessageRecord messageRecord;
  public Locale        locale;
  public Button        callBack;
  public ConversationUpdateItem(Context context) {
    super(context);
  }

  public ConversationUpdateItem(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  @Override
  public void onFinishInflate() {
    super.onFinishInflate();

    this.icon  = findViewById(R.id.conversation_update_icon);
    this.title = findViewById(R.id.conversation_update_title);
    this.body  = findViewById(R.id.conversation_update_body);
    this.date  = findViewById(R.id.conversation_update_date);
    this.callBack = findViewById(R.id.ic_callback);
    this.setOnClickListener(new InternalClickListener(null));
  }

  @Override
  public void bind(@NonNull MessageRecord           messageRecord,
                   @NonNull Optional<MessageRecord> previousMessageRecord,
                   @NonNull Optional<MessageRecord> nextMessageRecord,
                   @NonNull GlideRequests           glideRequests,
                   @NonNull Locale                  locale,
                   @NonNull Set<MessageRecord>      batchSelected,
                   @NonNull Recipient               conversationRecipient,
                   @Nullable String                 searchQuery,
                            boolean                 pulseUpdate)
  {
    this.batchSelected = batchSelected;

    bind(messageRecord, locale);
  }

  @Override
  protected void onDetachedFromWindow() {
    unbind();
    super.onDetachedFromWindow();
  }

  @Override
  public void setEventListener(@Nullable EventListener listener) {
    // No events to report yet
  }

  @Override
  public MessageRecord getMessageRecord() {
    return messageRecord;
  }

  private void bind(@NonNull MessageRecord messageRecord, @NonNull Locale locale) {
    if (this.sender != null) {
      this.sender.removeForeverObserver(this);
    }

    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody(), messageRecord.isGroupV2()).removeObserver(this);
    }

    this.messageRecord = messageRecord;
    this.sender        = messageRecord.getIndividualRecipient().live();
    this.locale        = locale;

    this.sender.observeForever(this);

    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody(), messageRecord.isGroupV2()).addObserver(this);
    }

    present(messageRecord);
  }

  private void present(MessageRecord messageRecord) {

    if (PinJobBinding.isBindMessage(this, messageRecord)) return;
    if (BackgroundJobBinding.isBindMessage(this, messageRecord)) return;

    if      (messageRecord.isGroupAction())           setGroupRecord(messageRecord);
    else if (messageRecord.isCallLog())               setCallRecord(messageRecord);
    else if (messageRecord.isJoined())                setJoinedRecord(messageRecord);
    else if (messageRecord.isExpirationTimerUpdate()) setTimerRecord(messageRecord);
    else if (messageRecord.isEndSession())            setEndSessionRecord(messageRecord);
    else if (messageRecord.isIdentityUpdate())        setIdentityRecord(messageRecord);
    else if (messageRecord.isIdentityVerified() ||
             messageRecord.isIdentityDefault())       setIdentityVerifyUpdate(messageRecord);
    else      throw new AssertionError("Neither group nor log nor joined.");



    if (batchSelected.contains(messageRecord)) setSelected(true);
    else                                       setSelected(false);
  }

  private void setCallRecord(MessageRecord messageRecord) {
    callBack.setVisibility(GONE);

    if      (messageRecord.isIncomingCall()) icon.setImageResource(R.drawable.ic_call_received_grey600_24dp);
    else if (messageRecord.isOutgoingCall()) icon.setImageResource(R.drawable.ic_call_made_grey600_24dp);
    else {
      icon.setImageResource(R.drawable.ic_call_missed_grey600_24dp);
      callBack.setVisibility(VISIBLE);

      callBack.setOnClickListener( new OnClickListener() {

        @Override
        public void onClick(View v) {
          MessageRecord messageRecordahihi = messageRecord;
          MessageRecord messageRecordahihddddi = messageRecord;

          try {
            sendMessageCallGroup(false, false, !messageRecord.getRecipient().isGroup(), messageRecord.getRecipient());
          } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
          }

        }
      });
    }
    body.setText(messageRecord.getDisplayBody(getContext()));
    date.setText(DateUtils.getExtendedRelativeTimeSpanString(getContext(), locale, messageRecord.getDateReceived()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(View.VISIBLE);
  }

  private void sendMessageCallGroup(boolean isHostEndCall, boolean isVideo, boolean isOneOne, Recipient recipient) throws UnsupportedEncodingException {
    Recipient recipientSnapshot = recipient;
    Recipient recipient1 = Recipient.self();

    String roomName = "yoush_";
    String subject = "";
    String messageType = "";
    String callerName = "";

    if (isOneOne || !recipientSnapshot.getGroupId().isPresent()) {

      String phoneNumber1 = recipient1.getE164().get();
      String phoneNumber2 = recipientSnapshot.getE164().get();

      if (phoneNumber1.compareTo(phoneNumber2) == 1) {
        roomName = roomName + phoneNumber1 + "_" + phoneNumber2;
      } else {
        roomName = roomName + phoneNumber2 + "_" + phoneNumber1;
      }


      subject = null;
      messageType = "call";
      callerName = recipient1.getProfileName().toString();
    } else {
      roomName = roomName + recipientSnapshot.getGroupId().get();
      subject = recipientSnapshot.getName(getContext());
      messageType = "groupCall";
      callerName = null;
    }

    String callState = "dialing";
    if (isHostEndCall) {
      callState = "endCall";
    } else {
//      DatabaseFactory.getSmsDatabase(getContext()).insertOutgoingCall(recipientSnapshot.getId());
    }

    roomName = roomName.replaceAll("!","");

    String callObject = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+!isVideo+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";
    String message = "{\"messageType\":\""+messageType+"\", \"room\": \""+roomName+"\", \"message\": \"Cuộc gọi\", \"state\":\""+callState+"\", \"audioOnly\":\""+!isVideo+"\", \"caller\":{\"uuid\":\""+recipient1.getUuid().get()+"\", \"phoneNumber\":\""+recipient1.getE164().get()+"\", \"callerName\": \""+callerName+"\"}, \"subject\": \""+subject+"\",\"callId\": \""+recipient1.getUuid().get()+"\"}";

    OutgoingMediaMessage outgoingMessage =
            new OutgoingMediaMessage(recipient,
                    message,
                    Collections.emptyList(),
                    System.currentTimeMillis(),
                    -1,
                    recipient.getExpireMessages() * 1000,
                    false,
                    ThreadDatabase.DistributionTypes.DEFAULT,
                    null,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(), "");

    if (!isHostEndCall) {
      OkHttpClient client = new OkHttpClient();

      Request request = new Request.Builder().url("/room-size?room=" + roomName).build();

      client.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(Call call, IOException e) {

        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
          if (response.code() == 404) {
            MessageSender.sendCallGroup(getContext(), outgoingMessage,callObject, -1, false, null);
          }
        }
      });
    }

    if (isHostEndCall) {
      MessageSender.sendCallGroup(getContext(), outgoingMessage,callObject, -1, false, null);
    }



    if (isHostEndCall) {
      Intent intent = new Intent(getContext(), GroupCallBeginService.class);
      intent.setAction(GroupCallBeginService.ACTION_LOCAL_HANGUP)
              .putExtra(GroupCallBeginService.EXTRA_REMOTE_PEER, new RemotePeer(recipient.getId()));
    }

    if (!isHostEndCall) {

      Intent intent = new Intent(getContext(), GroupCallBeginService.class);
      intent.setAction(GroupCallBeginService.ACTION_OUTGOING_CALL)
              .putExtra(GroupCallBeginService.EXTRA_REMOTE_PEER, new RemotePeer(recipient.getId()));

      getContext().startService(intent);

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

      JitsiMeetConferenceOptions options = null;
      try {
        options = new JitsiMeetConferenceOptions.Builder()
                .setRoom(roomName)
                .setServerURL(new URL("" + configOverride))
                .setToken(jws)
                .setUserInfo(userInfo)
                .setVideoMuted(!isVideo)

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

//                .setWelcomePageEnabled(false)
                .build();
      } catch (MalformedURLException e) {
        e.printStackTrace();
      }
      JitsiService.launch(getContext(),options, false, null);
    }
  }

  private void setTimerRecord(final MessageRecord messageRecord) {
    if (messageRecord.getExpiresIn() > 0) {
      icon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_timer_24));
    } else {
      icon.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_timer_disabled_24));
    }

    icon.setColorFilter(getIconTintFilter());
    title.setText(ExpirationUtil.getExpirationDisplayValue(getContext(), (int)(messageRecord.getExpiresIn() / 1000)));
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(VISIBLE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private ColorFilter getIconTintFilter() {
    return new PorterDuffColorFilter(ThemeUtil.getThemedColor(getContext(), R.attr.icon_tint), PorterDuff.Mode.SRC_IN);
  }

  private void setIdentityRecord(final MessageRecord messageRecord) {
    icon.setImageDrawable(ThemeUtil.getThemedDrawable(getContext(), R.attr.safety_number_icon));
    icon.setColorFilter(getIconTintFilter());
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setIdentityVerifyUpdate(final MessageRecord messageRecord) {
    if (messageRecord.isIdentityVerified()) icon.setImageResource(R.drawable.ic_check_white_24dp);
    else                                    icon.setImageResource(R.drawable.ic_info_outline_white_24dp);

    icon.setColorFilter(getIconTintFilter());
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setGroupRecord(MessageRecord messageRecord) {
    icon.setImageDrawable(ThemeUtil.getThemedDrawable(getContext(), R.attr.menu_group_icon));
    icon.clearColorFilter();

    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setJoinedRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_favorite_grey600_24dp);
    icon.clearColorFilter();
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }

  private void setEndSessionRecord(MessageRecord messageRecord) {
    icon.setImageResource(R.drawable.ic_refresh_white_24dp);
    icon.setColorFilter(getIconTintFilter());
    body.setText(messageRecord.getDisplayBody(getContext()));

    title.setVisibility(GONE);
    body.setVisibility(VISIBLE);
    date.setVisibility(GONE);
  }
  
  @Override
  public void onRecipientChanged(@NonNull Recipient recipient) {
    present(messageRecord);
  }

  @Override
  public void setOnClickListener(View.OnClickListener l) {
    super.setOnClickListener(new InternalClickListener(l));
  }

  @Override
  public void unbind() {
    if (sender != null) {
      sender.removeForeverObserver(this);
    }
    if (this.messageRecord != null && messageRecord.isGroupAction()) {
      GroupUtil.getDescription(getContext(), messageRecord.getBody(), messageRecord.isGroupV2()).removeObserver(this);
    }
  }

  private class InternalClickListener implements View.OnClickListener {

    @Nullable private final View.OnClickListener parent;

    InternalClickListener(@Nullable View.OnClickListener parent) {
      this.parent = parent;
    }

    @Override
    public void onClick(View v) {
      if ((!messageRecord.isIdentityUpdate()  &&
           !messageRecord.isIdentityDefault() &&
           !messageRecord.isIdentityVerified()) ||
          !batchSelected.isEmpty())
      {
        if (parent != null) parent.onClick(v);
        return;
      }

      final Recipient sender = ConversationUpdateItem.this.sender.get();

      IdentityUtil.getRemoteIdentityKey(getContext(), sender).addListener(new ListenableFuture.Listener<Optional<IdentityRecord>>() {
        @Override
        public void onSuccess(Optional<IdentityRecord> result) {
          if (result.isPresent()) {
            getContext().startActivity(VerifyIdentityActivity.newIntent(getContext(), result.get()));
          }
        }

        @Override
        public void onFailure(ExecutionException e) {
          Log.w(TAG, e);
        }
      });
    }
  }
}

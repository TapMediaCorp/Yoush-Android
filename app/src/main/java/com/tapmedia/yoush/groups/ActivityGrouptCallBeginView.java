package com.tapmedia.yoush.groups;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.AvatarImageView;
import com.tapmedia.yoush.contacts.avatars.ContactPhoto;
import com.tapmedia.yoush.contacts.avatars.FallbackContactPhoto;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.AvatarUtil;

public class ActivityGrouptCallBeginView extends FrameLayout {

  private ControlsListener controlsListener;

  private ImageView                     answer;
  private TextView                      recipientName;
  private String                        jitsiRoom;
  private AvatarImageView               avatar;
  private ImageView                     avatarCard;
  private TextView                      status;
  private static final Recipient.FallbackPhotoProvider FALLBACK_PHOTO_PROVIDER    = new Recipient.FallbackPhotoProvider();

  public ActivityGrouptCallBeginView(@NonNull Context context) {
    this(context, null);
  }

  public ActivityGrouptCallBeginView(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);

    LayoutInflater.from(context).inflate(R.layout.group_call_begin_view, this, true);
  }

  @SuppressWarnings("CodeBlock2Expr")
  @Override
  protected void onFinishInflate() {
    super.onFinishInflate();
    answer                    = findViewById(R.id.call_screen_answer_call);
    recipientName             = findViewById(R.id.call_screen_recipient_name);
    status                    = findViewById(R.id.call_screen_status);
    View decline                = findViewById(R.id.call_screen_decline_call);
    decline.setOnClickListener(v -> runIfNonNull(controlsListener, ControlsListener::onDenyCallPressed));

    answer.setOnClickListener(v -> startJitsi());

    avatar                    = findViewById(R.id.call_screen_recipient_avatar);
    avatarCard                = findViewById(R.id.call_screen_recipient_avatar_call_card);
  }

  private void startJitsi() {
//    runIfNonNull(controlsListener, ControlsListener::onDenyCallPressed);

    Intent     intent     = new Intent(this.getContext(), GroupCallBeginService.class);
    intent.setAction(GroupCallBeginService.ACTION_ANSWER_CALL);
    this.getContext().startService(intent);


//    JitsiMeetConferenceOptions options = null;
//    try {
//      Recipient recipient1 = Recipient.self();
//      JitsiMeetUserInfo userInfo = new JitsiMeetUserInfo();
//      userInfo.setDisplayName(recipient1.getE164().get());
//
//      options = new JitsiMeetConferenceOptions.Builder()
//              .setServerURL(new URL("https://jitsidev.tnmedcorp.com"))
//              .setRoom(jitsiRoom)
//              .setUserInfo(userInfo)
//              .setWelcomePageEnabled(true)
//              .build();
//    } catch (MalformedURLException e) {
//      e.printStackTrace();
//    }
//    JitsiService.launch(this.getContext(),options);
  }

  public void setRoomName(@NonNull String roomName) {
    jitsiRoom = roomName;
  }

  public void setSenderName(@NonNull String senderName,@NonNull String groupName) {
    if (groupName != null) {
      recipientName.setText(senderName);
      Resources resources = this.getResources();
      status.setText("Group " + groupName + ": " + resources.getString(R.string.WebRtcCallActivity__calling));
    } else {
      recipientName.setText(senderName);
      status.setText(R.string.WebRtcCallActivity__calling);
    }
    
  }

  public void setAvatar (@NonNull Recipient recipient) {
    avatar.setFallbackPhotoProvider(FALLBACK_PHOTO_PROVIDER);
    avatar.setAvatar(GlideApp.with(this), recipient, false);
    AvatarUtil.loadBlurredIconIntoViewBackground(recipient, this);
    setRecipientCallCard(recipient);
  }

  private void setRecipientCallCard(@NonNull Recipient recipient) {
    ContactPhoto         contactPhoto  = recipient.getContactPhoto();
    FallbackContactPhoto fallbackPhoto = recipient.getFallbackContactPhoto(FALLBACK_PHOTO_PROVIDER);

    GlideApp.with(this).load(contactPhoto)
            .fallback(fallbackPhoto.asCallCard(getContext()))
            .error(fallbackPhoto.asCallCard(getContext()))
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(this.avatarCard);

    if (contactPhoto == null) this.avatarCard.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    else                      this.avatarCard.setScaleType(ImageView.ScaleType.CENTER_CROP);

    this.avatarCard.setBackgroundColor(recipient.getColor().toActionBarColor(getContext()));
  }

  public void setControlsListener(@Nullable ControlsListener controlsListener) {
    this.controlsListener = controlsListener;
  }

  public interface ControlsListener {
    void onDenyCallPressed();
  }

  private static void runIfNonNull(@Nullable ControlsListener controlsListener, @NonNull Consumer<ControlsListener> controlsListenerConsumer) {
    if (controlsListener != null) {
      controlsListenerConsumer.accept(controlsListener);
    }
  }
}

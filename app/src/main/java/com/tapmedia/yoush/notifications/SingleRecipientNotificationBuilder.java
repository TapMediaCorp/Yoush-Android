package com.tapmedia.yoush.notifications;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.annimon.stream.Stream;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.avatars.ContactColors;
import com.tapmedia.yoush.contacts.avatars.ContactPhoto;
import com.tapmedia.yoush.contacts.avatars.FallbackContactPhoto;
import com.tapmedia.yoush.contacts.avatars.GeneratedContactPhoto;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.DecryptableStreamUriLoader;
import com.tapmedia.yoush.mms.GlideApp;
import com.tapmedia.yoush.mms.Slide;
import com.tapmedia.yoush.mms.SlideDeck;
import com.tapmedia.yoush.preferences.widgets.NotificationPrivacyPreference;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.AvatarUtil;
import com.tapmedia.yoush.util.BitmapUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;

import org.whispersystems.libsignal.util.guava.Optional;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SingleRecipientNotificationBuilder extends AbstractNotificationBuilder {

  private static final String TAG = SingleRecipientNotificationBuilder.class.getSimpleName();

  private static final int BIG_PICTURE_DIMEN = 500;
  private static final int LARGE_ICON_DIMEN  = 250;

  private final List<NotificationCompat.MessagingStyle.Message> messages = new LinkedList<>();

  private SlideDeck    slideDeck;
  private CharSequence contentTitle;
  private CharSequence contentText;
  private Recipient threadRecipient;
  public boolean isHidden = false;

  public SingleRecipientNotificationBuilder(@NonNull Context context, @NonNull NotificationPrivacyPreference privacy)
  {
    super(new ContextThemeWrapper(context, R.style.TextSecure_LightTheme), privacy);

    setSmallIcon(R.drawable.ic_noti_new);
    setColor(context.getResources().getColor(R.color.main_color_500));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }

  public void setThread(@NonNull Recipient recipient) {
    String channelId = recipient.getNotificationChannel();
    setChannelId(channelId != null ? channelId : NotificationChannels.getMessagesChannel(context));

    if (privacy.isDisplayContact()) {
      setContentTitle(recipient.getDisplayName(context));

      if (recipient.getContactUri() != null) {
        addPerson(recipient.getContactUri().toString());
      }

      setLargeIcon(getContactDrawable(recipient));

    } else {
      setLargeIcon(new GeneratedContactPhoto("Unknown", R.drawable.ic_profile_outline_40_new).asDrawable(context, ContactColors.UNKNOWN_COLOR.toConversationColor(context)));
    }
  }

  public void setThreadHidden(@NonNull Recipient recipient) {
    String channelId = recipient.getNotificationChannel();
    setChannelId(channelId != null ? channelId : NotificationChannels.getMessagesChannel(context));
  }

  private Drawable getContactDrawable(@NonNull Recipient recipient) {
    ContactPhoto contactPhoto = recipient.getContactPhoto();
    FallbackContactPhoto fallbackContactPhoto = recipient.getFallbackContactPhoto();

    if (contactPhoto != null) {
      try {
        return GlideApp.with(context.getApplicationContext())
                .load(contactPhoto)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .circleCrop()
                                    .submit(context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width),
                                            context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_height))
                                    .get();
      } catch (InterruptedException | ExecutionException e) {
        return fallbackContactPhoto.asDrawable(context, recipient.getColor().toConversationColor(context));
      }
    } else {
      return fallbackContactPhoto.asDrawable(context, recipient.getColor().toConversationColor(context));
    }
  }

  public void setMessageCount(int messageCount) {
    setContentInfo(String.valueOf(messageCount));
    setNumber(messageCount);
  }

  public void setPrimaryMessageBody(@NonNull  Recipient threadRecipients,
                                    @NonNull  Recipient individualRecipient,
                                    @NonNull  CharSequence message,
                                    @Nullable SlideDeck slideDeck)
  {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();

    if (privacy.isDisplayContact() && threadRecipients.isGroup() && !isHidden) {
      stringBuilder.append(Util.getBoldedString(individualRecipient.getDisplayName(context) + ": "));
    }else {

    }

    if (privacy.isDisplayMessage()) {
      setContentText(stringBuilder.append(message));
      this.slideDeck = slideDeck;
    } else {
      setContentText(stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message)));
    }
  }

  public void addAndroidAutoAction(@NonNull PendingIntent androidAutoReplyIntent,
                                   @NonNull PendingIntent androidAutoHeardIntent, long timestamp)
  {

    if (contentTitle == null || contentText == null)
      return;

    RemoteInput remoteInput = new RemoteInput.Builder(AndroidAutoReplyReceiver.VOICE_REPLY_KEY)
                                  .setLabel(context.getString(R.string.MessageNotifier_reply))
                                  .build();

    NotificationCompat.CarExtender.UnreadConversation.Builder unreadConversationBuilder =
            new NotificationCompat.CarExtender.UnreadConversation.Builder(contentTitle.toString())
                                                                 .addMessage(contentText.toString())
                                                                 .setLatestTimestamp(timestamp)
                                                                 .setReadPendingIntent(androidAutoHeardIntent)
                                                                 .setReplyAction(androidAutoReplyIntent, remoteInput);

    extend(new NotificationCompat.CarExtender().setUnreadConversation(unreadConversationBuilder.build()));
  }

  public void addActions(@NonNull PendingIntent markReadIntent,
                         @NonNull PendingIntent quickReplyIntent,
                         @NonNull PendingIntent wearableReplyIntent,
                         @NonNull ReplyMethod replyMethod)
  {
    Action markAsReadAction = new Action(R.drawable.check,
                                         context.getString(R.string.MessageNotifier_mark_read),
                                         markReadIntent);

    String actionName = context.getString(R.string.MessageNotifier_reply);
    String label      = context.getString(replyMethodLongDescription(replyMethod));

    Action replyAction = new Action(R.drawable.ic_reply_white_36dp,
                                    actionName,
                                    quickReplyIntent);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      replyAction = new Action.Builder(R.drawable.ic_reply_white_36dp,
                                       actionName,
                                       wearableReplyIntent)
          .addRemoteInput(new RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY)
                              .setLabel(label).build())
          .build();
    }

    Action wearableReplyAction = new Action.Builder(R.drawable.ic_reply,
                                                    actionName,
                                                    wearableReplyIntent)
        .addRemoteInput(new RemoteInput.Builder(DefaultMessageNotifier.EXTRA_REMOTE_REPLY)
                            .setLabel(label).build())
        .build();

    addAction(markAsReadAction);
    addAction(replyAction);

    extend(new NotificationCompat.WearableExtender().addAction(markAsReadAction)
                                                    .addAction(wearableReplyAction));
  }

  @StringRes
  private static int replyMethodLongDescription(@NonNull ReplyMethod replyMethod) {
    switch (replyMethod) {
      case GroupMessage:
        return R.string.MessageNotifier_reply;
      case SecureMessage:
        return R.string.MessageNotifier_signal_message;
      case UnsecuredSmsMessage:
        return R.string.MessageNotifier_unsecured_sms;
      default:
        return R.string.MessageNotifier_reply;
    }
  }

  public void addMessageBody(@Nullable Recipient threadRecipient,
                             @Nullable Recipient individualRecipient,
                             @Nullable CharSequence messageBody,
                             long timestamp) {
    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
    Person.Builder personBuilder = new Person.Builder()
            .setKey(individualRecipient.getId().serialize())
            .setBot(false);

    this.threadRecipient = threadRecipient;

    if (privacy.isDisplayContact() && individualRecipient != null) {
      personBuilder.setName(individualRecipient.getDisplayName(context));

      Bitmap bitmap = getLargeBitmap(getContactDrawable(individualRecipient));
      if (bitmap != null) {
        personBuilder.setIcon(IconCompat.createWithBitmap(bitmap));
      }
    } else {
      personBuilder.setName("");
    }

    final CharSequence text;
    if (privacy.isDisplayMessage()) {
      text = messageBody == null ? "" : messageBody;
    } else {
      text = stringBuilder.append(context.getString(R.string.SingleRecipientNotificationBuilder_new_message));
    }

    messages.add(new NotificationCompat.MessagingStyle.Message(text, timestamp, personBuilder.build()));
  }

  public void addMessageBodyHidden(
          @NonNull Recipient threadRecipient,
          @NonNull Recipient individualRecipient,
          long timestamp) {
    this.threadRecipient = threadRecipient;
    messages.add(new NotificationCompat.MessagingStyle.Message(
            context.getString(R.string.AbstractNotificationBuilder_new_message),
            timestamp,
           context.getString(R.string.SingleRecipientNotificationBuilder_signal))
    );
  }

  @Override
  public Notification build() {
    if (privacy.isDisplayMessage()) {
      Optional<Uri> largeIconUri = getLargeIconUri(slideDeck);
      Optional<Uri> bigPictureUri = getBigPictureUri(slideDeck);

      if (messages.size() == 1 && largeIconUri.isPresent()) {
        setLargeIcon(getNotificationPicture(largeIconUri.get(), LARGE_ICON_DIMEN));
      }

      if (messages.size() == 1 && bigPictureUri.isPresent()) {
        setStyle(new NotificationCompat.BigPictureStyle()
                                       .bigPicture(getNotificationPicture(bigPictureUri.get(), BIG_PICTURE_DIMEN))
                                       .setSummaryText(getBigText()));
      } else {
        if (Build.VERSION.SDK_INT >= 24) {
          applyMessageStyle();
        } else {
          applyLegacy();
        }
      }
    }

    return super.build();
  }

  private void applyMessageStyle() {
    NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(
        new Person.Builder()
                  .setBot(false)
                  .setName(Recipient.self().getDisplayName(context))
                  .setKey(Recipient.self().getId().serialize())
                  .setIcon(AvatarUtil.getIconForNotification(context, Recipient.self()))
                  .build());

    if (threadRecipient.isGroup()) {
      if (privacy.isDisplayContact() && !isHidden) {
        messagingStyle.setConversationTitle(threadRecipient.getDisplayName(context));
      } else {
      }

      messagingStyle.setGroupConversation(true);
    }

    Stream.of(messages).forEach(messagingStyle::addMessage);
    setStyle(messagingStyle);
  }

  private void applyLegacy() {
    setStyle(new NotificationCompat.BigTextStyle().bigText(getBigText()));
  }

  private void setLargeIcon(@Nullable Drawable drawable) {
    if (drawable != null) {
      setLargeIcon(getLargeBitmap(drawable));
    }
  }

  private @Nullable Bitmap getLargeBitmap(@Nullable Drawable drawable) {
    if (drawable != null) {
      int largeIconTargetSize  = context.getResources().getDimensionPixelSize(R.dimen.contact_photo_target_size);

      return BitmapUtil.createFromDrawable(drawable, largeIconTargetSize, largeIconTargetSize);
    }

    return null;
  }

  private static Optional<Uri> getLargeIconUri(@Nullable SlideDeck slideDeck) {
    if (slideDeck == null) {
      return Optional.absent();
    }

    Slide thumbnailSlide = Optional.fromNullable(slideDeck.getThumbnailSlide()).or(Optional.fromNullable(slideDeck.getStickerSlide())).orNull();
    return getThumbnailUri(thumbnailSlide);
  }

  private static Optional<Uri> getBigPictureUri(@Nullable SlideDeck slideDeck) {
    if (slideDeck == null) {
      return Optional.absent();
    }

    Slide thumbnailSlide = slideDeck.getThumbnailSlide();
    return getThumbnailUri(thumbnailSlide);
  }

  private static Optional<Uri> getThumbnailUri(@Nullable Slide slide) {
    if (slide != null && !slide.isInProgress() && slide.getThumbnailUri() != null) {
      return Optional.of(slide.getThumbnailUri());
    } else {
      return Optional.absent();
    }
  }

  private Bitmap getNotificationPicture(@NonNull Uri uri, int dimension)
  {
    try {
      return GlideApp.with(context.getApplicationContext())
                     .asBitmap()
                     .load(new DecryptableStreamUriLoader.DecryptableUri(uri))
                     .diskCacheStrategy(DiskCacheStrategy.NONE)
                     .submit(dimension, dimension)
                     .get();
    } catch (InterruptedException | ExecutionException e) {
      Log.w(TAG, e);
      return Bitmap.createBitmap(dimension, dimension, Bitmap.Config.RGB_565);
    }
  }

  @Override
  public NotificationCompat.Builder setContentTitle(CharSequence contentTitle) {
    this.contentTitle = contentTitle;
    return super.setContentTitle(contentTitle);
  }

  public NotificationCompat.Builder setContentText(CharSequence contentText) {
    this.contentText = trimToDisplayLength(contentText);
    return super.setContentText(this.contentText);
  }

  private CharSequence getBigText() {
    SpannableStringBuilder content = new SpannableStringBuilder();

    for (int i = 0; i < messages.size(); i++) {
      content.append(getBigTextFor(messages.get(i)));
      if (i < messages.size() - 1) {
        content.append('\n');
      }
    }

    return content;
  }

  private CharSequence getBigTextFor(NotificationCompat.MessagingStyle.Message message) {
    SpannableStringBuilder content = new SpannableStringBuilder();

    if (!isHidden && message.getPerson() != null && message.getPerson().getName() != null && threadRecipient.isGroup()) {
      content.append(Util.getBoldedString(message.getPerson().getName().toString())).append(": ");
    }

    return trimToDisplayLength(content.append(message.getText()));
  }

}

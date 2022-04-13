package com.tapmedia.yoush.webrtc;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.groups.ActivityGrouptCallBeginView;
import com.tapmedia.yoush.groups.GroupCallBeginActivity;
import com.tapmedia.yoush.groups.GroupCallBeginService;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.recipients.Recipient;

/**
 * Manages the state of the WebRtc items in the Android notification bar.
 *
 * @author Moxie Marlinspike
 *
 */

public class CallNotificationBuilder {

  private static final int WEBRTC_NOTIFICATION         = 313388;
  private static final int WEBRTC_NOTIFICATION_RINGING = 313389;

  public static final int TYPE_INCOMING_RINGING    = 1;
  public static final int TYPE_OUTGOING_RINGING    = 2;
  public static final int TYPE_ESTABLISHED         = 3;
  public static final int TYPE_INCOMING_CONNECTING = 4;

  private static ActivityGrouptCallBeginView callScreen;


  public static Notification getCallInProgressNotificationCallGroup(Context context, int type, Recipient recipient, Intent intent) { // String roomName
    Intent contentIntent = new Intent(context, GroupCallBeginActivity.class);
    contentIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    contentIntent.putExtra(GroupCallBeginActivity.ROOM_NAME,intent.getStringExtra(GroupCallBeginService.ROOM_NAME));
    contentIntent.putExtra(GroupCallBeginActivity.GROUP_NAME,intent.getStringExtra(GroupCallBeginService.GROUP_NAME));
    contentIntent.putExtra(GroupCallBeginActivity.SENDER_NAME,intent.getStringExtra(GroupCallBeginService.SENDER_NAME));

    PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT);


    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, getNotificationChannel(context, type))
            .setSmallIcon(R.drawable.ic_call_secure_white_24dp)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setContentTitle(recipient.getDisplayName(context));

    if (type == TYPE_INCOMING_CONNECTING) {
      builder.setContentText(context.getString(R.string.CallNotificationBuilder_connecting));
      builder.setPriority(NotificationCompat.PRIORITY_MIN);
    } else if (type == TYPE_INCOMING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__incoming_signal_call));

      builder.addAction(getServiceNotificationActionCallGroup(context, GroupCallBeginService.ACTION_DENY_CALL_BUTTON, R.drawable.ic_close_grey600_32dp,   R.string.NotificationBarManager__deny_call));
      builder.addAction(getServiceNotificationActionCallGroup(context, GroupCallBeginService.ACTION_ANSWER_CALL, R.drawable.ic_phone_grey600_32dp, R.string.NotificationBarManager__answer_call));

//      if (callActivityRestricted(context)) {
        builder.setFullScreenIntent(pendingIntent, true);
        builder.setPriority(NotificationCompat.PRIORITY_HIGH);
        builder.setCategory(NotificationCompat.CATEGORY_CALL);
//      }
    } else if (type == TYPE_OUTGOING_RINGING) {
      builder.setContentText(context.getString(R.string.NotificationBarManager__establishing_signal_call));
//      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__cancel_call));
    } else {
      builder.setContentText(context.getString(R.string.NotificationBarManager_signal_call_in_progress));
//      builder.addAction(getServiceNotificationAction(context, WebRtcCallService.ACTION_LOCAL_HANGUP, R.drawable.ic_call_end_grey600_32dp, R.string.NotificationBarManager__end_call));
    }

    return builder.build();
  }

  public static int getNotificationIdCallGroup() {
    return WEBRTC_NOTIFICATION_RINGING;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  public static boolean isWebRtcNotification(int notificationId) {
    return notificationId == WEBRTC_NOTIFICATION || notificationId == WEBRTC_NOTIFICATION_RINGING;
  }

  private static @NonNull String getNotificationChannel(@NonNull Context context, int type) {
    if (callActivityRestricted(context) && type == TYPE_INCOMING_RINGING) {
      return NotificationChannels.CALLS;
    } else {
      return NotificationChannels.OTHER;
    }
  }

  private static NotificationCompat.Action getServiceNotificationActionCallGroup(Context context, String action, int iconResId, int titleResId) {
    Intent intent = new Intent(context, GroupCallBeginService.class);
    intent.setAction(action);

    PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, 0);

    return new NotificationCompat.Action(iconResId, context.getString(titleResId), pendingIntent);
  }

  private static boolean callActivityRestricted(@NonNull Context context) {
    return ApplicationContext.getInstance(context).isAppVisible();
  }
}

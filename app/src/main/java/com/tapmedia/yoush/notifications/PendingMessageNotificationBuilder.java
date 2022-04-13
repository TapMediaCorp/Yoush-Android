package com.tapmedia.yoush.notifications;


import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

import com.tapmedia.yoush.MainActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.preferences.widgets.NotificationPrivacyPreference;
import com.tapmedia.yoush.util.TextSecurePreferences;

public class PendingMessageNotificationBuilder extends AbstractNotificationBuilder {

  public PendingMessageNotificationBuilder(Context context, NotificationPrivacyPreference privacy) {
    super(context, privacy);

    // TODO [greyson] Navigation
    Intent intent = new Intent(context, MainActivity.class);

    setSmallIcon(R.drawable.ic_noti_new);
    setColor(context.getResources().getColor(R.color.main_color_500));
    setCategory(NotificationCompat.CATEGORY_MESSAGE);

    setContentTitle(context.getString(R.string.MessageNotifier_you_may_have_new_messages));
    setContentText(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));
    setTicker(context.getString(R.string.MessageNotifier_open_signal_to_check_for_recent_notifications));

    setContentIntent(PendingIntent.getActivity(context, 0, intent, 0));
    setAutoCancel(true);
    setAlarms(null, RecipientDatabase.VibrateState.DEFAULT);

    setOnlyAlertOnce(true);

    if (!NotificationChannels.supported()) {
      setPriority(TextSecurePreferences.getNotificationPriority(context));
    }
  }
}

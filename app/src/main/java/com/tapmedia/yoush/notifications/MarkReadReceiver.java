package com.tapmedia.yoush.notifications;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import com.annimon.stream.Collectors;
import com.annimon.stream.Stream;

import com.tapmedia.yoush.ApplicationContext;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.MessagingDatabase.ExpirationInfo;
import com.tapmedia.yoush.database.MessagingDatabase.MarkedMessageInfo;
import com.tapmedia.yoush.database.MessagingDatabase.SyncMessageId;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.MultiDeviceReadUpdateJob;
import com.tapmedia.yoush.jobs.SendReadReceiptJob;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.service.ExpiringMessageManager;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class MarkReadReceiver extends BroadcastReceiver {

  private static final String TAG                   = MarkReadReceiver.class.getSimpleName();
  public static final  String CLEAR_ACTION          = "com.tapmedia.yoush.notifications.CLEAR";
  public static final  String THREAD_IDS_EXTRA      = "thread_ids";
  public static final  String NOTIFICATION_ID_EXTRA = "notification_id";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!CLEAR_ACTION.equals(intent.getAction()))
      return;

    final long[] threadIds = intent.getLongArrayExtra(THREAD_IDS_EXTRA);

    if (threadIds != null) {
      NotificationManagerCompat.from(context).cancel(intent.getIntExtra(NOTIFICATION_ID_EXTRA, -1));

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          List<MarkedMessageInfo> messageIdsCollection = new LinkedList<>();

          for (long threadId : threadIds) {
            Log.i(TAG, "Marking as read: " + threadId);
            List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);
            messageIdsCollection.addAll(messageIds);
          }

          process(context, messageIdsCollection);

          ApplicationDependencies.getMessageNotifier().updateNotification(context);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  public static void process(@NonNull Context context, @NonNull List<MarkedMessageInfo> markedReadMessages) {
    if (markedReadMessages.isEmpty()) return;

    List<SyncMessageId>  syncMessageIds    = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getSyncMessageId)
                                                   .toList();
    List<ExpirationInfo> mmsExpirationInfo = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getExpirationInfo)
                                                   .filter(ExpirationInfo::isMms)
                                                   .filter(info -> info.getExpiresIn() > 0 && info.getExpireStarted() <= 0)
                                                   .toList();
    List<ExpirationInfo> smsExpirationInfo = Stream.of(markedReadMessages)
                                                   .map(MarkedMessageInfo::getExpirationInfo)
                                                   .filterNot(ExpirationInfo::isMms)
                                                   .filter(info -> info.getExpiresIn() > 0 && info.getExpireStarted() <= 0)
                                                   .toList();

    scheduleDeletion(context, smsExpirationInfo, mmsExpirationInfo);

    ApplicationDependencies.getJobManager().add(new MultiDeviceReadUpdateJob(syncMessageIds));

    Map<Long, List<MarkedMessageInfo>> threadToInfo = Stream.of(markedReadMessages)
                                                            .collect(Collectors.groupingBy(MarkedMessageInfo::getThreadId));

    Stream.of(threadToInfo).forEach(threadToInfoEntry -> {
      Map<RecipientId, List<SyncMessageId>> idMapForThread = Stream.of(threadToInfoEntry.getValue())
                                                                   .map(MarkedMessageInfo::getSyncMessageId)
                                                                   .collect(Collectors.groupingBy(SyncMessageId::getRecipientId));

      Stream.of(idMapForThread).forEach(entry -> {
        List<Long> timestamps = Stream.of(entry.getValue()).map(SyncMessageId::getTimetamp).toList();

        ApplicationDependencies.getJobManager().add(new SendReadReceiptJob(threadToInfoEntry.getKey(), entry.getKey(), timestamps));
      });
    });
  }

  private static void scheduleDeletion(@NonNull Context context,
                                       @NonNull List<ExpirationInfo> smsExpirationInfo,
                                       @NonNull List<ExpirationInfo> mmsExpirationInfo)
  {
    if (smsExpirationInfo.size() > 0) {
      DatabaseFactory.getSmsDatabase(context).markExpireStarted(Stream.of(smsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (mmsExpirationInfo.size() > 0) {
      DatabaseFactory.getMmsDatabase(context).markExpireStarted(Stream.of(mmsExpirationInfo).map(ExpirationInfo::getId).toList(), System.currentTimeMillis());
    }

    if (smsExpirationInfo.size() + mmsExpirationInfo.size() > 0) {
      ExpiringMessageManager expirationManager = ApplicationContext.getInstance(context).getExpiringMessageManager();

      Stream.concat(Stream.of(smsExpirationInfo), Stream.of(mmsExpirationInfo))
            .forEach(info -> expirationManager.scheduleDeletion(info.getId(), info.isMms(), info.getExpiresIn()));
    }
  }
}

package com.tapmedia.yoush.jobmanager.migrations;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.groups.BadGroupIdException;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.JobMigration;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.Base64;
import com.tapmedia.yoush.util.GroupUtil;
import org.whispersystems.signalservice.api.messages.SignalServiceContent;

import java.io.IOException;

/**
 * We changed the format of the queue key for {@link com.tapmedia.yoush.jobs.PushProcessMessageJob}
 * to have the recipient ID in it, so this migrates existing jobs to be in that format.
 */
public class PushProcessMessageQueueJobMigration extends JobMigration {

  private static final String TAG = Log.tag(PushProcessMessageQueueJobMigration.class);

  private final Context context;

  public PushProcessMessageQueueJobMigration(@NonNull Context context) {
    super(6);
    this.context = context;
  }

  @Override
  protected @NonNull JobData migrate(@NonNull JobData jobData) {
    if ("PushProcessJob".equals(jobData.getFactoryKey())) {
      Log.i(TAG, "Found a PushProcessMessageJob to migrate.");
      try {
        return migratePushProcessMessageJob(context, jobData);
      } catch (IOException e) {
        Log.w(TAG, "Failed to migrate message job.", e);
        return jobData;
      }
    }
    return jobData;
  }

  private static @NonNull JobData migratePushProcessMessageJob(@NonNull Context context, @NonNull JobData jobData) throws IOException {
    Data data = jobData.getData();

    String suffix = "";

    if (data.getInt("message_state") == 0) {
      SignalServiceContent content = SignalServiceContent.deserialize(Base64.decode(data.getString("message_content")));

      if (content != null && content.getDataMessage().isPresent() && content.getDataMessage().get().getGroupContext().isPresent()) {
        Log.i(TAG, "Migrating a group message.");
        try {
          GroupId   groupId   = GroupUtil.idFromGroupContext(content.getDataMessage().get().getGroupContext().get());
          Recipient recipient = Recipient.externalGroup(context, groupId);

          suffix = recipient.getId().toQueueKey();
        } catch (BadGroupIdException e) {
          Log.w(TAG, "Bad groupId! Using default queue.");
        }
      } else if (content != null) {
        Log.i(TAG, "Migrating an individual message.");
        suffix = RecipientId.from(content.getSender()).toQueueKey();
      }
    } else {
      Log.i(TAG, "Migrating an exception message.");

      String  exceptionSender = data.getString("exception_sender");
      GroupId exceptionGroup  =  GroupId.parseNullableOrThrow(data.getStringOrDefault("exception_groupId", null));

      if (exceptionGroup != null) {
        suffix = Recipient.externalGroup(context, exceptionGroup).getId().toQueueKey();
      } else if (exceptionSender != null) {
        suffix = Recipient.external(context, exceptionSender).getId().toQueueKey();
      }
    }

    return jobData.withQueueKey("__PUSH_PROCESS_JOB__" + suffix);
  }
}

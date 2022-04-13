package com.tapmedia.yoush.jobs;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.TextSecureExpiredException;
import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.MediaConstraints;
import com.tapmedia.yoush.transport.UndeliverableMessageException;
import com.tapmedia.yoush.util.Util;

import java.util.List;

public abstract class SendJob extends BaseJob {

  @SuppressWarnings("unused")
  private final static String TAG = SendJob.class.getSimpleName();

  public SendJob(Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public final void onRun() throws Exception {
//    if (Util.getDaysTillBuildExpiry() <= 0) {
//      throw new TextSecureExpiredException(String.format("TextSecure expired (build %d, now %d)",
//                                                         BuildConfig.BUILD_TIMESTAMP,
//                                                         System.currentTimeMillis()));
//    }

    Log.i(TAG, "Starting message send attempt");
    onSend();
    Log.i(TAG, "Message send completed");
  }

  protected abstract void onSend() throws Exception;

  protected void markAttachmentsUploaded(long messageId, @NonNull List<Attachment> attachments) {
    AttachmentDatabase database = DatabaseFactory.getAttachmentDatabase(context);

    for (Attachment attachment : attachments) {
      database.markAttachmentUploaded(messageId, attachment);
    }
  }
}

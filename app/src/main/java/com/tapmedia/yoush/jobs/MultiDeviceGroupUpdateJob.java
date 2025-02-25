package com.tapmedia.yoush.jobs;

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.profiles.AvatarHelper;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroup;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceGroupsOutputStream;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class MultiDeviceGroupUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceGroupUpdateJob";

  private static final String TAG = MultiDeviceGroupUpdateJob.class.getSimpleName();

  public MultiDeviceGroupUpdateJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setQueue("MultiDeviceGroupUpdateJob")
                           .setLifespan(TimeUnit.DAYS.toMillis(1))
                           .setMaxAttempts(Parameters.UNLIMITED)
                           .build());
  }

  private MultiDeviceGroupUpdateJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public void onRun() throws Exception {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    ParcelFileDescriptor[] pipe        = ParcelFileDescriptor.createPipe();
    InputStream            inputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
    Uri                    uri         = BlobProvider.getInstance()
                                                     .forData(inputStream, 0)
                                                     .withFileName("multidevice-group-update")
                                                     .createForSingleSessionOnDiskAsync(context,
                                                                                        () -> Log.i(TAG, "Write successful."),
                                                                                        e  -> Log.w(TAG, "Error during write.", e));

    try (GroupDatabase.Reader reader = DatabaseFactory.getGroupDatabase(context).getGroups()) {
      DeviceGroupsOutputStream out     = new DeviceGroupsOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]));
      boolean                  hasData = false;

      GroupDatabase.GroupRecord record;

      while ((record = reader.getNext()) != null) {
        if (record.isV1Group()) {
          List<SignalServiceAddress> members = new LinkedList<>();

          for (RecipientId member : record.getMembers()) {
            members.add(RecipientUtil.toSignalServiceAddress(context, Recipient.resolved(member)));
          }

          RecipientId               recipientId     = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(record.getId());
          Recipient                 recipient       = Recipient.resolved(recipientId);
          Optional<Integer>         expirationTimer = recipient.getExpireMessages() > 0 ? Optional.of(recipient.getExpireMessages()) : Optional.absent();
          Map<RecipientId, Integer> inboxPositions  = DatabaseFactory.getThreadDatabase(context).getInboxPositions();
          Set<RecipientId>          archived        = DatabaseFactory.getThreadDatabase(context).getArchivedRecipients();

          out.write(new DeviceGroup(record.getId().getDecodedId(),
                                    Optional.fromNullable(record.getTitle()),
                                    members,
                                    getAvatar(record.getRecipientId()),
                                    record.isActive(),
                                    expirationTimer,
                                    Optional.of(recipient.getColor().serialize()),
                                    recipient.isBlocked(),
                                    Optional.fromNullable(inboxPositions.get(recipientId)),
                                    archived.contains(recipientId)));

          hasData = true;
        }
      }

      out.close();

      if (hasData) {
        long length = BlobProvider.getInstance().calculateFileSize(context, uri);

        sendUpdate(ApplicationDependencies.getSignalServiceMessageSender(),
                   BlobProvider.getInstance().getStream(context, uri),
                   length);
      } else {
        Log.w(TAG, "No groups present for sync message...");
      }
    } finally {
      BlobProvider.getInstance().delete(context, uri);
    }
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onFailure() {

  }

  private void sendUpdate(SignalServiceMessageSender messageSender, InputStream stream, long length)
      throws IOException, UntrustedIdentityException
  {
    SignalServiceAttachmentStream attachmentStream   = SignalServiceAttachment.newStreamBuilder()
                                                                              .withStream(stream)
                                                                              .withContentType("application/octet-stream")
                                                                              .withLength(length)
                                                                              .build();

    messageSender.sendMessage(SignalServiceSyncMessage.forGroups(attachmentStream),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }


  private Optional<SignalServiceAttachmentStream> getAvatar(@NonNull RecipientId recipientId) throws IOException {
    if (!AvatarHelper.hasAvatar(context, recipientId)) return Optional.absent();

    return Optional.of(SignalServiceAttachment.newStreamBuilder()
                                              .withStream(AvatarHelper.getAvatar(context, recipientId))
                                              .withContentType("image/*")
                                              .withLength(AvatarHelper.getAvatarLength(context, recipientId))
                                              .build());
  }

  private File createTempFile(String prefix) throws IOException {
    File file = File.createTempFile(prefix, "tmp", context.getCacheDir());
    file.deleteOnExit();

    return file;
  }

  public static final class Factory implements Job.Factory<MultiDeviceGroupUpdateJob> {
    @Override
    public @NonNull MultiDeviceGroupUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceGroupUpdateJob(parameters);
    }
  }
}

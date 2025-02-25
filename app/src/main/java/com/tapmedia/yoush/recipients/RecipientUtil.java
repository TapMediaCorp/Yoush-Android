package com.tapmedia.yoush.recipients;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.sync.DirectoryHelper;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.RecipientDatabase.RegisteredState;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.groups.GroupManager;
import com.tapmedia.yoush.jobs.DirectoryRefreshJob;
import com.tapmedia.yoush.jobs.MultiDeviceBlockedUpdateJob;
import com.tapmedia.yoush.jobs.MultiDeviceMessageRequestResponseJob;
import com.tapmedia.yoush.jobs.RotateProfileKeyJob;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.storage.StorageSyncHelper;
import com.tapmedia.yoush.util.FeatureFlags;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.IOException;

public class RecipientUtil {

  private static final String TAG = Log.tag(RecipientUtil.class);

  /**
   * This method will do it's best to craft a fully-populated {@link SignalServiceAddress} based on
   * the provided recipient. This includes performing a possible network request if no UUID is
   * available.
   */
  @WorkerThread
  public static @NonNull SignalServiceAddress toSignalServiceAddress(@NonNull Context context, @NonNull Recipient recipient) {
    recipient = recipient.resolve();

    if (!recipient.getUuid().isPresent() && !recipient.getE164().isPresent()) {
      throw new AssertionError(recipient.getId() + " - No UUID or phone number!");
    }

    if (FeatureFlags.cds() && !recipient.getUuid().isPresent()) {
      Log.i(TAG, recipient.getId() + " is missing a UUID...");
      try {
        RegisteredState state = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
        recipient = Recipient.resolved(recipient.getId());
        Log.i(TAG, "Successfully performed a UUID fetch for " + recipient.getId() + ". Registered: " + state);
      } catch (IOException e) {
        Log.w(TAG, "Failed to fetch a UUID for " + recipient.getId() + ". Scheduling a future fetch and building an address without one.");
        ApplicationDependencies.getJobManager().add(new DirectoryRefreshJob(recipient, false));
      }
    }

    return new SignalServiceAddress(Optional.fromNullable(recipient.getUuid().orNull()), Optional.fromNullable(recipient.resolve().getE164().orNull()));
  }

  public static boolean isBlockable(@NonNull Recipient recipient) {
    Recipient resolved = recipient.resolve();
    return resolved.isPushGroup() || resolved.hasServiceIdentifier();
  }

  @WorkerThread
  public static void block(@NonNull Context context, @NonNull Recipient recipient) {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    Recipient resolved = recipient.resolve();

    DatabaseFactory.getRecipientDatabase(context).setBlocked(resolved.getId(), true);

    if (resolved.isGroup()) {
      leaveGroup(context, recipient);
    }

    if (resolved.isSystemContact() || resolved.isProfileSharing() || isProfileSharedViaGroup(context,resolved)) {
      ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(resolved.getId(), false);
    }

    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
    StorageSyncHelper.scheduleSyncForDataChange();
  }

  @WorkerThread
  public static void unblock(@NonNull Context context, @NonNull Recipient recipient) {
    if (!isBlockable(recipient)) {
      throw new AssertionError("Recipient is not blockable!");
    }

    DatabaseFactory.getRecipientDatabase(context).setBlocked(recipient.getId(), false);
    ApplicationDependencies.getJobManager().add(new MultiDeviceBlockedUpdateJob());
    StorageSyncHelper.scheduleSyncForDataChange();
    ApplicationDependencies.getJobManager().add(MultiDeviceMessageRequestResponseJob.forAccept(recipient.getId()));
  }

  @WorkerThread
  public static void leaveGroup(@NonNull Context context, @NonNull Recipient recipient) {
    Recipient resolved = recipient.resolve();

    if (!resolved.isGroup()) {
      throw new AssertionError("Not a group!");
    }

    if (!GroupManager.silentLeaveGroup(context, resolved.requireGroupId().requirePush())) {
      Log.w(TAG, "Failed to leave group.");
      Toast.makeText(context, R.string.RecipientPreferenceActivity_error_leaving_group, Toast.LENGTH_LONG).show();
    }
  }

  /**
   * If true, the new message request UI does not need to be shown, and it's safe to send read
   * receipts.
   *
   * Note that this does not imply that a user has explicitly accepted a message request -- it could
   * also be the case that the thread in question is for a system contact or something of the like.
   */
  @WorkerThread
  public static boolean isMessageRequestAccepted(@NonNull Context context, long threadId) {
    if (threadId < 0) {
      return true;
    }

    ThreadDatabase threadDatabase  = DatabaseFactory.getThreadDatabase(context);
    Recipient      threadRecipient = threadDatabase.getRecipientForThreadId(threadId);

    if (threadRecipient == null) {
      return true;
    }

    return isMessageRequestAccepted(context, threadId, threadRecipient);
  }

  /**
   * See {@link #isMessageRequestAccepted(Context, long)}.
   */
  @WorkerThread
  public static boolean isMessageRequestAccepted(@NonNull Context context, @Nullable Recipient threadRecipient) {
    if (threadRecipient == null) {
      return true;
    }

    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(threadRecipient);
    return isMessageRequestAccepted(context, threadId, threadRecipient);
  }

  /**
   * @return True if a conversation existed before we enabled message requests, otherwise false.
   */
  @WorkerThread
  public static boolean isPreMessageRequestThread(@NonNull Context context, long threadId) {
    long beforeTime = SignalStore.misc().getMessageRequestEnableTime();
    return DatabaseFactory.getMmsSmsDatabase(context).getConversationCount(threadId, beforeTime) > 0;
  }

  @WorkerThread
  public static void shareProfileIfFirstSecureMessage(@NonNull Context context, @NonNull Recipient recipient) {
    long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdIfExistsFor(recipient);

    if (isPreMessageRequestThread(context, threadId)) {
      return;
    }

    boolean firstMessage = DatabaseFactory.getMmsSmsDatabase(context).getOutgoingSecureConversationCount(threadId) == 0;

    if (firstMessage) {
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient.getId(), true);
    }
  }

  public static boolean isLegacyProfileSharingAccepted(@NonNull Recipient threadRecipient) {
    return threadRecipient.isLocalNumber()    ||
           threadRecipient.isProfileSharing() ||
           threadRecipient.isSystemContact()  ||
           !threadRecipient.isRegistered();
  }

  @WorkerThread
  private static boolean isMessageRequestAccepted(@NonNull Context context, long threadId, @NonNull Recipient threadRecipient) {
    return threadRecipient.isLocalNumber()                       ||
           threadRecipient.isProfileSharing()                    ||
           threadRecipient.isSystemContact()                     ||
           threadRecipient.isForceSmsSelection()                 ||
           !threadRecipient.isRegistered()                       ||
           hasSentMessageInThread(context, threadId)             ||
           noSecureMessagesAndNoCallsInThread(context, threadId) ||
           isPreMessageRequestThread(context, threadId);
  }

  @WorkerThread
  private static boolean hasSentMessageInThread(@NonNull Context context, long threadId) {
    return DatabaseFactory.getMmsSmsDatabase(context).getOutgoingSecureConversationCount(threadId) != 0;
  }

  @WorkerThread
  private static boolean noSecureMessagesAndNoCallsInThread(@NonNull Context context, long threadId) {
    return DatabaseFactory.getMmsSmsDatabase(context).getSecureConversationCount(threadId) == 0 &&
           !DatabaseFactory.getThreadDatabase(context).hasReceivedAnyCallsSince(threadId, 0);
  }

  @WorkerThread
  private static boolean isProfileSharedViaGroup(@NonNull Context context, @NonNull Recipient recipient) {
    return Stream.of(DatabaseFactory.getGroupDatabase(context).getPushGroupsContainingMember(recipient.getId()))
                 .anyMatch(group -> Recipient.resolved(group.getRecipientId()).isProfileSharing());
  }
}

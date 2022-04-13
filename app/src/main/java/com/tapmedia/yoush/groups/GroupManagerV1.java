package com.tapmedia.yoush.groups;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.protobuf.ByteString;

import com.tapmedia.yoush.attachments.Attachment;
import com.tapmedia.yoush.attachments.UriAttachment;
import com.tapmedia.yoush.database.AttachmentDatabase;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.database.ThreadDatabase;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.groups.GroupManager.GroupActionResult;
import com.tapmedia.yoush.jobs.LeaveGroupJob;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.mms.MmsException;
import com.tapmedia.yoush.mms.OutgoingExpirationUpdateMessage;
import com.tapmedia.yoush.mms.OutgoingGroupUpdateMessage;
import com.tapmedia.yoush.profiles.AvatarHelper;
import com.tapmedia.yoush.providers.BlobProvider;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.sms.MessageSender;
import com.tapmedia.yoush.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

final class GroupManagerV1 {

  private static final String TAG = Log.tag(GroupManagerV1.class);

  static @NonNull GroupActionResult createGroup(@NonNull Context          context,
                                                @NonNull Set<RecipientId> memberIds,
                                                @Nullable byte[]          avatarBytes,
                                                @Nullable String          name,
                                                          boolean         mms)
  {
    final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
    final SecureRandom  secureRandom     = new SecureRandom();
    final GroupId       groupId          = mms ? GroupId.createMms(secureRandom) : GroupId.createV1(secureRandom);
    final RecipientId   groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    final Recipient     groupRecipient   = Recipient.resolved(groupRecipientId);

    memberIds.add(Recipient.self().getId());

    if (groupId.isV1()) {
      GroupId.V1 groupIdV1 = groupId.requireV1();

      groupDatabase.create(groupIdV1, name, memberIds, null, null);

      try {
        AvatarHelper.setAvatar(context, groupRecipientId, avatarBytes != null ? new ByteArrayInputStream(avatarBytes) : null);
      } catch (IOException e) {
        Log.w(TAG, "Failed to save avatar!", e);
      }
      groupDatabase.onAvatarUpdated(groupIdV1, avatarBytes != null);
      DatabaseFactory.getRecipientDatabase(context).setProfileSharing(groupRecipient.getId(), true);
      return sendGroupUpdate(context, groupIdV1, memberIds, name, avatarBytes);
    } else {
      groupDatabase.create(groupId.requireMms(), memberIds);
      long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient, ThreadDatabase.DistributionTypes.CONVERSATION);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  static GroupActionResult updateGroup(@NonNull  Context          context,
                                       @NonNull  GroupId          groupId,
                                       @NonNull  Set<RecipientId> memberAddresses,
                                       @Nullable byte[]           avatarBytes,
                                       @Nullable String           name)
  {
    final GroupDatabase groupDatabase    = DatabaseFactory.getGroupDatabase(context);
    final RecipientId   groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);

    memberAddresses.add(Recipient.self().getId());
    groupDatabase.updateMembers(groupId, new LinkedList<>(memberAddresses));

    if (groupId.isPush()) {
      GroupId.V1 groupIdV1 = groupId.requireV1();

      groupDatabase.updateTitle(groupIdV1, name);
      groupDatabase.onAvatarUpdated(groupIdV1, avatarBytes != null);

      try {
        AvatarHelper.setAvatar(context, groupRecipientId, avatarBytes != null ? new ByteArrayInputStream(avatarBytes) : null);
      } catch (IOException e) {
        Log.w(TAG, "Failed to save avatar!", e);
      }
      return sendGroupUpdate(context, groupIdV1, memberAddresses, name, avatarBytes);
    } else {
      Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);
      long        threadId         = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
      return new GroupActionResult(groupRecipient, threadId);
    }
  }

  private static GroupActionResult sendGroupUpdate(@NonNull  Context          context,
                                                   @NonNull  GroupId.V1       groupId,
                                                   @NonNull  Set<RecipientId> members,
                                                   @Nullable String           groupName,
                                                   @Nullable byte[]           avatar)
  {
    Attachment  avatarAttachment = null;
    RecipientId groupRecipientId = DatabaseFactory.getRecipientDatabase(context).getOrInsertFromGroupId(groupId);
    Recipient   groupRecipient   = Recipient.resolved(groupRecipientId);

    List<GroupContext.Member> uuidMembers = new LinkedList<>();
    List<String>              e164Members = new LinkedList<>();

    for (RecipientId member : members) {
      Recipient recipient = Recipient.resolved(member);
      uuidMembers.add(GroupV1MessageProcessor.createMember(RecipientUtil.toSignalServiceAddress(context, recipient)));
    }

    GroupContext.Builder groupContextBuilder = GroupContext.newBuilder()
                                                           .setId(ByteString.copyFrom(groupId.getDecodedId()))
                                                           .setType(GroupContext.Type.UPDATE)
                                                           .addAllMembersE164(e164Members)
                                                           .addAllMembers(uuidMembers);
    if (groupName != null) groupContextBuilder.setName(groupName);
    GroupContext groupContext = groupContextBuilder.build();

    if (avatar != null) {
      Uri avatarUri = BlobProvider.getInstance().forData(avatar).createForSingleUseInMemory();
      avatarAttachment = new UriAttachment(avatarUri, MediaUtil.IMAGE_PNG, AttachmentDatabase.TRANSFER_PROGRESS_DONE, avatar.length, null, false, false, false, null, null, null, null, null);
    }

    OutgoingGroupUpdateMessage outgoingMessage = new OutgoingGroupUpdateMessage(groupRecipient, groupContext, avatarAttachment, System.currentTimeMillis(), 0, false, null, Collections.emptyList(), Collections.emptyList());
    long                      threadId        = MessageSender.send(context, outgoingMessage, -1, false, null);

    return new GroupActionResult(groupRecipient, threadId);
  }

  @WorkerThread
  static boolean leaveGroup(@NonNull Context context, @NonNull GroupId.V1 groupId) {
    Recipient                            groupRecipient = Recipient.externalGroup(context, groupId);
    long                                 threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
    Optional<OutgoingGroupUpdateMessage> leaveMessage   = createGroupLeaveMessage(context, groupId, groupRecipient);

    if (threadId != -1 && leaveMessage.isPresent()) {
      try {
        long id = DatabaseFactory.getMmsDatabase(context).insertMessageOutbox(leaveMessage.get(), threadId, false, null);
        DatabaseFactory.getMmsDatabase(context).markAsSent(id, true);
      } catch (MmsException e) {
        Log.w(TAG, "Failed to insert leave message.", e);
      }
      ApplicationDependencies.getJobManager().add(LeaveGroupJob.create(groupRecipient));

      GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
      groupDatabase.setActive(groupId, false);
      groupDatabase.remove(groupId, Recipient.self().getId());
      return true;
    } else {
      Log.i(TAG, "Group was already inactive. Skipping.");
      return false;
    }
  }

  @WorkerThread
  static boolean silentLeaveGroup(@NonNull Context context, @NonNull GroupId.V1 groupId) {
    if (DatabaseFactory.getGroupDatabase(context).isActive(groupId)) {
      Recipient                            groupRecipient = Recipient.externalGroup(context, groupId);
      long                                 threadId       = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(groupRecipient);
      Optional<OutgoingGroupUpdateMessage> leaveMessage   = createGroupLeaveMessage(context, groupId, groupRecipient);

      if (threadId != -1 && leaveMessage.isPresent()) {
        ApplicationDependencies.getJobManager().add(LeaveGroupJob.create(groupRecipient));

        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);
        groupDatabase.setActive(groupId, false);
        groupDatabase.remove(groupId, Recipient.self().getId());
        return true;
      } else {
        Log.w(TAG, "Failed to leave group.");
        return false;
      }
    } else {
      Log.i(TAG, "Group was already inactive. Skipping.");
      return true;
    }
  }

  @WorkerThread
  static void updateGroupTimer(@NonNull Context context, @NonNull GroupId.V1 groupId, int expirationTime) {
    RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(context);
    ThreadDatabase    threadDatabase    = DatabaseFactory.getThreadDatabase(context);
    Recipient         recipient         = Recipient.externalGroup(context, groupId);
    long              threadId          = threadDatabase.getThreadIdFor(recipient);

    recipientDatabase.setExpireMessages(recipient.getId(), expirationTime);
    OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(recipient, System.currentTimeMillis(), expirationTime * 1000L);
    MessageSender.send(context, outgoingMessage, threadId, false, null);
  }

  @WorkerThread
  private static Optional<OutgoingGroupUpdateMessage> createGroupLeaveMessage(@NonNull Context context,
                                                                              @NonNull GroupId.V1 groupId,
                                                                              @NonNull Recipient groupRecipient)
  {
    GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(context);

    if (!groupDatabase.isActive(groupId)) {
      Log.w(TAG, "Group has already been left.");
      return Optional.absent();
    }

    GroupContext groupContext = GroupContext.newBuilder()
                                            .setId(ByteString.copyFrom(groupId.getDecodedId()))
                                            .setType(GroupContext.Type.QUIT)
                                            .build();

    return Optional.of(new OutgoingGroupUpdateMessage(groupRecipient,
                                                      groupContext,
                                                      null,
                                                      System.currentTimeMillis(),
                                                      0,
                                                      false,
                                                      null,
                                                      Collections.emptyList(),
                                                      Collections.emptyList()));
  }
}

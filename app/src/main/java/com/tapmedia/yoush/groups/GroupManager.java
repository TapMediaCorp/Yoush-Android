package com.tapmedia.yoush.groups;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.tapmedia.yoush.conversation.background.BackgroundData;
import com.tapmedia.yoush.conversation.background.BackgroundJobSend;
import com.tapmedia.yoush.conversation.model.BackgroundWrapper;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.profiles.AvatarHelper;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.DevLogger;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.Util;

import org.signal.zkgroup.groups.GroupMasterKey;
import org.signal.zkgroup.groups.UuidCiphertext;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class GroupManager {

  private static final String TAG = Log.tag(GroupManager.class);

  @WorkerThread
  public static @NonNull GroupActionResult createGroup(@NonNull  Context        context,
                                                       @NonNull  Set<Recipient> members,
                                                       @Nullable byte[]         avatar,
                                                       @Nullable String         name,
                                                                 boolean        mms)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    boolean          shouldAttemptToCreateV2 = !mms && FeatureFlags.groupsV2create();
    Set<RecipientId> memberIds               = getMemberIds(members);

    if (shouldAttemptToCreateV2) {
      try {
        try (GroupManagerV2.GroupCreator groupCreator = new GroupManagerV2(context).create()) {
          return groupCreator.createGroup(memberIds, name, avatar);
        }
      } catch (MembershipNotSuitableForV2Exception e) {
        Log.w(TAG, "Attempted to make a GV2, but membership was not suitable, falling back to GV1", e);

        return GroupManagerV1.createGroup(context, memberIds, avatar, name, false);
      }
    } else {
      return GroupManagerV1.createGroup(context, memberIds, avatar, name, mms);
    }
  }

  @WorkerThread
  public static @NonNull GroupActionResult createGroupV1(@NonNull  Context        context,
                                                         @NonNull  Set<Recipient> members,
                                                         @Nullable byte[]         avatar,
                                                         @Nullable String         name,
                                                                   boolean        mms)
  {
    return GroupManagerV1.createGroup(context, getMemberIds(members), avatar, name, mms);
  }

  @WorkerThread
  public static GroupActionResult updateGroup(@NonNull  Context context,
                                              @NonNull  GroupId groupId,
                                              @Nullable byte[]  avatar,
                                                        boolean avatarChanged,
                                              @NonNull  String  name,
                                                        boolean nameChanged)
    throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
        return edit.updateGroupTitleAndAvatar(nameChanged ? name : null, avatar, avatarChanged);
      }
    } else {
      List<Recipient> members = DatabaseFactory.getGroupDatabase(context)
                                               .getGroupMembers(groupId, GroupDatabase.MemberSet.FULL_MEMBERS_EXCLUDING_SELF);

      return updateGroup(context, groupId.requireV1(), new HashSet<>(members), avatar, name);
    }
  }

  public static @Nullable GroupActionResult updateGroup(@NonNull  Context        context,
                                                        @NonNull  GroupId.V1     groupId,
                                                        @NonNull  Set<Recipient> members,
                                                        @Nullable byte[]         avatar,
                                                        @Nullable String         name)
  {
    Set<RecipientId> addresses = getMemberIds(members);

    return GroupManagerV1.updateGroup(context, groupId, addresses, avatar, name);
  }

  private static Set<RecipientId> getMemberIds(Collection<Recipient> recipients) {
    final Set<RecipientId> results = new HashSet<>();
    for (Recipient recipient : recipients) {
      results.add(recipient.getId());
    }

    return results;
  }

  @WorkerThread
  public static void leaveGroup(@NonNull Context context, @NonNull GroupId.Push groupId)
      throws GroupChangeBusyException, GroupChangeFailedException, IOException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
        edit.leaveGroup();
        Log.i(TAG, "Left group " + groupId);
      } catch (GroupInsufficientRightsException e) {
        Log.w(TAG, "Unexpected prevention from leaving " + groupId + " due to rights", e);
        throw new GroupChangeFailedException(e);
      } catch (GroupNotAMemberException e) {
        Log.w(TAG, "Already left group " + groupId, e);
      }
    } else {
      if (!GroupManagerV1.leaveGroup(context, groupId.requireV1())) {
        Log.w(TAG, "GV1 group leave failed" + groupId);
        throw new GroupChangeFailedException();
      }
    }
  }

  @WorkerThread
  public static boolean silentLeaveGroup(@NonNull Context context, @NonNull GroupId.Push groupId) {
    if (groupId.isV2()) {
      throw new AssertionError("NYI"); // TODO [Alan] GV2 support silent leave for block and leave operations on GV2
    } else {
      return GroupManagerV1.silentLeaveGroup(context, groupId.requireV1());
    }
  }

  @WorkerThread
  public static void ejectFromGroup(@NonNull Context context, @NonNull GroupId.V2 groupId, @NonNull Recipient recipient)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupInsufficientRightsException, GroupNotAMemberException, IOException
  {
    try (GroupManagerV2.GroupEditor edit = new GroupManagerV2(context).edit(groupId.requireV2())) {
      edit.ejectMember(recipient.getId());
      Log.i(TAG, "Member removed from group " + groupId);
    }
  }

  @WorkerThread
  public static void updateGroupFromServer(@NonNull Context context,
                                           @NonNull GroupMasterKey groupMasterKey,
                                           int revision,
                                           long timestamp,
                                           @Nullable byte[] signedGroupChange)
      throws GroupChangeBusyException, IOException, GroupNotAMemberException
  {
    try (GroupManagerV2.GroupUpdater updater = new GroupManagerV2(context).updater(groupMasterKey)) {
      updater.updateLocalToServerRevision(revision, timestamp, signedGroupChange);
    }
  }

  @WorkerThread
  public static void setMemberAdmin(@NonNull Context context,
                                    @NonNull GroupId.V2 groupId,
                                    @NonNull RecipientId recipientId,
                                    boolean admin)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupInsufficientRightsException, GroupNotAMemberException, IOException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.setMemberAdmin(recipientId, admin);
    }
  }

  @WorkerThread
  public static void updateSelfProfileKeyInGroup(@NonNull Context context, @NonNull GroupId.V2 groupId)
      throws IOException, GroupChangeBusyException, GroupInsufficientRightsException, GroupNotAMemberException, GroupChangeFailedException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateSelfProfileKeyInGroup();
    }
  }

  @WorkerThread
  public static void acceptInvite(@NonNull Context context, @NonNull GroupId.V2 groupId)
      throws GroupChangeBusyException, GroupChangeFailedException, GroupNotAMemberException, GroupInsufficientRightsException, IOException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.acceptInvite();
      DatabaseFactory.getGroupDatabase(context)
                     .setActive(groupId, true);
    }
  }

  @WorkerThread
  public static void updateGroupTimer(@NonNull Context context, @NonNull GroupId.Push groupId, int expirationTime)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
        editor.updateGroupTimer(expirationTime);
      }
    } else {
      GroupManagerV1.updateGroupTimer(context, groupId.requireV1(), expirationTime);
    }
  }

  @WorkerThread
  public static void cancelInvites(@NonNull Context context,
                                   @NonNull GroupId.V2 groupId,
                                   @NonNull Collection<UuidCiphertext> uuidCipherTexts)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.cancelInvites(uuidCipherTexts);
    }
  }

  @WorkerThread
  public static void applyMembershipAdditionRightsChange(@NonNull Context context,
                                                         @NonNull GroupId.V2 groupId,
                                                         @NonNull GroupAccessControl newRights)
       throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateMembershipRights(newRights);
    }
  }

  @WorkerThread
  public static void applyAttributesRightsChange(@NonNull Context context,
                                                 @NonNull GroupId.V2 groupId,
                                                 @NonNull GroupAccessControl newRights)
      throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException
  {
    try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
      editor.updateAttributesRights(newRights);
    }
  }

  public static void addMembers(@NonNull Context context,
                                @NonNull GroupId.Push groupId,
                                @NonNull Collection<RecipientId> newMembers)
          throws GroupChangeFailedException, GroupInsufficientRightsException, IOException, GroupNotAMemberException, GroupChangeBusyException, MembershipNotSuitableForV2Exception {


    if (groupId.isV2()) {
      try (GroupManagerV2.GroupEditor editor = new GroupManagerV2(context).edit(groupId.requireV2())) {
        editor.addMembers(newMembers);
      }
    } else {
      GroupDatabase.GroupRecord groupRecord = DatabaseFactory.getGroupDatabase(context).requireGroup(groupId);
      List<RecipientId> members = groupRecord.getMembers();
      byte[] avatar = groupRecord.hasAvatar() ? Util.readFully(AvatarHelper.getAvatar(context, groupRecord.getRecipientId())) : null;
      Set<RecipientId> addresses = new HashSet<>(members);
      addresses.addAll(newMembers);
      GroupManagerV1.updateGroup(context, groupId, addresses, avatar, groupRecord.getTitle());
    }

    DevLogger.d("addMembers");
    //Recipient recipient = Recipient.externalGroup(context, groupId);
    //long threadId = DatabaseFactory.getThreadDatabase(context).getThreadIdFor(recipient);
    //BackgroundWrapper wrapper = BackgroundData.getCurrentBackgroundWrapper(threadId);
    //if (wrapper == null || wrapper.getAction().equals("remove")) return;
    //BackgroundJobSend.setBackground(null, wrapper.getImageUrl(), true);

  }

  public static class GroupActionResult {
    private final Recipient groupRecipient;
    private final long      threadId;

    public GroupActionResult(Recipient groupRecipient, long threadId) {
      this.groupRecipient = groupRecipient;
      this.threadId       = threadId;
    }

    public Recipient getGroupRecipient() {
      return groupRecipient;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}

package com.tapmedia.yoush.mms;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import com.tapmedia.yoush.database.model.databaseprotos.DecryptedGroupV2Context;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.Base64;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Represents either a GroupV1 or GroupV2 encoded context.
 */
public final class MessageGroupContext {

  @NonNull  private final String            encodedGroupContext;
  @NonNull  private final GroupProperties   group;
  @Nullable private final GroupV1Properties groupV1;
  @Nullable private final GroupV2Properties groupV2;

  public MessageGroupContext(@NonNull String encodedGroupContext, boolean v2)
      throws IOException
  {
    this.encodedGroupContext = encodedGroupContext;
    if (v2) {
      this.groupV1 = null;
      this.groupV2 = new GroupV2Properties(DecryptedGroupV2Context.parseFrom(Base64.decode(encodedGroupContext)));
      this.group   = groupV2;
    } else {
      this.groupV1 = new GroupV1Properties(GroupContext.parseFrom(Base64.decode(encodedGroupContext)));
      this.groupV2 = null;
      this.group   = groupV1;
    }
  }

  public MessageGroupContext(@NonNull GroupContext group) {
    this.encodedGroupContext = Base64.encodeBytes(group.toByteArray());
    this.groupV1             = new GroupV1Properties(group);
    this.groupV2             = null;
    this.group               = groupV1;
  }

  public MessageGroupContext(@NonNull DecryptedGroupV2Context group) {
    this.encodedGroupContext = Base64.encodeBytes(group.toByteArray());
    this.groupV1             = null;
    this.groupV2             = new GroupV2Properties(group);
    this.group               = groupV2;
  }
  
  public @NonNull GroupV1Properties requireGroupV1Properties() {
    if (groupV1 == null) {
      throw new AssertionError();
    }
    return groupV1;
  }

  public @NonNull GroupV2Properties requireGroupV2Properties() {
    if (groupV2 == null) {
      throw new AssertionError();
    }
    return groupV2;
  }

  public boolean isV2Group() {
    return groupV2 != null;
  }

  public @NonNull String getEncodedGroupContext() {
    return encodedGroupContext;
  }

  public String getName() {
    return group.getName();
  }

  public List<RecipientId> getMembersListExcludingSelf() {
    return group.getMembersListExcludingSelf();
  }

  public interface GroupProperties {
    @NonNull String getName();
    @NonNull List<RecipientId> getMembersListExcludingSelf();
  }

  public static class GroupV1Properties implements GroupProperties {

    private final GroupContext groupContext;

    public GroupV1Properties(GroupContext groupContext) {
      this.groupContext = groupContext;
    }

    public @NonNull GroupContext getGroupContext() {
      return groupContext;
    }

    public boolean isQuit() {
      return groupContext.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
    }

    public boolean isUpdate() {
      return groupContext.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
    }

    @Override
    public @NonNull String getName() {
      return groupContext.getName();
    }

    @Override
    public @NonNull List<RecipientId> getMembersListExcludingSelf() {
      List<GroupContext.Member> membersList = groupContext.getMembersList();
      if (membersList.isEmpty()) {
        return Collections.emptyList();
      } else {
        LinkedList<RecipientId> members = new LinkedList<>();

        for (GroupContext.Member member : membersList) {
          RecipientId recipient = RecipientId.from(UuidUtil.parseOrNull(member.getUuid()), member.getE164());
          if (!Recipient.self().getId().equals(recipient)) {
            members.add(recipient);
          }
        }
        return members;
      }
    }
  }

  public static class GroupV2Properties implements GroupProperties {

    private final DecryptedGroupV2Context decryptedGroupV2Context;
    private final GroupContextV2          groupContext;
    private final GroupMasterKey          groupMasterKey;

    public GroupV2Properties(DecryptedGroupV2Context decryptedGroupV2Context) {
      this.decryptedGroupV2Context = decryptedGroupV2Context;
      this.groupContext            = decryptedGroupV2Context.getContext();
      try {
        groupMasterKey = new GroupMasterKey(groupContext.getMasterKey().toByteArray());
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
    }

    public @NonNull GroupContextV2 getGroupContext() {
      return groupContext;
    }

    public @NonNull GroupMasterKey getGroupMasterKey() {
      return groupMasterKey;
    }

    public @NonNull List<UUID> getAllActivePendingAndRemovedMembers() {
      LinkedList<UUID> memberUuids = new LinkedList<>();

      memberUuids.addAll(DecryptedGroupUtil.membersToUuidList(decryptedGroupV2Context.getGroupState().getMembersList()));
      memberUuids.addAll(DecryptedGroupUtil.pendingToUuidList(decryptedGroupV2Context.getGroupState().getPendingMembersList()));
      memberUuids.addAll(DecryptedGroupUtil.removedMembersUuidList(decryptedGroupV2Context.getChange()));

      return UuidUtil.filterKnown(memberUuids);
    }

    @Override
    public @NonNull String getName() {
      return decryptedGroupV2Context.getGroupState().getTitle();
    }

    @Override
    public @NonNull List<RecipientId> getMembersListExcludingSelf() {
      List<RecipientId> members = new ArrayList<>(decryptedGroupV2Context.getGroupState().getMembersCount());

      for (DecryptedMember member : decryptedGroupV2Context.getGroupState().getMembersList()) {
        RecipientId recipient = RecipientId.from(UuidUtil.fromByteString(member.getUuid()), null);
        if (!Recipient.self().getId().equals(recipient)) {
          members.add(recipient);
        }
      }

      return members;
    }
  }
}

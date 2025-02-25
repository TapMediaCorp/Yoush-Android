package com.tapmedia.yoush.groups.ui.pendingmemberinvites;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.core.util.Consumer;

import com.annimon.stream.Stream;
import com.google.protobuf.ByteString;

import org.signal.storageservice.protos.groups.local.DecryptedGroup;
import org.signal.storageservice.protos.groups.local.DecryptedPendingMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.UuidCiphertext;
import org.signal.zkgroup.util.UUIDUtil;
import com.tapmedia.yoush.database.DatabaseFactory;
import com.tapmedia.yoush.database.GroupDatabase;
import com.tapmedia.yoush.groups.GroupChangeBusyException;
import com.tapmedia.yoush.groups.GroupChangeFailedException;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.groups.GroupInsufficientRightsException;
import com.tapmedia.yoush.groups.GroupManager;
import com.tapmedia.yoush.groups.GroupNotAMemberException;
import com.tapmedia.yoush.groups.GroupProtoUtil;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.util.concurrent.SignalExecutors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Repository for modifying the pending members on a single group.
 */
final class PendingMemberRepository {

  private static final String TAG = Log.tag(PendingMemberRepository.class);

  private final Context    context;
  private final GroupId.V2 groupId;
  private final Executor   executor;

  PendingMemberRepository(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    this.context  = context.getApplicationContext();
    this.executor = SignalExecutors.BOUNDED;
    this.groupId  = groupId;
  }

  public void getInvitees(@NonNull Consumer<InviteeResult> onInviteesLoaded) {
    executor.execute(() -> {
      GroupDatabase                                groupDatabase      = DatabaseFactory.getGroupDatabase(context);
      GroupDatabase.V2GroupProperties              v2GroupProperties  = groupDatabase.getGroup(groupId).get().requireV2GroupProperties();
      DecryptedGroup                               decryptedGroup     = v2GroupProperties.getDecryptedGroup();
      List<DecryptedPendingMember>                 pendingMembersList = decryptedGroup.getPendingMembersList();
      List<SinglePendingMemberInvitedByYou>        byMe               = new ArrayList<>(pendingMembersList.size());
      List<MultiplePendingMembersInvitedByAnother> byOthers           = new ArrayList<>(pendingMembersList.size());
      ByteString                                   self               = ByteString.copyFrom(UUIDUtil.serialize(Recipient.self().getUuid().get()));
      boolean                                      selfIsAdmin        = v2GroupProperties.isAdmin(Recipient.self());

      Stream.of(pendingMembersList)
            .groupBy(DecryptedPendingMember::getAddedByUuid)
            .forEach(g ->
              {
                ByteString                   inviterUuid    = g.getKey();
                List<DecryptedPendingMember> invitedMembers = g.getValue();

                if (self.equals(inviterUuid)) {
                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    try {
                      Recipient      invitee        = GroupProtoUtil.pendingMemberToRecipient(context, pendingMember);
                      UuidCiphertext uuidCipherText = new UuidCiphertext(pendingMember.getUuidCipherText().toByteArray());

                      byMe.add(new SinglePendingMemberInvitedByYou(invitee, uuidCipherText));
                    } catch (InvalidInputException e) {
                      Log.w(TAG, e);
                    }
                  }
                } else {
                  Recipient                 inviter         = GroupProtoUtil.uuidByteStringToRecipient(context, inviterUuid);
                  ArrayList<UuidCiphertext> uuidCipherTexts = new ArrayList<>(invitedMembers.size());

                  for (DecryptedPendingMember pendingMember : invitedMembers) {
                    try {
                      uuidCipherTexts.add(new UuidCiphertext(pendingMember.getUuidCipherText().toByteArray()));
                    } catch (InvalidInputException e) {
                      Log.w(TAG, e);
                    }
                  }

                  byOthers.add(new MultiplePendingMembersInvitedByAnother(inviter, uuidCipherTexts));
                }
              }
            );

      onInviteesLoaded.accept(new InviteeResult(byMe, byOthers, selfIsAdmin));
    });
  }

  @WorkerThread
  boolean cancelInvites(@NonNull Collection<UuidCiphertext> uuidCipherTexts) {
    try {
      GroupManager.cancelInvites(context, groupId, uuidCipherTexts);
      return true;
    } catch (GroupChangeFailedException | GroupInsufficientRightsException | IOException | GroupNotAMemberException | GroupChangeBusyException e) {
      Log.w(TAG, e);
      return false;
    }
  }

  public static final class InviteeResult {
    private final List<SinglePendingMemberInvitedByYou>        byMe;
    private final List<MultiplePendingMembersInvitedByAnother> byOthers;
    private final boolean                                      canCancelInvites;

    private InviteeResult(List<SinglePendingMemberInvitedByYou> byMe,
                          List<MultiplePendingMembersInvitedByAnother> byOthers,
                          boolean canCancelInvites)
    {
      this.byMe             = byMe;
      this.byOthers         = byOthers;
      this.canCancelInvites = canCancelInvites;
    }

    public List<SinglePendingMemberInvitedByYou> getByMe() {
      return byMe;
    }

    public List<MultiplePendingMembersInvitedByAnother> getByOthers() {
      return byOthers;
    }

    public boolean isCanCancelInvites() {
      return canCancelInvites;
    }
  }

  public final static class SinglePendingMemberInvitedByYou {
    private final Recipient      invitee;
    private final UuidCiphertext inviteeCipherText;

    private SinglePendingMemberInvitedByYou(@NonNull Recipient invitee, @NonNull UuidCiphertext inviteeCipherText) {
      this.invitee           = invitee;
      this.inviteeCipherText = inviteeCipherText;
    }

    public Recipient getInvitee() {
      return invitee;
    }

    public UuidCiphertext getInviteeCipherText() {
      return inviteeCipherText;
    }
  }

  public final static class MultiplePendingMembersInvitedByAnother {
    private final Recipient                  inviter;
    private final Collection<UuidCiphertext> uuidCipherTexts;

    private MultiplePendingMembersInvitedByAnother(@NonNull Recipient inviter, @NonNull Collection<UuidCiphertext> uuidCipherTexts) {
      this.inviter         = inviter;
      this.uuidCipherTexts = uuidCipherTexts;
    }

    public Recipient getInviter() {
      return inviter;
    }

    public Collection<UuidCiphertext> getUuidCipherTexts() {
      return uuidCipherTexts;
    }
  }
}
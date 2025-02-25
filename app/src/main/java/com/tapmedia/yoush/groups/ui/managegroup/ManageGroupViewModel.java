package com.tapmedia.yoush.groups.ui.managegroup;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.tapmedia.yoush.BlockUnblockDialog;
import com.tapmedia.yoush.ContactSelectionListFragment;
import com.tapmedia.yoush.ExpirationDialog;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.ContactsCursorLoader;
import com.tapmedia.yoush.database.MediaDatabase;
import com.tapmedia.yoush.database.loaders.MediaLoader;
import com.tapmedia.yoush.database.loaders.ThreadMediaLoader;
import com.tapmedia.yoush.groups.GroupAccessControl;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.groups.LiveGroup;
import com.tapmedia.yoush.groups.ui.GroupChangeFailureReason;
import com.tapmedia.yoush.groups.ui.GroupErrors;
import com.tapmedia.yoush.groups.ui.GroupMemberEntry;
import com.tapmedia.yoush.groups.ui.addmembers.AddMembersActivity;
import com.tapmedia.yoush.notifications.NotificationChannels;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.util.DefaultValueLiveData;
import com.tapmedia.yoush.util.ExpirationUtil;
import com.tapmedia.yoush.util.SingleLiveEvent;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.livedata.LiveDataUtil;

import java.util.ArrayList;
import java.util.List;

public class ManageGroupViewModel extends ViewModel {

  private static final int MAX_COLLAPSED_MEMBERS = 5;

  private final Context                                     context;
  private final ManageGroupRepository                       manageGroupRepository;
  private final SingleLiveEvent<SnackbarEvent>              snackbarEvents            = new SingleLiveEvent<>();
  private final LiveData<String>                            title;
  private final LiveData<Boolean>                           isAdmin;
  private final LiveData<Boolean>                           canEditGroupAttributes;
  private final LiveData<Boolean>                           canAddMembers;
  private final LiveData<List<GroupMemberEntry.FullMember>> members;
  private final LiveData<Integer>                           pendingMemberCount;
  private final LiveData<String>                            disappearingMessageTimer;
  private final LiveData<String>                            memberCountSummary;
  private final LiveData<String>                            fullMemberCountSummary;
  private final LiveData<GroupAccessControl>                editMembershipRights;
  private final LiveData<GroupAccessControl>                editGroupAttributesRights;
  private final LiveData<Recipient>                         groupRecipient;
  private final MutableLiveData<GroupViewState>             groupViewState            = new MutableLiveData<>(null);
  private final LiveData<MuteState>                         muteState;
  private final LiveData<Boolean>                           hasCustomNotifications;
  private final LiveData<Boolean>                           canCollapseMemberList;
  private final DefaultValueLiveData<CollapseState>         memberListCollapseState   = new DefaultValueLiveData<>(CollapseState.COLLAPSED);
  private final LiveData<Boolean>                           canLeaveGroup;
  private final LiveData<Boolean>                           canBlockGroup;

  private ManageGroupViewModel(@NonNull Context context, @NonNull ManageGroupRepository manageGroupRepository) {
    this.context               = context;
    this.manageGroupRepository = manageGroupRepository;

    manageGroupRepository.getGroupState(this::groupStateLoaded);

    LiveGroup liveGroup = new LiveGroup(manageGroupRepository.getGroupId());

    this.title                     = Transformations.map(liveGroup.getTitle(),
                                                         title -> TextUtils.isEmpty(title) ? context.getString(R.string.Recipient_unknown)
                                                                                           : title);
    this.isAdmin                   = liveGroup.isSelfAdmin();
    this.canCollapseMemberList     = LiveDataUtil.combineLatest(memberListCollapseState,
                                                                Transformations.map(liveGroup.getFullMembers(), m -> m.size() > MAX_COLLAPSED_MEMBERS),
                                                                (state, hasEnoughMembers) -> state != CollapseState.OPEN && hasEnoughMembers);
    this.members                   = LiveDataUtil.combineLatest(liveGroup.getFullMembers(),
                                                                memberListCollapseState,
                                                                this::filterMemberList);
    this.pendingMemberCount        = liveGroup.getPendingMemberCount();
    this.memberCountSummary        = liveGroup.getMembershipCountDescription(context.getResources());
    this.fullMemberCountSummary    = liveGroup.getFullMembershipCountDescription(context.getResources());
    this.editMembershipRights      = liveGroup.getMembershipAdditionAccessControl();
    this.editGroupAttributesRights = liveGroup.getAttributesAccessControl();
    this.disappearingMessageTimer  = Transformations.map(liveGroup.getExpireMessages(), expiration -> ExpirationUtil.getExpirationDisplayValue(context, expiration));
    this.canEditGroupAttributes    = liveGroup.selfCanEditGroupAttributes();
    this.canAddMembers             = liveGroup.selfCanAddMembers();
    this.groupRecipient            = liveGroup.getGroupRecipient();
    this.muteState                 = Transformations.map(this.groupRecipient,
                                                         recipient -> new MuteState(recipient.getMuteUntil(), recipient.isMuted()));
    this.hasCustomNotifications    = Transformations.map(this.groupRecipient,
                                                         recipient -> recipient.getNotificationChannel() != null || !NotificationChannels.supported());
    this.canLeaveGroup             = liveGroup.isActive();
    this.canBlockGroup             = Transformations.map(this.groupRecipient, recipient -> !recipient.isBlocked());
  }

  @WorkerThread
  private void groupStateLoaded(@NonNull ManageGroupRepository.GroupStateResult groupStateResult) {
    groupViewState.postValue(new GroupViewState(groupStateResult.getThreadId(),
                                                groupStateResult.getRecipient(),
                                                () -> new ThreadMediaLoader(context, groupStateResult.getThreadId(), MediaLoader.MediaType.GALLERY, MediaDatabase.Sorting.Newest).getCursor()));
  }

  LiveData<List<GroupMemberEntry.FullMember>> getMembers() {
    return members;
  }

  LiveData<Integer> getPendingMemberCount() {
    return pendingMemberCount;
  }

  LiveData<String> getMemberCountSummary() {
    return memberCountSummary;
  }

  LiveData<String> getFullMemberCountSummary() {
    return fullMemberCountSummary;
  }

  public LiveData<Recipient> getGroupRecipient() {
    return groupRecipient;
  }

  LiveData<GroupViewState> getGroupViewState() {
    return groupViewState;
  }

  LiveData<String> getTitle() {
    return title;
  }

  LiveData<MuteState> getMuteState() {
    return muteState;
  }

  LiveData<GroupAccessControl> getMembershipRights() {
    return editMembershipRights;
  }

  LiveData<GroupAccessControl> getEditGroupAttributesRights() {
    return editGroupAttributesRights;
  }

  LiveData<Boolean> getIsAdmin() {
    return isAdmin;
  }

  LiveData<Boolean> getCanEditGroupAttributes() {
    return canEditGroupAttributes;
  }

  LiveData<Boolean> getCanAddMembers() {
    return canAddMembers;
  }

  LiveData<String> getDisappearingMessageTimer() {
    return disappearingMessageTimer;
  }

  LiveData<Boolean> hasCustomNotifications() {
    return hasCustomNotifications;
  }

  SingleLiveEvent<SnackbarEvent> getSnackbarEvents() {
    return snackbarEvents;
  }

  LiveData<Boolean> getCanCollapseMemberList() {
    return canCollapseMemberList;
  }

  LiveData<Boolean> getCanBlockGroup() {
    return canBlockGroup;
  }

  LiveData<Boolean> getCanLeaveGroup() {
    return canLeaveGroup;
  }

  void handleExpirationSelection() {
    manageGroupRepository.getRecipient(groupRecipient ->
                                         ExpirationDialog.show(context,
                                                               groupRecipient.getExpireMessages(),
                                                               expirationTime -> manageGroupRepository.setExpiration(expirationTime, this::showErrorToast)));
  }

  void applyMembershipRightsChange(@NonNull GroupAccessControl newRights) {
    manageGroupRepository.applyMembershipRightsChange(newRights, this::showErrorToast);
  }

  void applyAttributesRightsChange(@NonNull GroupAccessControl newRights) {
    manageGroupRepository.applyAttributesRightsChange(newRights, this::showErrorToast);
  }

  void blockAndLeave(@NonNull FragmentActivity activity) {
    manageGroupRepository.getRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient,
                                       () -> RecipientUtil.block(context, recipient)));
  }

  void unblock(@NonNull FragmentActivity activity) {
    manageGroupRepository.getRecipient(recipient -> BlockUnblockDialog.showUnblockFor(activity, activity.getLifecycle(), recipient,
                                       () -> RecipientUtil.unblock(context, recipient)));
  }

  void onAddMembers(List<RecipientId> selected) {
    manageGroupRepository.addMembers(selected, this::showSuccessSnackbar, this::showErrorToast);
  }

  void setMuteUntil(long muteUntil) {
    manageGroupRepository.setMuteUntil(muteUntil);
  }

  void clearMuteUntil() {
    manageGroupRepository.setMuteUntil(0);
  }

  void revealCollapsedMembers() {
    memberListCollapseState.setValue(CollapseState.OPEN);
  }

  private @NonNull List<GroupMemberEntry.FullMember> filterMemberList(@NonNull List<GroupMemberEntry.FullMember> members,
                                                                      @NonNull CollapseState collapseState)
  {
    if (collapseState == CollapseState.COLLAPSED && members.size() > MAX_COLLAPSED_MEMBERS) {
      return members.subList(0, MAX_COLLAPSED_MEMBERS);
    } else {
      return members;
    }
  }

  @WorkerThread
  private void showSuccessSnackbar(int numberOfMembersAdded) {
    snackbarEvents.postValue(new SnackbarEvent(numberOfMembersAdded));
  }

  @WorkerThread
  private void showErrorToast(@NonNull GroupChangeFailureReason e) {
    Util.runOnMain(() -> Toast.makeText(context, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show());
  }

  public void onAddMembersClick(@NonNull Fragment fragment, int resultCode) {
    manageGroupRepository.getGroupCapacity(capacity -> {
      int remainingCapacity = capacity.getTotalCapacity();
      if (remainingCapacity <= 0) {
        Toast.makeText(fragment.requireContext(), R.string.ContactSelectionListFragment_the_group_is_full, Toast.LENGTH_SHORT).show();
      } else {
        Intent intent = new Intent(fragment.requireActivity(), AddMembersActivity.class);
        intent.putExtra(AddMembersActivity.GROUP_ID, manageGroupRepository.getGroupId().toString());
        intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, ContactsCursorLoader.DisplayMode.FLAG_PUSH);
        intent.putExtra(ContactSelectionListFragment.TOTAL_CAPACITY, remainingCapacity);
        intent.putParcelableArrayListExtra(ContactSelectionListFragment.CURRENT_SELECTION, new ArrayList<>(capacity.getMembers()));
        fragment.startActivityForResult(intent, resultCode);
      }
    });
  }

  static final class GroupViewState {
             private final long          threadId;
    @NonNull private final Recipient     groupRecipient;
    @NonNull private final CursorFactory mediaCursorFactory;

    private GroupViewState(long threadId,
                           @NonNull Recipient groupRecipient,
                           @NonNull CursorFactory mediaCursorFactory)
    {
      this.threadId           = threadId;
      this.groupRecipient     = groupRecipient;
      this.mediaCursorFactory = mediaCursorFactory;
    }

    long getThreadId() {
      return threadId;
    }

    @NonNull Recipient getGroupRecipient() {
      return groupRecipient;
    }

    @NonNull CursorFactory getMediaCursorFactory() {
      return mediaCursorFactory;
    }
  }

  static final class MuteState {
    private final long    mutedUntil;
    private final boolean isMuted;

    MuteState(long mutedUntil, boolean isMuted) {
      this.mutedUntil = mutedUntil;
      this.isMuted    = isMuted;
    }

    public long getMutedUntil() {
      return mutedUntil;
    }

    public boolean isMuted() {
      return isMuted;
    }
  }

  static final class SnackbarEvent {
    private final int numberOfMembersAdded;

    private SnackbarEvent(int numberOfMembersAdded) {
      this.numberOfMembersAdded = numberOfMembersAdded;
    }

    public int getNumberOfMembersAdded() {
      return numberOfMembersAdded;
    }
  }

  private enum CollapseState {
    OPEN,
    COLLAPSED
  }

  interface CursorFactory {
    Cursor create();
  }

  public static class Factory implements ViewModelProvider.Factory {
    private final Context context;
    private final GroupId groupId;

    public Factory(@NonNull Context context, @NonNull GroupId groupId) {
      this.context = context;
      this.groupId = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new ManageGroupViewModel(context, new ManageGroupRepository(context.getApplicationContext(), groupId));
    }
  }
}

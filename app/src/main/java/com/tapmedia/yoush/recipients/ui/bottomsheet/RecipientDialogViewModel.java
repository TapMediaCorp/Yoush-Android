package com.tapmedia.yoush.recipients.ui.bottomsheet;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.tapmedia.yoush.BlockUnblockDialog;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.VerifyIdentityActivity;
import com.tapmedia.yoush.database.IdentityDatabase;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.groups.LiveGroup;
import com.tapmedia.yoush.groups.ui.GroupChangeFailureReason;
import com.tapmedia.yoush.groups.ui.GroupErrors;
import com.tapmedia.yoush.groups.ui.addtogroup.AddToGroupsActivity;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.recipients.RecipientUtil;
import com.tapmedia.yoush.recipients.ui.managerecipient.ManageRecipientActivity;
import com.tapmedia.yoush.util.CommunicationActions;
import com.tapmedia.yoush.util.Util;
import com.tapmedia.yoush.util.livedata.LiveDataUtil;

import java.util.Objects;

final class RecipientDialogViewModel extends ViewModel {

  private final Context                                          context;
  private final RecipientDialogRepository                        recipientDialogRepository;
  private final LiveData<Recipient>                              recipient;
  private final MutableLiveData<IdentityDatabase.IdentityRecord> identity;
  private final LiveData<AdminActionStatus>                      adminActionStatus;
  private final MutableLiveData<Boolean>                         adminActionBusy;

  private RecipientDialogViewModel(@NonNull Context context,
                                   @NonNull RecipientDialogRepository recipientDialogRepository)
  {
    this.context                   = context;
    this.recipientDialogRepository = recipientDialogRepository;
    this.identity                  = new MutableLiveData<>();
    this.adminActionBusy           = new MutableLiveData<>(false);

    boolean recipientIsSelf = recipientDialogRepository.getRecipientId().equals(Recipient.self().getId());

    if (recipientDialogRepository.getGroupId() != null && recipientDialogRepository.getGroupId().isV2() && !recipientIsSelf) {
      LiveGroup source = new LiveGroup(recipientDialogRepository.getGroupId());

      LiveData<Boolean> localIsAdmin     = source.isSelfAdmin();
      LiveData<Boolean> recipientIsAdmin = source.getRecipientIsAdmin(recipientDialogRepository.getRecipientId());

      adminActionStatus = LiveDataUtil.combineLatest(localIsAdmin, recipientIsAdmin,
        (localAdmin, recipientAdmin) ->
          new AdminActionStatus(localAdmin,
            localAdmin && !recipientAdmin,
            localAdmin && recipientAdmin));
    } else {
      adminActionStatus = new MutableLiveData<>(new AdminActionStatus(false, false, false));
    }

    recipient = Recipient.live(recipientDialogRepository.getRecipientId()).getLiveData();

    boolean isSelf = recipientDialogRepository.getRecipientId().equals(Recipient.self().getId());
    if (!isSelf) {
      recipientDialogRepository.getIdentity(identity::postValue);
    }
  }

  LiveData<Recipient> getRecipient() {
    return recipient;
  }

  LiveData<AdminActionStatus> getAdminActionStatus() {
    return adminActionStatus;
  }

  LiveData<IdentityDatabase.IdentityRecord> getIdentity() {
    return identity;
  }

  LiveData<Boolean> getAdminActionBusy() {
    return adminActionBusy;
  }

  void onMessageClicked(@NonNull Activity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startConversation(activity, recipient, null));
  }

  void onInsecureCallClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> CommunicationActions.startInsecureCall(activity, recipient));
  }

  void onBlockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showBlockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.block(context, recipient)));
  }

  void onUnblockClicked(@NonNull FragmentActivity activity) {
    recipientDialogRepository.getRecipient(recipient -> BlockUnblockDialog.showUnblockFor(activity, activity.getLifecycle(), recipient, () -> RecipientUtil.unblock(context, recipient)));
  }

  void onViewSafetyNumberClicked(@NonNull Activity activity, @NonNull IdentityDatabase.IdentityRecord identityRecord) {
    activity.startActivity(VerifyIdentityActivity.newIntent(activity, identityRecord));
  }

  void onAvatarClicked(@NonNull Activity activity) {
    activity.startActivity(ManageRecipientActivity.newIntent(activity, recipientDialogRepository.getRecipientId()));
  }

  void onMakeGroupAdminClicked(@NonNull Activity activity) {
    new AlertDialog.Builder(activity)
                   .setMessage(context.getString(R.string.RecipientBottomSheet_s_will_be_able_to_edit_group, Objects.requireNonNull(recipient.getValue()).getDisplayName(context)))
                   .setPositiveButton(R.string.RecipientBottomSheet_make_group_admin,
                                      (dialog, which) -> {
                                        adminActionBusy.setValue(true);
                                        recipientDialogRepository.setMemberAdmin(true, result -> {
                                          adminActionBusy.setValue(false);
                                          if (!result) {
                                            Toast.makeText(activity, R.string.ManageGroupActivity_failed_to_update_the_group, Toast.LENGTH_SHORT).show();
                                          }
                                        },
                                        this::showErrorToast);
                                      })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                   .show();
  }

  void onRemoveGroupAdminClicked(@NonNull Activity activity) {
    new AlertDialog.Builder(activity)
                   .setMessage(context.getString(R.string.RecipientBottomSheet_remove_s_as_group_admin, Objects.requireNonNull(recipient.getValue()).getDisplayName(context)))
                   .setPositiveButton(R.string.RecipientBottomSheet_remove_as_admin,
                                      (dialog, which) -> {
                                        adminActionBusy.setValue(true);
                                        recipientDialogRepository.setMemberAdmin(false, result -> {
                                          adminActionBusy.setValue(false);
                                          if (!result) {
                                            Toast.makeText(activity, R.string.ManageGroupActivity_failed_to_update_the_group, Toast.LENGTH_SHORT).show();
                                          }
                                        },
                                        this::showErrorToast);
                                      })
                   .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                   .show();
  }

  void onRemoveFromGroupClicked(@NonNull Activity activity, @NonNull Runnable onSuccess) {
    recipientDialogRepository.getGroupName(title ->
      new AlertDialog.Builder(activity)
                     .setMessage(context.getString(R.string.RecipientBottomSheet_remove_s_from_s, Objects.requireNonNull(recipient.getValue()).getDisplayName(context), title))
                     .setPositiveButton(R.string.RecipientBottomSheet_remove,
                                        (dialog, which) -> {
                                          adminActionBusy.setValue(true);
                                          recipientDialogRepository.removeMember(result -> {
                                            adminActionBusy.setValue(false);
                                            if (result) {
                                              onSuccess.run();
                                            }
                                          },
                                          this::showErrorToast);
                                        })
                     .setNegativeButton(android.R.string.cancel, (dialog, which) -> {})
                     .show());
  }

  void onAddedToContacts() {
    recipientDialogRepository.refreshRecipient();
  }

  void onAddToGroupButton(@NonNull Activity activity) {
    recipientDialogRepository.getGroupMembership(existingGroups -> activity.startActivity(AddToGroupsActivity.newIntent(activity, recipientDialogRepository.getRecipientId(), existingGroups)));
  }

  @WorkerThread
  private void showErrorToast(@NonNull GroupChangeFailureReason e) {
    Util.runOnMain(() -> Toast.makeText(context, GroupErrors.getUserDisplayMessage(e), Toast.LENGTH_LONG).show());
  }

  static class AdminActionStatus {
    private final boolean canRemove;
    private final boolean canMakeAdmin;
    private final boolean canMakeNonAdmin;

    AdminActionStatus(boolean canRemove, boolean canMakeAdmin, boolean canMakeNonAdmin) {
      this.canRemove       = canRemove;
      this.canMakeAdmin    = canMakeAdmin;
      this.canMakeNonAdmin = canMakeNonAdmin;
    }

    boolean isCanRemove() {
      return canRemove;
    }

    boolean isCanMakeAdmin() {
      return canMakeAdmin;
    }

    boolean isCanMakeNonAdmin() {
      return canMakeNonAdmin;
    }
  }

  public static class Factory implements ViewModelProvider.Factory {

    private final Context     context;
    private final RecipientId recipientId;
    private final GroupId     groupId;

    Factory(@NonNull Context context, @NonNull RecipientId recipientId, @Nullable GroupId groupId) {
      this.context     = context;
      this.recipientId = recipientId;
      this.groupId     = groupId;
    }

    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection unchecked
      return (T) new RecipientDialogViewModel(context, new RecipientDialogRepository(context, recipientId, groupId));
    }
  }
}

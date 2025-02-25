package com.tapmedia.yoush;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;

import com.tapmedia.yoush.groups.LiveGroup;
import com.tapmedia.yoush.groups.ui.GroupMemberEntry;
import com.tapmedia.yoush.groups.ui.GroupMemberListView;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment;

import java.util.List;

public final class GroupMembersDialog {

  private final FragmentActivity fragmentActivity;
  private final Recipient        groupRecipient;

  public GroupMembersDialog(@NonNull FragmentActivity activity,
                            @NonNull Recipient groupRecipient)
  {
    this.fragmentActivity = activity;
    this.groupRecipient   = groupRecipient;
  }

  public void display() {
    AlertDialog dialog = new AlertDialog.Builder(fragmentActivity)
                                        .setTitle(R.string.ConversationActivity_group_members)
                                        .setIconAttribute(R.attr.group_members_dialog_icon)
                                        .setCancelable(true)
                                        .setView(R.layout.dialog_group_members)
                                        .setPositiveButton(android.R.string.ok, null)
                                        .show();

    GroupMemberListView memberListView = dialog.findViewById(R.id.list_members);

    LiveGroup                                   liveGroup   = new LiveGroup(groupRecipient.requireGroupId());
    LiveData<List<GroupMemberEntry.FullMember>> fullMembers = liveGroup.getFullMembers();

    //noinspection ConstantConditions
    fullMembers.observe(fragmentActivity, memberListView::setMembers);

    dialog.setOnDismissListener(d -> fullMembers.removeObservers(fragmentActivity));

    memberListView.setRecipientClickListener(recipient -> {
      dialog.dismiss();
      contactClick(recipient);
    });
  }

  private void contactClick(@NonNull Recipient recipient) {
    RecipientBottomSheetDialogFragment.create(recipient.getId(), groupRecipient.requireGroupId())
                                      .show(fragmentActivity.getSupportFragmentManager(), "BOTTOM");
  }
}

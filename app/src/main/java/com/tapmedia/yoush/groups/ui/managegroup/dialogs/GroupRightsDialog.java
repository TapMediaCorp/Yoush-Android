package com.tapmedia.yoush.groups.ui.managegroup.dialogs;

import android.content.Context;

import androidx.annotation.ArrayRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.groups.GroupAccessControl;

public final class GroupRightsDialog {

  private final AlertDialog.Builder builder;

  @NonNull private GroupAccessControl rights;

  public GroupRightsDialog(@NonNull Context context,
                           @NonNull Type type,
                           @NonNull GroupAccessControl currentRights,
                           @NonNull GroupRightsDialog.OnChange onChange)
  {
    rights = currentRights;

    builder = new AlertDialog.Builder(context)
                             .setTitle(type.message)
                             .setSingleChoiceItems(type.choices, currentRights.ordinal(), (dialog, which) -> rights = GroupAccessControl.values()[which])
                             .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                             })
                             .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                               GroupAccessControl newGroupAccessControl = rights;

                               if (newGroupAccessControl != currentRights) {
                                 onChange.changed(currentRights, newGroupAccessControl);
                               }
                             });
  }

  public void show() {
    builder.show();
  }

  public interface OnChange {
    void changed(@NonNull GroupAccessControl from, @NonNull GroupAccessControl to);
  }

  public enum Type {

    MEMBERSHIP(R.string.GroupManagement_choose_who_can_add_or_invite_new_members,
               R.array.GroupManagement_edit_group_membership_choices),

    ATTRIBUTES(R.string.GroupManagement_choose_who_can_change_the_group_name_and_photo,
               R.array.GroupManagement_edit_group_info_choices);

    @StringRes private final int message;
    @ArrayRes  private final int choices;

    Type(@StringRes int message, @ArrayRes int choices) {
      this.message = message;
      this.choices = choices;
    }
  }
}

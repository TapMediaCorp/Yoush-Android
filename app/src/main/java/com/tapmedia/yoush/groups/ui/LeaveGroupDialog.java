package com.tapmedia.yoush.groups.ui;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;

import com.tapmedia.yoush.R;
import com.tapmedia.yoush.groups.GroupChangeBusyException;
import com.tapmedia.yoush.groups.GroupChangeFailedException;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.groups.GroupManager;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.concurrent.SimpleTask;

import java.io.IOException;

public final class LeaveGroupDialog {

  private static final String TAG = Log.tag(LeaveGroupDialog.class);

  private LeaveGroupDialog() {
  }

  public static void handleLeavePushGroup(@NonNull Context context,
                                          @NonNull Lifecycle lifecycle,
                                          @NonNull GroupId.Push groupId,
                                          @Nullable Runnable onSuccess)
  {
    new AlertDialog.Builder(context)
                   .setTitle(context.getString(R.string.ConversationActivity_leave_group))
                   .setIconAttribute(R.attr.dialog_info_icon)
                   .setCancelable(true)
                   .setMessage(context.getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group))
                   .setPositiveButton(R.string.yes, (dialog, which) ->
                      SimpleTask.run(
                        lifecycle,
                        () -> {
                          try {
                            GroupManager.leaveGroup(context, groupId);
                            return true;
                          } catch (GroupChangeFailedException | GroupChangeBusyException | IOException e) {
                            Log.w(TAG, e);
                            return false;
                          }
                        },
                       (success) -> {
                          if (success) {
                            if (onSuccess != null) onSuccess.run();
                          } else {
                            Toast.makeText(context, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
                          }
                        }))
                   .setNegativeButton(R.string.no, null)
                   .show();
  }
}

package com.tapmedia.yoush.groups.ui.managegroup;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;

import com.tapmedia.yoush.PassphraseRequiredActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.background.BackgroundJobSend;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.util.DynamicNoActionBarTheme;
import com.tapmedia.yoush.util.DynamicTheme;

public class ManageGroupActivity extends PassphraseRequiredActivity {

  private static final String GROUP_ID = "GROUP_ID";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context, @NonNull GroupId groupId) {
    Intent intent = new Intent(context, ManageGroupActivity.class);
    intent.putExtra(GROUP_ID, groupId.toString());
    return intent;
  }

  public static @Nullable Bundle createTransitionBundle(@NonNull Context activityContext, @NonNull View from) {
    if (activityContext instanceof Activity) {
      return ActivityOptionsCompat.makeSceneTransitionAnimation((Activity) activityContext, from, "avatar").toBundle();
    } else {
      return null;
    }
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    getWindow().getDecorView().setSystemUiVisibility(getWindow().getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    setContentView(R.layout.group_manage_activity);
    if (savedInstanceState == null) {
      getSupportFragmentManager().beginTransaction()
              .replace(R.id.viewFragmentContainer, ManageGroupFragment.newInstance(getIntent().getStringExtra(GROUP_ID)))
              .commitNow();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (data == null || resultCode != RESULT_OK) {
      return;
    }
    BackgroundJobSend.setBackground(data.getData(), null);
    finish();
  }
}

package com.tapmedia.yoush.groups.ui.pendingmemberinvites;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.tapmedia.yoush.PassphraseRequiredActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.groups.GroupId;
import com.tapmedia.yoush.util.DynamicNoActionBarTheme;
import com.tapmedia.yoush.util.DynamicTheme;

public class PendingMemberInvitesActivity extends PassphraseRequiredActivity {

  private static final String GROUP_ID = "GROUP_ID";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context, @NonNull GroupId.V2 groupId) {
    Intent intent = new Intent(context, PendingMemberInvitesActivity.class);
    intent.putExtra(GROUP_ID, groupId.toString());
    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState, boolean ready) {
    super.onCreate(savedInstanceState, ready);
    setContentView(R.layout.group_pending_member_invites_activity);

    if (savedInstanceState == null) {
      getSupportFragmentManager().beginTransaction()
                                 .replace(R.id.viewFragmentContainer, PendingMemberInvitesFragment.newInstance(GroupId.parseOrThrow(getIntent().getStringExtra(GROUP_ID)).requireV2()))
                                 .commitNow();
    }

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }

  @Override
  public boolean onSupportNavigateUp() {
    onBackPressed();
    return true;
  }
}

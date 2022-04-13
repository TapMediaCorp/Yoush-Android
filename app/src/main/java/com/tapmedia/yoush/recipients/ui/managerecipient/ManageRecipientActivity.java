package com.tapmedia.yoush.recipients.ui.managerecipient;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;

import com.tapmedia.yoush.PassphraseRequiredActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.conversation.background.BackgroundJobSend;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.DynamicNoActionBarTheme;
import com.tapmedia.yoush.util.DynamicTheme;

public class ManageRecipientActivity extends PassphraseRequiredActivity {

  private static final String RECIPIENT_ID = "RECIPIENT_ID";
  private static final String FROM_CONVERSATION = "FROM_CONVERSATION";

  private final DynamicTheme dynamicTheme = new DynamicNoActionBarTheme();

  public static Intent newIntent(@NonNull Context context, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, ManageRecipientActivity.class);
    intent.putExtra(RECIPIENT_ID, recipientId);
    return intent;
  }

  /**
   * Makes the message button behave like back.
   */
  public static Intent newIntentFromConversation(@NonNull Context context, @NonNull RecipientId recipientId) {
    Intent intent = new Intent(context, ManageRecipientActivity.class);
    intent.putExtra(RECIPIENT_ID, recipientId);
    intent.putExtra(FROM_CONVERSATION, true);
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
    setContentView(R.layout.recipient_manage_activity);
    if (savedInstanceState != null) {
      return;
    }
    Fragment fragment = ManageRecipientFragment.newInstance(
            getIntent().getParcelableExtra(RECIPIENT_ID),
            getIntent().getBooleanExtra(FROM_CONVERSATION, false)
    );
    getSupportFragmentManager()
            .beginTransaction()
            .replace(R.id.viewFragmentContainer, fragment)
            .commitNow();
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

  @Override
  public void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
  }
}

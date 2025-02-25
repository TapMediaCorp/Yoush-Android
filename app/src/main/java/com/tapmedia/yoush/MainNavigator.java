package com.tapmedia.yoush;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.tapmedia.yoush.conversation.ConversationActivity;
import com.tapmedia.yoush.conversationlist.ConversationListArchiveFragment;
import com.tapmedia.yoush.conversationlist.ConversationListFragment;
import com.tapmedia.yoush.conversationlist.model.Conversation;
import com.tapmedia.yoush.database.model.ThreadRecord;
import com.tapmedia.yoush.groups.ui.creategroup.CreateGroupActivity;
import com.tapmedia.yoush.insights.InsightsLauncher;
import com.tapmedia.yoush.recipients.RecipientId;

public class MainNavigator {

  private final MainActivity activity;

  public MainNavigator(@NonNull MainActivity activity) {
    this.activity = activity;
  }

  public static MainNavigator get(@NonNull Activity activity) {
    if (!(activity instanceof MainActivity)) {
      throw new IllegalArgumentException("Activity must be an instance of MainActivity!");
    }

    return ((MainActivity) activity).getNavigator();
  }

  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      return;
    }
    /*getFragmentManager().beginTransaction()
            .add(R.id.viewFragmentContainer, new ConversationListFragment())
            .commit();*/
  }

  /**
   * @return True if the back pressed was handled in our own custom way, false if it should be given
   *         to the system to do the default behavior.
   */
  public boolean onBackPressed() {
    Fragment fragment = getFragmentManager().findFragmentById(R.id.viewFragmentContainer);

    if (fragment instanceof BackHandler) {
      return ((BackHandler) fragment).onBackPressed();
    }

    return false;
  }

  public void goToConversation(Conversation conversation) {
    goToConversation(conversation.getThreadRecord());
  }

  public void goToConversation(ThreadRecord record) {
    goToConversation(record.getRecipient().getId(), record.getThreadId(), record.getDistributionType(), -1);
  }

  public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, int startingPosition) {
    Intent intent = ConversationActivity.buildIntent(activity, recipientId, threadId, distributionType, startingPosition);
    activity.startActivity(intent);
    activity.overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
  }

  public void goToAppSettings() {
    Intent intent = new Intent(activity, ApplicationPreferencesActivity.class);
    activity.startActivity(intent);
  }

  public void goToArchiveList() {
    getFragmentManager().beginTransaction()
            .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
            .replace(R.id.viewFragmentContainer, new ConversationListArchiveFragment())
            .addToBackStack(null)
            .commit();
  }



  public void goToGroupCreation() {
    activity.startActivity(CreateGroupActivity.newIntent(activity));
  }

  public void goToInvite() {
    Intent intent = new Intent(activity, InviteActivity.class);
    activity.startActivity(intent);
  }

  public void goToInsights() {
    InsightsLauncher.showInsightsDashboard(activity.getSupportFragmentManager());
  }

  private @NonNull FragmentManager getFragmentManager() {
    return activity.getSupportFragmentManager();
  }

  public interface BackHandler {
    /**
     * @return True if the back pressed was handled in our own custom way, false if it should be given
     *         to the system to do the default behavior.
     */
    boolean onBackPressed();
  }
}

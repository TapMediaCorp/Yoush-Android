package com.tapmedia.yoush.groups.ui.creategroup;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.annimon.stream.Stream;

import com.tapmedia.yoush.ContactSelectionActivity;
import com.tapmedia.yoush.ContactSelectionListFragment;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.ContactsCursorLoader;
import com.tapmedia.yoush.contacts.sync.DirectoryHelper;
import com.tapmedia.yoush.database.RecipientDatabase;
import com.tapmedia.yoush.groups.GroupsV2CapabilityChecker;
import com.tapmedia.yoush.groups.ui.creategroup.details.AddGroupDetailsActivity;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.recipients.Recipient;
import com.tapmedia.yoush.recipients.RecipientId;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.ProfileUtil;
import com.tapmedia.yoush.util.Stopwatch;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.concurrent.SimpleTask;
import com.tapmedia.yoush.util.views.SimpleProgressDialog;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.profiles.ProfileAndCredential;
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

public class CreateGroupActivity extends ContactSelectionActivity {

  private static String TAG = Log.tag(CreateGroupActivity.class);

  private static final int   MINIMUM_GROUP_SIZE       = 1;
  private static final short REQUEST_CODE_ADD_DETAILS = 17275;

  private View next;

  public static Intent newIntent(@NonNull Context context) {
    Intent intent = new Intent(context, CreateGroupActivity.class);

    intent.putExtra(ContactSelectionListFragment.MULTI_SELECT, true);
    intent.putExtra(ContactSelectionListFragment.REFRESHABLE, false);
    intent.putExtra(ContactSelectionActivity.EXTRA_LAYOUT_RES_ID, R.layout.create_group_activity);

    int displayMode = TextSecurePreferences.isSmsEnabled(context) ? ContactsCursorLoader.DisplayMode.FLAG_SMS | ContactsCursorLoader.DisplayMode.FLAG_PUSH
                                                                  : ContactsCursorLoader.DisplayMode.FLAG_PUSH;

    intent.putExtra(ContactSelectionListFragment.DISPLAY_MODE, displayMode);
    intent.putExtra(ContactSelectionListFragment.TOTAL_CAPACITY, FeatureFlags.groupsV2create() ? FeatureFlags.gv2GroupCapacity() - 1
                                                                                               : ContactSelectionListFragment.NO_LIMIT);

    return intent;
  }

  @Override
  public void onCreate(Bundle bundle, boolean ready) {
    super.onCreate(bundle, ready);
    assert getSupportActionBar() != null;
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    next = findViewById(R.id.next);

    disableNext();
    next.setOnClickListener(v -> handleNextPressed());
    contactsFragment.inviteCallback = null;
    contactsFragment.newGroupCallback = null;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == android.R.id.home) {
      finish();
      return true;
    }

    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_CODE_ADD_DETAILS && resultCode == RESULT_OK) {
      finish();
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  @Override
  public void onContactSelected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      toolbar.clear();
    }

    if (contactsFragment.getSelectedContactsCount() >= MINIMUM_GROUP_SIZE) {
      enableNext();
    }
  }

  @Override
  public void onContactDeselected(Optional<RecipientId> recipientId, String number) {
    if (contactsFragment.hasQueryFilter()) {
      toolbar.clear();
    }

    if (contactsFragment.getSelectedContactsCount() < MINIMUM_GROUP_SIZE) {
      disableNext();
    }
  }

  private void enableNext() {
    next.setEnabled(true);
    next.animate().alpha(1f);
  }

  private void disableNext() {
    next.setEnabled(false);
    next.animate().alpha(0.5f);
  }

  private void handleNextPressed() {
    Stopwatch                    stopwatch      = new Stopwatch("Recipient Refresh");
    AtomicReference<AlertDialog> progressDialog = new AtomicReference<>();

    Runnable showDialogRunnable = () -> {
      Log.i(TAG, "Taking some time. Showing a progress dialog.");
      progressDialog.set(SimpleProgressDialog.show(this));
    };

    next.postDelayed(showDialogRunnable, 300);

    SimpleTask.run(getLifecycle(), () -> {
      RecipientId[] ids = Stream.of(contactsFragment.getSelectedContacts())
                                .map(selectedContact -> selectedContact.getOrCreateRecipientId(this))
                                .toArray(RecipientId[]::new);

      List<Recipient> resolved = Stream.of(ids)
                                       .map(Recipient::resolved)
                                       .toList();

      stopwatch.split("resolve");

      List<Recipient> registeredChecks = Stream.of(resolved)
                                               .filter(r -> r.getRegistered() == RecipientDatabase.RegisteredState.UNKNOWN)
                                               .toList();

      Log.i(TAG, "Need to do " + registeredChecks.size() + " registration checks.");

      for (Recipient recipient : registeredChecks) {
        try {
          DirectoryHelper.refreshDirectoryFor(this, recipient, false);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh registered status for " + recipient.getId(), e);
        }
      }

      stopwatch.split("registered");

      if (FeatureFlags.groupsV2()) {
        try {
          new GroupsV2CapabilityChecker().refreshCapabilitiesIfNecessary(resolved);
        } catch (IOException e) {
          Log.w(TAG, "Failed to refresh all recipient capabilities.", e);
        }
      }

      stopwatch.split("capabilities");

      return ids;
    }, ids -> {
      if (progressDialog.get() != null) {
        progressDialog.get().dismiss();
      }

      next.removeCallbacks(showDialogRunnable);
      stopwatch.stop(TAG);

      startActivityForResult(AddGroupDetailsActivity.newIntent(this, ids), REQUEST_CODE_ADD_DETAILS);
    });
  }
}

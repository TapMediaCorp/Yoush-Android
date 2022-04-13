package com.tapmedia.yoush.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;

import com.google.firebase.iid.FirebaseInstanceId;

import com.tapmedia.yoush.ApplicationPreferencesActivity;
import com.tapmedia.yoush.BuildConfig;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.contacts.ContactAccessor;
import com.tapmedia.yoush.contacts.ContactIdentityManager;
import com.tapmedia.yoush.conversationlist.action.ActionData;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.dialog.AlertBottomDialog;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.logsubmit.SubmitDebugLogActivity;
import com.tapmedia.yoush.registration.RegistrationNavigationActivity;
import com.tapmedia.yoush.ui.main.MainFragment;
import com.tapmedia.yoush.ui.pin.PinCreateFragment;
import com.tapmedia.yoush.ui.pin.PinResetFragment;
import com.tapmedia.yoush.ui.pin.PinUpdateFragment;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.task.ProgressDialogAsyncTask;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;

import java.io.IOException;

public class AdvancedPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String TAG = AdvancedPreferenceFragment.class.getSimpleName();

  private static final String PUSH_MESSAGING_PREF   = "pref_toggle_push_messaging";
  private static final String SUBMIT_DEBUG_LOG_PREF = "pref_submit_debug_logs";
  private static final String INTERNAL_PREF         = "pref_internal";

  private static final int PICK_IDENTITY_CONTACT = 1;

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);

    initializeIdentitySelection();

    Preference submitDebugLog = this.findPreference(SUBMIT_DEBUG_LOG_PREF);
    // submitDebugLog.setOnPreferenceClickListener(new SubmitDebugLogListener());
    submitDebugLog.setSummary(getVersion(getActivity()));

    Preference internalPreference = this.findPreference(INTERNAL_PREF);
    internalPreference.setVisible(FeatureFlags.internalUser());
    internalPreference.setOnPreferenceClickListener(preference -> {
      if (FeatureFlags.internalUser()) {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                .replace(android.R.id.content, new InternalOptionsPreferenceFragment())
                .addToBackStack(null)
                .commit();
        return true;
      } else {
        return false;
      }
    });


    Preference pinCodeCreatePre = this.findPreference("pin_code_create");
    pinCodeCreatePre.setOnPreferenceClickListener(preference -> {
      PinCreateFragment fragment = new PinCreateFragment();
      fragment.onSuccess = () -> {
        requireActivity().onBackPressed();
      };
      showFragment(fragment);
      return true;
    });

    Preference pinCodeUpdatePre = this.findPreference("pin_code_update");
    pinCodeUpdatePre.setOnPreferenceClickListener(preference -> {
      PinUpdateFragment fragment = new PinUpdateFragment();
      fragment.onSuccess = () -> {
        requireActivity().onBackPressed();
      };
      showFragment(fragment);
      return true;
    });


    Preference pinCodeResetPre = this.findPreference("pin_code_reset");
    pinCodeResetPre.setOnPreferenceClickListener(preference -> {
      FragmentActivity activity = requireActivity();
      AlertBottomDialog d = new AlertBottomDialog();
      d.title = activity.getString(R.string.pin_reset_title);
      d.subTitle = activity.getString(R.string.pin_reset_sub_title);
      d.confirmLabel = activity.getString(R.string.pin_reset_btn);
      d.confirmColor = activity.getColor(R.color.red_600);
      d.cancelLabel = activity.getString(R.string.pin_reset_cancel);
      d.runnable = () -> {
        PinResetFragment fragment = new PinResetFragment();
        fragment.onSuccess = () -> {
          requireActivity().onBackPressed();
          ActionData.deleteHiddenConversation(AdvancedPreferenceFragment.this, result -> {
          });
        };
        showFragment(fragment);
      };
      d.show(activity.getSupportFragmentManager(), null);
      return true;
    });

    String pinCode = SignalStore.pinCodeValues().getPinCode();
    boolean isPinCodeEmpty = TextUtils.isEmpty(pinCode);
    pinCodeCreatePre.setVisible(isPinCodeEmpty);
    pinCodeUpdatePre.setVisible(!isPinCodeEmpty);
    pinCodeResetPre.setVisible(!isPinCodeEmpty);

  }

  private void showFragment(MainFragment fragment) {
    requireActivity().getSupportFragmentManager()
            .beginTransaction()
            .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
            .add(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced);

    initializePushMessagingToggle();
  }

  @Override
  public void onActivityResult(int reqCode, int resultCode, Intent data) {
    super.onActivityResult(reqCode, resultCode, data);

    Log.i(TAG, "Got result: " + resultCode + " for req: " + reqCode);
    if (resultCode == Activity.RESULT_OK && reqCode == PICK_IDENTITY_CONTACT) {
      handleIdentitySelection(data);
    }
  }

  private void initializePushMessagingToggle() {
    CheckBoxPreference preference = (CheckBoxPreference)this.findPreference(PUSH_MESSAGING_PREF);

    if (TextSecurePreferences.isPushRegistered(getActivity())) {
      preference.setChecked(true);
      preference.setSummary(TextSecurePreferences.getLocalNumber(getActivity()));
    } else {
      preference.setChecked(false);
      preference.setSummary(R.string.preferences__free_private_messages_and_calls);
    }

    preference.setOnPreferenceChangeListener(new PushMessagingClickListener());
  }

  private void initializeIdentitySelection() {
    ContactIdentityManager identity = ContactIdentityManager.getInstance(getActivity());

    Preference preference = this.findPreference(TextSecurePreferences.IDENTITY_PREF);

    if (identity.isSelfIdentityAutoDetected()) {
      this.getPreferenceScreen().removePreference(preference);
    } else {
      Uri contactUri = identity.getSelfIdentityUri();

      if (contactUri != null) {
        String contactName = ContactAccessor.getInstance().getNameFromContact(getActivity(), contactUri);
        preference.setSummary(String.format(getString(R.string.ApplicationPreferencesActivity_currently_s),
                                            contactName));
      }

      preference.setOnPreferenceClickListener(new IdentityPreferenceClickListener());
    }
  }

  private @NonNull String getVersion(@Nullable Context context) {
    if (context == null) return "";

    String app     = context.getString(R.string.app_name);
    String version = BuildConfig.VERSION_NAME;

    return String.format("%s %s", app, version);
  }

  private class IdentityPreferenceClickListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      Intent intent = new Intent(Intent.ACTION_PICK);
      intent.setType(ContactsContract.Contacts.CONTENT_TYPE);
      startActivityForResult(intent, PICK_IDENTITY_CONTACT);
      return true;
    }
  }

  private void handleIdentitySelection(Intent data) {
    Uri contactUri = data.getData();

    if (contactUri != null) {
      TextSecurePreferences.setIdentityContactUri(getActivity(), contactUri.toString());
      initializeIdentitySelection();
    }
  }

  private class SubmitDebugLogListener implements Preference.OnPreferenceClickListener {
    @Override
    public boolean onPreferenceClick(Preference preference) {
      final Intent intent = new Intent(getActivity(), SubmitDebugLogActivity.class);
      startActivity(intent);
      return true;
    }
  }

  private class PushMessagingClickListener implements Preference.OnPreferenceChangeListener {
    private static final int SUCCESS       = 0;
    private static final int NETWORK_ERROR = 1;

    private class DisablePushMessagesTask extends ProgressDialogAsyncTask<Void, Void, Integer> {
      private final CheckBoxPreference checkBoxPreference;

      public DisablePushMessagesTask(final CheckBoxPreference checkBoxPreference) {
        super(getActivity(), R.string.ApplicationPreferencesActivity_unregistering, R.string.ApplicationPreferencesActivity_unregistering_from_signal_messages_and_calls);
        this.checkBoxPreference = checkBoxPreference;
      }

      @Override
      protected void onPostExecute(Integer result) {
        super.onPostExecute(result);
        switch (result) {
        case NETWORK_ERROR:
          Toast.makeText(getActivity(),
                         R.string.ApplicationPreferencesActivity_error_connecting_to_server,
                         Toast.LENGTH_LONG).show();
          break;
        case SUCCESS:
          TextSecurePreferences.setPushRegistered(getActivity(), false);
          SignalStore.registrationValues().clearRegistrationComplete();
          initializePushMessagingToggle();
          break;
        }
      }

      @Override
      protected Integer doInBackground(Void... params) {
        try {
          Context                     context        = getActivity();
          SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();

          try {
            accountManager.setGcmId(Optional.<String>absent());
          } catch (AuthorizationFailedException e) {
            Log.w(TAG, e);
          }

          if (!TextSecurePreferences.isFcmDisabled(context)) {
            FirebaseInstanceId.getInstance().deleteInstanceId();
          }

          return SUCCESS;
        } catch (IOException ioe) {
          Log.w(TAG, ioe);
          return NETWORK_ERROR;
        }
      }
    }

    @Override
    public boolean onPreferenceChange(final Preference preference, Object newValue) {
      if (((CheckBoxPreference)preference).isChecked()) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setIconAttribute(R.attr.dialog_info_icon);
        builder.setTitle(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls);
        builder.setMessage(R.string.ApplicationPreferencesActivity_disable_signal_messages_and_calls_by_unregistering);
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            new DisablePushMessagesTask((CheckBoxPreference)preference).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
          }
        });
        builder.show();
      } else {
        startActivity(RegistrationNavigationActivity.newIntentForReRegistration(requireContext()));
      }

      return false;
    }
  }
}

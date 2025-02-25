package com.tapmedia.yoush.preferences;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.google.android.material.snackbar.Snackbar;

import com.tapmedia.yoush.ApplicationPreferencesActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.lock.v2.CreateKbsPinActivity;
import com.tapmedia.yoush.pin.PinOptOutDialog;
import com.tapmedia.yoush.util.TextSecurePreferences;

public class AdvancedPinPreferenceFragment extends ListSummaryPreferenceFragment {

  private static final String PREF_ENABLE  = "pref_pin_enable";
  private static final String PREF_DISABLE = "pref_pin_disable";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_advanced_pin);
  }

  @Override
  public void onResume() {
    super.onResume();
    updatePreferenceState();
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == CreateKbsPinActivity.REQUEST_NEW_PIN && resultCode == CreateKbsPinActivity.RESULT_OK) {
      Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_created, Snackbar.LENGTH_LONG).setTextColor(Color.WHITE).show();
    }
  }

  private void updatePreferenceState() {
    Preference enable = this.findPreference(PREF_ENABLE);
    Preference disable = this.findPreference(PREF_DISABLE);

    if (SignalStore.kbsValues().hasOptedOut()) {
      enable.setVisible(true);
      disable.setVisible(false);

      enable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(true);
        return true;
      });
    } else {
      enable.setVisible(false);
      disable.setVisible(true);

      disable.setOnPreferenceClickListener(preference -> {
        onPreferenceChanged(false);
        return true;
      });
    }

    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__advanced_pin_settings);
  }

  private void onPreferenceChanged(boolean enabled) {
    boolean hasRegistrationLock = TextSecurePreferences.isV1RegistrationLockEnabled(requireContext()) ||
                                  SignalStore.kbsValues().isV2RegistrationLockEnabled();

    if (!enabled && hasRegistrationLock) {
      new AlertDialog.Builder(requireContext())
                     .setMessage(R.string.ApplicationPreferencesActivity_pins_are_required_for_registration_lock)
                     .setCancelable(true)
                     .setPositiveButton(android.R.string.ok, (d, which) -> d.dismiss())
                     .show();
    } else if (!enabled) {
      PinOptOutDialog.showForOptOut(requireContext(),
                                    () -> {
                                      updatePreferenceState();
                                      Snackbar.make(requireView(), R.string.ApplicationPreferencesActivity_pin_disabled, Snackbar.LENGTH_SHORT).setTextColor(Color.WHITE).show();
                                    },
                                    () -> Toast.makeText(requireContext(), R.string.ApplicationPreferencesActivity_failed_to_disable_pins_try_again_later, Toast.LENGTH_LONG).show());
    } else {
      startActivityForResult(CreateKbsPinActivity.getIntentForPinCreate(requireContext()), CreateKbsPinActivity.REQUEST_NEW_PIN);
    }
  }
}

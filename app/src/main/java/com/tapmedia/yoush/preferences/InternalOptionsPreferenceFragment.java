package com.tapmedia.yoush.preferences;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

import com.tapmedia.yoush.ApplicationPreferencesActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.components.SwitchPreferenceCompat;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobs.RefreshAttributesJob;
import com.tapmedia.yoush.jobs.RefreshOwnProfileJob;
import com.tapmedia.yoush.jobs.RotateProfileKeyJob;
import com.tapmedia.yoush.keyvalue.InternalValues;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;

public class InternalOptionsPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String TAG = Log.tag(InternalOptionsPreferenceFragment.class);

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_internal);

    PreferenceDataStore preferenceDataStore = SignalStore.getPreferenceDataStore();

    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_FORCE_INVITES, SignalStore.internalValues().forceGv2Invites());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_IGNORE_SERVER_CHANGES, SignalStore.internalValues().gv2IgnoreServerChanges());
    initializeSwitchPreference(preferenceDataStore, InternalValues.GV2_IGNORE_P2P_CHANGES, SignalStore.internalValues().gv2IgnoreP2PChanges());

    findPreference("pref_refresh_attributes").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager()
                             .startChain(new RefreshAttributesJob())
                             .then(new RefreshOwnProfileJob())
                             .enqueue();
      Toast.makeText(getContext(), "Scheduled attribute refresh", Toast.LENGTH_SHORT).show();
      return true;
    });

    findPreference("pref_rotate_profile_key").setOnPreferenceClickListener(preference -> {
      ApplicationDependencies.getJobManager().add(new RotateProfileKeyJob());
      Toast.makeText(getContext(), "Scheduled profile key rotation", Toast.LENGTH_SHORT).show();
      return true;
    });
  }

  private void initializeSwitchPreference(@NonNull PreferenceDataStore preferenceDataStore,
                                          @NonNull String key,
                                          boolean checked)
  {
    SwitchPreferenceCompat forceGv2Preference = (SwitchPreferenceCompat) findPreference(key);
    forceGv2Preference.setPreferenceDataStore(preferenceDataStore);
    forceGv2Preference.setChecked(checked);
  }

  @Override
  public void onResume() {
    super.onResume();
    //noinspection ConstantConditions
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__internal_preferences);
  }
}

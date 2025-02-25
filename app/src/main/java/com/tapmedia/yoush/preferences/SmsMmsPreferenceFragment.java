package com.tapmedia.yoush.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.tapmedia.yoush.ApplicationPreferencesActivity;
import com.tapmedia.yoush.R;
import com.tapmedia.yoush.util.TextSecurePreferences;
import com.tapmedia.yoush.util.Util;

public class SmsMmsPreferenceFragment extends CorrectedPreferenceFragment {
  private static final String KITKAT_DEFAULT_PREF = "pref_set_default";
  private static final String MMS_PREF            = "pref_mms_preferences";

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);


    this.findPreference(MMS_PREF)
      .setOnPreferenceClickListener(new ApnPreferencesClickListener());

    initializePlatformSpecificOptions();
  }

  @Override
  public void onCreatePreferences(@Nullable Bundle savedInstanceState, String rootKey) {
    addPreferencesFromResource(R.xml.preferences_sms_mms);
  }

  @Override
  public void onResume() {
    super.onResume();
    ((ApplicationPreferencesActivity) getActivity()).getSupportActionBar().setTitle(R.string.preferences__sms_mms);

    initializeDefaultPreference();
  }

  private void initializePlatformSpecificOptions() {
    PreferenceScreen preferenceScreen    = getPreferenceScreen();
    Preference       defaultPreference   = findPreference(KITKAT_DEFAULT_PREF);
    Preference       allSmsPreference    = findPreference(TextSecurePreferences.ALL_SMS_PREF);
    Preference       allMmsPreference    = findPreference(TextSecurePreferences.ALL_MMS_PREF);
    Preference       manualMmsPreference = findPreference(MMS_PREF);

    if (VERSION.SDK_INT >= VERSION_CODES.KITKAT) {
      if (allSmsPreference != null) preferenceScreen.removePreference(allSmsPreference);
      if (allMmsPreference != null) preferenceScreen.removePreference(allMmsPreference);
    } else if (defaultPreference != null) {
      preferenceScreen.removePreference(defaultPreference);
    }

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP && manualMmsPreference != null) {
      preferenceScreen.removePreference(manualMmsPreference);
    }
  }

  private void initializeDefaultPreference() {
    if (VERSION.SDK_INT < VERSION_CODES.KITKAT) return;

    Preference defaultPreference = findPreference(KITKAT_DEFAULT_PREF);
    if (Util.isDefaultSmsProvider(getActivity())) {
      if (VERSION.SDK_INT < VERSION_CODES.M) defaultPreference.setIntent(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
      if (VERSION.SDK_INT < VERSION_CODES.N) defaultPreference.setIntent(new Intent(Settings.ACTION_SETTINGS));
      else                                   defaultPreference.setIntent(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));

      defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_enabled));
      defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_change_your_default_sms_app));
    } else {
      Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
      intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getActivity().getPackageName());
      defaultPreference.setIntent(intent);
      defaultPreference.setTitle(getString(R.string.ApplicationPreferencesActivity_sms_disabled));
      defaultPreference.setSummary(getString(R.string.ApplicationPreferencesActivity_touch_to_make_signal_your_default_sms_app));
    }
  }

  private class ApnPreferencesClickListener implements Preference.OnPreferenceClickListener {

    @Override
    public boolean onPreferenceClick(Preference preference) {
      Fragment            fragment            = new MmsPreferencesFragment();
      FragmentManager     fragmentManager     = getActivity().getSupportFragmentManager();
      FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
      fragmentTransaction.replace(android.R.id.content, fragment);
      fragmentTransaction.addToBackStack(null);
      fragmentTransaction.commit();

      return true;
    }
  }

  public static CharSequence getSummary(Context context) {
    final String on                 = context.getString(R.string.ApplicationPreferencesActivity_on);
    final String onCaps             = context.getString(R.string.ApplicationPreferencesActivity_On);
    final String off                = context.getString(R.string.ApplicationPreferencesActivity_off);
    final String offCaps            = context.getString(R.string.ApplicationPreferencesActivity_Off);
    final int    smsMmsSummaryResId = R.string.ApplicationPreferencesActivity_sms_mms_summary;

    boolean postKitkatSMS = Util.isDefaultSmsProvider(context);
    boolean preKitkatSMS  = TextSecurePreferences.isInterceptAllSmsEnabled(context);
    boolean preKitkatMMS  = TextSecurePreferences.isInterceptAllMmsEnabled(context);

    if (postKitkatSMS) return onCaps;
    else               return offCaps;
  }
}

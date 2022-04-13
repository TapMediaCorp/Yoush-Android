package com.tapmedia.yoush.keyvalue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.lock.SignalPinReminders;
import com.tapmedia.yoush.lock.v2.PinKeyboardType;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.TextSecurePreferences;

/**
 * Specifically handles just the UI/UX state around PINs. For actual keys, see {@link KbsValues}.
 */
public final class PinCodeValues extends SignalStoreValues {

  private static final String TAG = Log.tag(PinCodeValues.class);

  public  static final String PIN_CODE = "PIN_CODE";

  PinCodeValues(KeyValueStore store) {
    super(store);
  }

  @Override
  void onFirstEverAppLaunch() {
    getStore().beginWrite()
            .putString(PIN_CODE, null)
            .commit();
  }

  public void setPinCode(@NonNull String pin) {
    getStore().beginWrite().putString(PIN_CODE, pin).commit();
  }

  public @Nullable String getPinCode() {
    return getString(PIN_CODE, null);
  }

}

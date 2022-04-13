package com.tapmedia.yoush.lock.v2;

import android.content.Context;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.util.CensorshipUtil;
import com.tapmedia.yoush.util.FeatureFlags;
import com.tapmedia.yoush.util.TextSecurePreferences;

public final class RegistrationLockUtil {

  private RegistrationLockUtil() {}

  public static boolean userHasRegistrationLock(@NonNull Context context) {
    return TextSecurePreferences.isV1RegistrationLockEnabled(context) || SignalStore.kbsValues().isV2RegistrationLockEnabled();
  }
}

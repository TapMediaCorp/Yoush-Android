package com.tapmedia.yoush.migrations;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.jobs.BaseJob;
import com.tapmedia.yoush.keyvalue.KbsValues;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.lock.PinHashing;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.pin.PinState;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.KeyBackupServicePinException;
import org.whispersystems.signalservice.api.KeyBackupSystemNoDataException;
import org.whispersystems.signalservice.api.KbsPinData;
import org.whispersystems.signalservice.api.kbs.HashedPin;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Migrates an existing V1 registration lock user to a V2 registration lock that is backed by a
 * Signal PIN.
 *
 * Deliberately not a {@link MigrationJob} because it is not something that needs to run at app start.
 * This migration can run at anytime.
 */
public final class RegistrationPinV2MigrationJob extends BaseJob {

  private static final String TAG = Log.tag(RegistrationPinV2MigrationJob.class);

  public static final String KEY = "RegistrationPinV2MigrationJob";

  public RegistrationPinV2MigrationJob() {
    this(new Parameters.Builder()
                       .setQueue(KEY)
                       .setMaxInstances(1)
                       .addConstraint(NetworkConstraint.KEY)
                       .setLifespan(Job.Parameters.IMMORTAL)
                       .setMaxAttempts(Job.Parameters.UNLIMITED)
                       .setMaxBackoff(TimeUnit.HOURS.toMillis(2))
                       .build());
  }

  private RegistrationPinV2MigrationJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  protected void onRun() throws IOException, UnauthenticatedResponseException {
    if (!TextSecurePreferences.isV1RegistrationLockEnabled(context)) {
      Log.i(TAG, "Registration lock disabled");
      return;
    }

    //noinspection deprecation Only acceptable place to read the old pin.
    String pinValue = TextSecurePreferences.getDeprecatedV1RegistrationLockPin(context);

    if (pinValue == null | TextUtils.isEmpty(pinValue)) {
      Log.i(TAG, "No old pin to migrate");
      return;
    }

    Log.i(TAG, "Migrating pin to Key Backup Service");
    PinState.onMigrateToRegistrationLockV2(context, pinValue);
    Log.i(TAG, "Pin migrated to Key Backup Service");
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof IOException;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onFailure() {
  }

  public static class Factory implements Job.Factory<RegistrationPinV2MigrationJob> {
    @Override
    public @NonNull RegistrationPinV2MigrationJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RegistrationPinV2MigrationJob(parameters);
    }
  }
}

package com.tapmedia.yoush.jobs;


import androidx.annotation.NonNull;

import com.tapmedia.yoush.crypto.UnidentifiedAccessUtil;
import com.tapmedia.yoush.dependencies.ApplicationDependencies;
import com.tapmedia.yoush.jobmanager.Data;
import com.tapmedia.yoush.jobmanager.Job;
import com.tapmedia.yoush.jobmanager.impl.NetworkConstraint;
import com.tapmedia.yoush.keyvalue.SignalStore;
import com.tapmedia.yoush.logging.Log;
import com.tapmedia.yoush.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.storage.StorageKey;
import org.whispersystems.signalservice.api.messages.multidevice.KeysMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;

public class MultiDeviceKeysUpdateJob extends BaseJob {

  public static final String KEY = "MultiDeviceKeysUpdateJob";

  private static final String TAG = MultiDeviceKeysUpdateJob.class.getSimpleName();

  public MultiDeviceKeysUpdateJob() {
    this(new Parameters.Builder()
                           .setQueue("MultiDeviceKeysUpdateJob")
                           .setMaxInstances(2)
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(10)
                           .build());

  }

  private MultiDeviceKeysUpdateJob(@NonNull Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    SignalServiceMessageSender messageSender     = ApplicationDependencies.getSignalServiceMessageSender();
    StorageKey                 storageServiceKey = SignalStore.storageServiceValues().getOrCreateStorageKey();

    messageSender.sendMessage(SignalServiceSyncMessage.forKeys(new KeysMessage(Optional.fromNullable(storageServiceKey))),
                              UnidentifiedAccessUtil.getAccessForSync(context));
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception e) {
    return e instanceof PushNetworkException;
  }

  @Override
  public void onFailure() {
  }

  public static final class Factory implements Job.Factory<MultiDeviceKeysUpdateJob> {
    @Override
    public @NonNull MultiDeviceKeysUpdateJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new MultiDeviceKeysUpdateJob(parameters);
    }
  }
}
